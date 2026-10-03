"""Fetch the bundled model from its immutable upstream revision; verify every byte.

The release APK already contains it. This helper is for building from source.
"""
from pathlib import Path
import hashlib
import json
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / 'app/src/main/assets/models/zipformer-en'

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as data:
        for block in iter(lambda: data.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def main():
    manifest = json.loads((BUNDLE / 'manifest.json').read_text('utf-8'))
    total = 0
    for item in manifest['files']:
        name = item['filename']
        if Path(name).name != name or not item['url'].startswith('https://huggingface.co/k2-fsa/'):
            raise ValueError('Unexpected model source')
        target = BUNDLE / name
        valid = target.exists() and target.stat().st_size == item['bytes'] and digest(target) == item['sha256']
        if not valid:
            staged = BUNDLE / (name + '.download')
            request = urllib.request.Request(item['url'], headers={'User-Agent': 'Venith-Dictation-build/1.0'})
            count = 0
            with urllib.request.urlopen(request, timeout=120) as source, staged.open('wb') as out:
                while True:
                    block = source.read(1024 * 1024)
                    if not block:
                        break
                    count += len(block)
                    if count > item['bytes']:
                        raise ValueError('Model exceeds pinned size')
                    out.write(block)
            if staged.stat().st_size != item['bytes'] or digest(staged) != item['sha256']:
                raise ValueError('Model integrity check failed: ' + name)
            if target.exists():
                archived = BUNDLE / (name + '.previous')
                if archived.exists():
                    raise ValueError('Preserved model already exists; inspect it before replacing')
                target.rename(archived)
            staged.replace(target)
        total += target.stat().st_size
        print('Verified', name, item['sha256'])
    if total != manifest['total_model_bytes'] or total >= 1_000_000_000:
        raise ValueError('Model size gate failed')
    print('MODEL_BUNDLE_PASS', total)

if __name__ == '__main__':
    main()
