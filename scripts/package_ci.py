"""Package a verified hosted main push; never publish or log signing secrets."""
from pathlib import Path
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "release-packages"
EXPECTED_REPOSITORY = "alterbusinessmethod-afk/venith-dictation-android"
STEM = "Venith-Dictation-1.0.0-preview.1-arm64"
DOCS = {
    "INSTALL_SAMSUNG.md": "docs/INSTALL_SAMSUNG.md",
    "MODEL_CHOICES.md": "docs/MODEL_CHOICES.md",
    "LICENSE": "LICENSE",
    "NOTICE": "NOTICE",
    "MODEL_MANIFEST.json": "app/src/main/assets/models/zipformer-en/manifest.json",
    "THIRD_PARTY_NOTICES.txt": "app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt",
}
V1_ADDITIONS = {"META-INF/MANIFEST.MF", "META-INF/VENITH.SF",
                "META-INF/VENITH.RSA", "META-INF/VENITH.DSA", "META-INF/VENITH.EC"}
SECRET_NAMES = ("VENITH_SIGNING_STORE_BASE64", "VENITH_SIGNING_PASSWORD")


class PackageFailure(Exception):
    """Only fixed safe messages may reach the hosted log."""


def run(argv, label, signing=False):
    environment = os.environ.copy()
    environment.pop(SECRET_NAMES[0], None)
    if not signing:
        environment.pop(SECRET_NAMES[1], None)
    try:
        result = subprocess.run(argv, cwd=ROOT, env=environment, capture_output=True,
                                timeout=180, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise PackageFailure(label + " failed") from None
    if result.returncode:
        # Tool stderr is never surfaced: signing errors can contain credential material.
        raise PackageFailure(label + " failed")
    if signing:
        return b""
    if len(result.stdout) > 2 * 1024 * 1024:
        raise PackageFailure(label + " output exceeded limit")
    return result.stdout


def stream_digest(source):
    value = hashlib.sha256()
    for block in iter(lambda: source.read(1024 * 1024), b""):
        value.update(block)
    return value.hexdigest()


def digest(path):
    with path.open("rb") as source:
        return stream_digest(source)


def git_blob(name):
    return run(["git", "show", "HEAD:" + name], "Read committed package document")


def apk_entries(path):
    entries = {}
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            if item.filename in entries:
                raise PackageFailure("Duplicate APK entry")
            with archive.open(item) as source:
                entries[item.filename] = {"bytes": item.file_size, "sha256": stream_digest(source)}
    return entries


def verify_payload(original, signed):
    before, after = apk_entries(original), apk_entries(signed)
    if any(after.get(name) != metadata for name, metadata in before.items()):
        raise PackageFailure("Signing altered an original APK entry")
    additions = set(after) - set(before)
    if not additions or not additions.issubset(V1_ADDITIONS):
        raise PackageFailure("Unexpected signing payload additions")
    return {"original_entry_count": len(before), "original_entries": before,
            "added_v1_entries": {name: after[name] for name in sorted(additions)},
            "original_entries_unchanged": True}


def check_release(tools, apk):
    details = run([str(tools / "aapt2"), "dump", "badging", str(apk)], "Release metadata verification")
    text = details.decode("utf-8", errors="replace")
    if "application-debuggable" in text or not re.search(
            r"package: name='com\.venith\.dictation'.*versionCode='10001'.*versionName='1\.0\.0-preview\.1'", text):
        raise PackageFailure("Expected nondebuggable Venith release identity")
    if not re.search(r"^sdkVersion:'26'$", text, re.MULTILINE) or not re.search(
            r"^targetSdkVersion:'35'$", text, re.MULTILINE):
        raise PackageFailure("Expected pinned minimum and target SDK")
    contents = run([sys.executable, "scripts/verify_apk.py", str(apk)], "ARM64 and bundled model verification")
    if not contents.startswith(b"APK_CONTENTS_PASS "):
        raise PackageFailure("Missing APK content verification evidence")
    prefix = "app/src/main/assets/licenses/"
    committed = run(["git", "ls-tree", "-rz", "--name-only", "HEAD", "--", prefix],
                    "List committed license payloads").decode("utf-8").rstrip("\0").split("\0")
    if not committed or len(committed) > 32 or any(not name.startswith(prefix) for name in committed):
        raise PackageFailure("Expected bounded committed licensing payload")
    with zipfile.ZipFile(apk) as archive:
        for name in committed:
            if archive.read("assets/licenses/" + name[len(prefix):]) != git_blob(name):
                raise PackageFailure("APK licensing payload differs from committed bytes")
    xml = run([str(tools / "aapt2"), "dump", "xmltree", str(apk),
               "--file", "AndroidManifest.xml"], "Full manifest XML evidence")
    permissions = run([str(tools / "aapt2"), "dump", "permissions", str(apk)],
                      "Full manifest permissions evidence")
    if not xml.strip() or not permissions.strip():
        raise PackageFailure("Missing full manifest evidence")
    return {"badging": details, "manifest-xml": xml,
            "permissions": permissions, "apk-contents": contents}


def retain_manifest_evidence(packages, label, apk, evidence):
    logs = {}
    for kind, value in evidence.items():
        name = label + "-" + kind + ".log"
        (packages / name).write_bytes(value)
        logs[name] = {"bytes": len(value), "sha256": hashlib.sha256(value).hexdigest()}
    return {"apk_bytes": apk.stat().st_size, "apk_sha256": digest(apk), "logs": logs}


def prepare_signing_store():
    encoded = os.environ[SECRET_NAMES[0]]
    if len(encoded) > 2 * 1024 * 1024:
        raise PackageFailure("Signing store input exceeded limit")
    try:
        value = base64.b64decode("".join(encoded.split()), validate=True)
    except ValueError:
        raise PackageFailure("Invalid signing store input") from None
    if not value or len(value) > 1024 * 1024:
        raise PackageFailure("Invalid signing store size")
    temporary = Path(os.environ.get("RUNNER_TEMP", ""))
    if not temporary.is_absolute() or not temporary.is_dir():
        raise PackageFailure("Hosted temporary directory unavailable")
    run_id, attempt = os.environ.get("GITHUB_RUN_ID", ""), os.environ.get("GITHUB_RUN_ATTEMPT", "")
    if not run_id.isdigit() or not attempt.isdigit():
        raise PackageFailure("Hosted run identity unavailable")
    directory = temporary / ("venith-signing-" + run_id + "-" + attempt)
    directory.mkdir(mode=0o700, exist_ok=False)
    store = directory / "task-signing.p12"
    descriptor = os.open(store, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as target:
        target.write(value)
    packages = directory / "verified-packages"
    packages.mkdir(mode=0o700)
    return store, packages


def sign_candidate(tools, unsigned, store, packages):
    signed = packages / (STEM + ".apk")
    run([str(tools / "apksigner"), "sign", "--ks", str(store), "--ks-type", "PKCS12",
         "--ks-key-alias", "venith-dictation", "--ks-pass", "env:VENITH_SIGNING_PASSWORD",
         "--key-pass", "env:VENITH_SIGNING_PASSWORD", "--v1-signer-name", "VENITH",
         "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
         "--out", str(signed), str(unsigned)], "APK signing", signing=True)
    (packages / "signing.log").write_text(
        "APK_SIGN_COMMAND_PASS\nGitHub encrypted signing secrets; password through env reference; "
        "keystore only in RUNNER_TEMP.\n", encoding="utf-8")
    verification = run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(signed)],
                       "APK signature verification")
    certificates = re.findall(rb"Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})", verification)
    if len(certificates) != 1 or b"Verified using v2 scheme (APK Signature Scheme v2): true" not in verification:
        raise PackageFailure("Expected one verified v2 signing certificate")
    alignment = run([str(tools / "zipalign"), "-c", "-P", "16", "-v", "4", str(signed)],
                    "Signed APK alignment verification")
    (packages / "signature-verification.log").write_bytes(verification)
    (packages / "alignment-verification.log").write_bytes(alignment)
    return signed, certificates[0].decode("ascii").lower()


