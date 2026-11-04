"""Build an offline-only debug APK with Google's SDK tools and the installed JDK.
Usage: python build.py [--prepare-only | --test-only]
No global environment or SDK configuration is modified.
"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / '.tools'
SDK = TOOLS / 'sdk'
BUILD = ROOT / 'build'
JAVA = Path(os.environ.get('JAVA_HOME', '')) / 'bin'

def run(args):
    # Windows SDK native tools cannot reliably open non-ASCII absolute paths.
    args = [str(a.relative_to(ROOT)) if isinstance(a, Path) and a.is_relative_to(ROOT) else str(a) for a in args]
    subprocess.run(args, cwd=ROOT, check=True)

def jdk(name):
    p = JAVA / (name + '.exe' if os.name == 'nt' else name)
    return str(p) if p.is_file() else shutil.which(name) or name

def fetch(url, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = target.with_suffix(target.suffix + '.part')
    print('Download:', url, flush=True)
    with urllib.request.urlopen(url, timeout=120) as r, temp.open('wb') as f:
        shutil.copyfileobj(r, f)
    temp.replace(target)

def prepare():
    # Official repository metadata pins both archive URL and SHA-1, verified before extraction.
    platform = SDK / 'android-35' / 'android.jar'
    bt = SDK / 'android-15'
    if platform.exists() and (bt / 'lib' / 'apksigner.jar').exists():
        return platform, bt
    TOOLS.mkdir(exist_ok=True)
    metadata = TOOLS / 'repository.xml'
    if not metadata.exists(): fetch('https://dl.google.com/android/repository/repository2-1.xml', metadata)
    root = ET.parse(metadata).getroot()
    host = 'windows' if os.name == 'nt' else 'linux'
    for package in root:
        name = package.attrib.get('path')
        if name not in ('platforms;android-35', 'build-tools;35.0.0'): continue
        for archive in package.findall('./archives/archive'):
            if archive.findtext('host-os') not in (None, host): continue
            complete = archive.find('complete')
            url = complete.findtext('url')
            if name == 'platforms;android-35' and url != 'platform-35_r02.zip': continue
            digest = complete.findtext('checksum')
            dest = TOOLS / Path(url).name
            if not dest.exists(): fetch('https://dl.google.com/android/repository/' + url, dest)
            if hashlib.sha1(dest.read_bytes()).hexdigest() != digest:
                raise RuntimeError('SDK archive checksum mismatch: ' + str(dest))
            SDK.mkdir(exist_ok=True)
            with zipfile.ZipFile(dest) as z:
                for member in z.infolist():
                    candidate = (SDK / member.filename).resolve()
                    if not candidate.is_relative_to(SDK.resolve()): raise RuntimeError('Unsafe SDK member')
                z.extractall(SDK)
            print('Ready:', name, flush=True)
            break
    if not platform.exists() or not (bt/'lib'/'apksigner.jar').exists():
        raise RuntimeError('Unexpected SDK layout; inspect .tools/sdk')
    return platform, bt

def tests():
    classes = BUILD / 'checks'
    classes.mkdir(parents=True, exist_ok=True)
    src = ROOT / 'app/src/main/java/cn/local/tilecounter'
    run([jdk('javac'), '-encoding','UTF-8','--release','8','-d',classes,src/'Counter.java',src/'Recognition.java',src/'PokerVision.java',src/'PokerSession.java',ROOT/'tests/CoreCheck.java',ROOT/'tests/SessionCheck.java',ROOT/'tests/VisionReplay.java'])
    run([jdk('java'),'-cp',classes,'cn.local.tilecounter.CoreCheck'])
    run([jdk('java'),'-cp',classes,'cn.local.tilecounter.SessionCheck'])
    if (BUILD/'feedback').is_dir():
        run([jdk('java'),'-cp',classes,'cn.local.tilecounter.VisionReplay']+sorted((BUILD/'feedback').glob('*.jpg')))

def build(platform, bt):
    for sub in ('classes','dex','resources'):
        target=(BUILD/sub).resolve()
        if not target.is_relative_to((ROOT/'build').resolve()): raise RuntimeError('Unsafe build path')
        if target.exists(): shutil.rmtree(target)
        target.mkdir(parents=True,exist_ok=True)
    aapt = bt / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
    # AGP supplies the package and version itself; standalone aapt2 needs them in its input.
    manifest = BUILD/'AndroidManifest.xml'
    original = (ROOT/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
    manifest.write_text(original.replace('<manifest ', '<manifest package="cn.local.tilecounter" android:versionCode="5" android:versionName="0.5.0" ', 1),encoding='utf-8')
    run([aapt,'compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip'])
    run([aapt,'link','-o',BUILD/'resources.apk','-I',platform,'--manifest',manifest,'--java',BUILD/'resources','-A',ROOT/'app/src/main/assets',BUILD/'resources.zip'])
    sources = list((ROOT/'app/src/main/java').rglob('*.java')) + list((BUILD/'resources').rglob('*.java'))
    response=BUILD/'sources.txt'
    response.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8')
    run([jdk('javac'),'-encoding','UTF-8','--release','8','-classpath',platform,'-d',BUILD/'classes','@'+str(response)])
    run([jdk('jar'),'cf',BUILD/'classes.jar','-C',BUILD/'classes','.'])
    run([jdk('java'),'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform,'--min-api','26','--output',BUILD/'dex',BUILD/'classes.jar'])
    unsigned=BUILD/'unsigned.apk'
    shutil.copyfile(BUILD/'resources.apk',unsigned)
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as z:
        for dex in (BUILD/'dex').glob('*.dex'): z.write(dex,dex.name)
    align=bt/('zipalign.exe' if os.name=='nt' else 'zipalign')
    run([align,'-f','4',unsigned,BUILD/'aligned.apk'])
    key=TOOLS/'debug.jks'
    if not key.exists():
        run([jdk('keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Local Debug','-keyalg','RSA','-keysize','2048','-validity','3650','-noprompt'])
    apk=BUILD/'FloatCounter-v0.5.apk'
    signer=[jdk('java'),'-jar',bt/'lib/apksigner.jar']
    run(signer+['sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,BUILD/'aligned.apk'])
    run(signer+['verify','--verbose',apk])
    print('APK:',apk,flush=True)
    print('SHA256:',hashlib.sha256(apk.read_bytes()).hexdigest(),flush=True)

if __name__ == '__main__':
    BUILD.mkdir(exist_ok=True)
    if '--prepare-only' in sys.argv: prepare()
    elif '--test-only' in sys.argv: tests()
    else:
        tests()
        build(*prepare())
