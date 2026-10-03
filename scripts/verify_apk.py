"""Verify bundled weights and ARM64/native entries without executing an APK."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

apk = Path(sys.argv[1])
manifest = json.loads((Path(__file__).resolve().parents[1] / 'app/src/main/assets/models/zipformer-en/manifest.json').read_text('utf-8'))
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise ValueError('Duplicate APK entries')
    native = [name for name in names if name.startswith('lib/') and name.endswith('.so')]
    if not native or any(not name.startswith('lib/arm64-v8a/') for name in native):
        raise ValueError('Expected an ARM64-only release')
    for item in manifest['files']:
        value=archive.read('assets/models/zipformer-en/'+item['filename'])
        if len(value)!=item['bytes'] or hashlib.sha256(value).hexdigest()!=item['sha256']:
            raise ValueError('Bundled model integrity mismatch')
    for name in native:
        header=archive.open(name).read(20)
        if header[:5] != b'\x7fELF\x02' or int.from_bytes(header[18:20],'little')!=183:
            raise ValueError('Invalid ARM64 ELF library: '+name)
    forbidden = ['.jks','.keystore','.env','key-password.txt','local.properties','speech.pcm','INPUT.md','goal-objective.md']
    if any(any(name.endswith(item) for item in forbidden) for name in names):
        raise ValueError('Private/test file unexpectedly packaged')
    if 'assets/licenses/NOTICES.txt' not in names or 'assets/licenses/VoiceFlow-MIT.txt' not in names:
        raise ValueError('Missing license notices')
print('APK_CONTENTS_PASS', json.dumps({'apk_bytes':apk.stat().st_size,'model_bytes':manifest['total_model_bytes'],'native_libraries':native}))