def package_archives(packages, signed, documents):
    install = packages / (STEM + "-install.zip")
    checksums = [digest(signed) + "  " + signed.name]
    checksums.extend(hashlib.sha256(value).hexdigest() + "  " + name for name, value in documents.items())
    with zipfile.ZipFile(install, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        archive.write(signed, signed.name)
        for name, value in documents.items():
            archive.writestr(name, value)
        archive.writestr("SHA256SUMS.txt", "\n".join(checksums) + "\n")
    source = packages / (STEM + "-source.zip")
    run(["git", "archive", "--format=zip", "--prefix=" + STEM + "-source/",
         "--output=" + str(source), "HEAD"], "Committed source archive")
    return install, source


def write_manifest(packages, sha, unsigned, certificate, payload, archives, metadata_evidence):
    artifacts = {path.name: {"bytes": path.stat().st_size, "sha256": digest(path)}
                 for path in sorted(packages.iterdir()) if path.is_file()}
    manifest = {"candidate_commit": sha, "repository": os.environ.get("GITHUB_REPOSITORY", ""),
                "github_run_id": os.environ["GITHUB_RUN_ID"], "github_run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
                "event": "push", "ref": "refs/heads/main", "publication_performed": False,
                "certificate_sha256": certificate,
                "required_downstream_release_gates": ["Bind certificate to root provisioning receipt",
                                                      "Verify full manifest security on actual signed bytes"],
                "signing_key_source": "Task-generated key supplied by run owner through encrypted repository secrets",
                "key_origin_independently_verified": False,
                "unsigned_apk": {"bytes": unsigned.stat().st_size, "sha256": digest(unsigned)},
                "payload_provenance": payload, "artifacts": artifacts,
                "manifest_evidence": metadata_evidence,
                "checks": {"signature_verified": True, "alignment_16k_verified": True,
                           "arm64_only": True, "nondebuggable": True, "bundled_model_verified": True,
                           "notice_bytes_match_git_head": True, "source_archive_from_git_head": True},
                "install_zip": archives[0].name, "source_zip": archives[1].name}
    (packages / "RELEASE_PACKAGE_MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    checksums = [digest(path) + "  " + path.name for path in sorted(packages.iterdir()) if path.is_file()]
    (packages / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")


def promote_verified(packages):
    # A failed earlier gate never exposes a signed APK to the always() artifact upload.
    if OUTPUT.exists():
        raise PackageFailure("Release output already exists")
    staging = ROOT / (".verified-release-packages-" + os.environ["GITHUB_RUN_ID"] + "-" + os.environ["GITHUB_RUN_ATTEMPT"])
    shutil.copytree(packages, staging)
    for path in packages.iterdir():
        if not path.is_file() or digest(path) != digest(staging / path.name):
            raise PackageFailure("Verified package copy changed")
    staging.rename(OUTPUT)


def main():
    if os.environ.get("GITHUB_EVENT_NAME") != "push" or os.environ.get("GITHUB_REF") != "refs/heads/main":
        print("SIGNED_PACKAGE_SKIPPED: trusted main push required")
        return
    if os.environ.get("GITHUB_REPOSITORY", "").casefold() != EXPECTED_REPOSITORY:
        raise PackageFailure("Expected owned signing repository")
    store_set, password_set = bool(os.environ.get(SECRET_NAMES[0])), bool(os.environ.get(SECRET_NAMES[1]))
    if not store_set and not password_set:
        print("SIGNED_PACKAGE_SKIPPED: signing secrets not provisioned")
        return
    if not store_set or not password_set:
        raise PackageFailure("Both generated signing secrets are required")
    sha = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or run(["git", "rev-parse", "HEAD"], "Candidate identity").decode().strip() != sha:
        raise PackageFailure("Checked-out candidate does not match GITHUB_SHA")
    tools = Path(os.environ.get("ANDROID_HOME", "")) / "build-tools/35.0.0"
    if not tools.is_absolute() or any(not (tools / name).is_file() for name in ("apksigner", "zipalign", "aapt2")):
        raise PackageFailure("Pinned Android build tools unavailable")
    candidates = list((ROOT / "app/build/outputs/apk/release").glob("*unsigned.apk"))
    if len(candidates) != 1:
        raise PackageFailure("Expected one current unsigned release APK")
    unsigned = candidates[0]
    documents = {name: git_blob(source) for name, source in DOCS.items()}
    original_verification = check_release(tools, unsigned)
    store, packages = prepare_signing_store()
    signed, certificate = sign_candidate(tools, unsigned, store, packages)
    payload = verify_payload(unsigned, signed)
    signed_verification = check_release(tools, signed)
    metadata_evidence = {
        "unsigned": retain_manifest_evidence(packages, "unsigned", unsigned, original_verification),
        "signed": retain_manifest_evidence(packages, "signed", signed, signed_verification),
    }
    (packages / "payload-provenance.log").write_text(
        "APK_PAYLOAD_UNCHANGED_PASS\nAll original entry sizes and SHA256 values match; "
        "only allowlisted v1 signature entries added.\n", encoding="utf-8")
    archives = package_archives(packages, signed, documents)
    write_manifest(packages, sha, unsigned, certificate, payload, archives, metadata_evidence)
    promote_verified(packages)
    print("SIGNED_RELEASE_PACKAGE_PASS " + sha + " certificate_sha256=" + certificate)


if __name__ == "__main__":
    try:
        main()
    except PackageFailure as exc:
        # Only fixed labels authored in this script; never raw tool output or credentials.
        print("SIGNED_RELEASE_PACKAGE_FAILED: " + str(exc), file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        # Exception type identifies an unexpected phase failure without revealing values.
        print("SIGNED_RELEASE_PACKAGE_FAILED: unexpected " + type(exc).__name__, file=sys.stderr)
        sys.exit(1)
