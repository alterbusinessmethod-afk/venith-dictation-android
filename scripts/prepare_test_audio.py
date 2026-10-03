"""Fetch one immutable public LibriSpeech test clip into the test APK only."""
from pathlib import Path
import hashlib
import io
import json
import urllib.request
import wave

root = Path(__file__).resolve().parents[1]
url = 'https://huggingface.co/k2-fsa/sherpa-onnx-zipformer-gigaspeech-2023-12-12/resolve/c9e185789e2067cbf79350c7f691d5d2d4c5a28a/test_wavs/1089-134686-0001.wav'
with urllib.request.urlopen(url, timeout=90) as response:
    data = response.read(212045)
if len(data) != 212044:
    raise ValueError('Unexpected speech fixture size')
blob = hashlib.sha1(b'blob ' + str(len(data)).encode('ascii') + b'\0' + data).hexdigest()
if blob != 'bfe1519ead65b33e26f8b81f25b1c072cbb90d13':
    raise ValueError('Speech fixture does not match pinned Git blob')
with wave.open(io.BytesIO(data)) as source:
    if source.getnchannels() != 1 or source.getsampwidth() != 2 or source.getframerate() != 16000:
        raise ValueError('Unexpected audio format')
    pcm = source.readframes(source.getnframes())
target = root / 'app/src/androidTest/assets'
target.mkdir(parents=True, exist_ok=True)
(target / 'speech.pcm').write_bytes(pcm)
(target / 'speech-source.json').write_text(json.dumps({'url': url, 'git_blob': blob, 'wav_sha256': hashlib.sha256(data).hexdigest(), 'pcm_sha256': hashlib.sha256(pcm).hexdigest(), 'license': 'CC-BY-4.0', 'credit': 'LibriSpeech corpus by Vassil Panayotov, Guoguo Chen, Daniel Povey and Sanjeev Khudanpur; https://www.openslr.org/12'}, indent=2) + '\n', encoding='utf-8')
print('AUDIO_FIXTURE_PASS', len(pcm))
