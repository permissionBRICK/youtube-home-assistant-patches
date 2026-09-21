#!/usr/bin/env python3
"""Build a Manager-compatible .rvp using public, checksum-pinned dependencies."""
from pathlib import Path
import hashlib, os, shutil, subprocess, urllib.request, zipfile

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
CLI_SHA = 'c25549bc17d59d2eb94fa5f86e60e9b77a02772ca88f7050f8f1276f923a9958'
cli = ROOT / 'tools/revanced-cli.jar'
cli.parent.mkdir(exist_ok=True)
if not cli.exists():
    urllib.request.urlretrieve('https://github.com/ReVanced/revanced-cli/releases/download/v6.0.0/revanced-cli-6.0.0-all.jar', cli)
if hashlib.sha256(cli.read_bytes()).hexdigest() != CLI_SHA:
    raise SystemExit('ReVanced CLI checksum mismatch. Remove tools/revanced-cli.jar and try again.')
sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '/opt/android-sdk')))
android = sdk / 'platforms/android-36/android.jar'
r8 = ROOT / 'tools/r8.jar'
if not r8.exists():
    urllib.request.urlretrieve('https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.0.32/r8-9.0.32.jar', r8)
if hashlib.sha256(r8.read_bytes()).hexdigest() != 'a561da8b3d2419f3c0b1936546f6a756790112653f834563f65cebc4fce0bddd':
    raise SystemExit('R8 checksum mismatch.')
d8 = ['java', '-cp', str(r8), 'com.android.tools.r8.D8']
if not android.exists():
    raise SystemExit('Install Android SDK platform android-36; set ANDROID_HOME.')
ext = ROOT / 'extensions/build'
classes = ext / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '--release', '17', '-classpath', str(android), '-d', str(classes),
    *map(str, sorted((ROOT / 'extensions/src/main/java').rglob('*.java')))], check=True)
subprocess.run([*d8, '--release', '--min-api', '26', '--lib', str(android), '--output', str(ext),
    *map(str, sorted(classes.rglob('*.class')))], check=True)
(ext / 'extension.rve').write_bytes((ext / 'classes.dex').read_bytes())
rvp = ROOT / 'build/libs/youtube-home-assistant.rvp'
rvp.unlink(missing_ok=True)
subprocess.run(['./gradlew', 'jar', '--no-daemon', '--max-workers=2'], check=True)
rvp = ROOT / 'build/libs/youtube-home-assistant.rvp'
patchdex = ROOT / 'build/patchdex'
patchdex.mkdir(parents=True, exist_ok=True)
patchjar = patchdex / 'patches.jar'
with zipfile.ZipFile(rvp) as source, zipfile.ZipFile(patchjar, 'w') as target:
    for name in source.namelist():
        if name.endswith('.class'):
            target.writestr(name, source.read(name))
subprocess.run([*d8, '--release', '--min-api', '26', '--lib', str(android), '--classpath', str(cli),
    '--output', str(patchdex), str(patchjar)], check=True)
with zipfile.ZipFile(rvp, 'a', zipfile.ZIP_DEFLATED) as bundle:
    bundle.write(patchdex / 'classes.dex', 'classes.dex')
print(rvp)
