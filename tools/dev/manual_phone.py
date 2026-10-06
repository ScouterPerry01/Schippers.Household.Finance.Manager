"""Saves the emulator's screen as one of the manual's phone pictures.

python tools/dev/manual_phone.py <en|fr> <name> [picture.png]

Takes the screen with adb (or reads the given picture of it), cuts off Android's status bar (the
clock and icons), scales it to 540 pixels wide and keeps 256 colours, so it stays small inside the
app, then writes core/i18n/src/main/resources/hfm/manual/<lang>/images/phone-<name>.png.
Only the phone's own screen is ever captured. Needs Pillow. See tools/dev/README.md.
"""
import io
import os
import subprocess
import sys

from PIL import Image

ADB = os.path.join(os.environ.get('ANDROID_HOME', os.path.expanduser('~/AppData/Local/Android/Sdk')), 'platform-tools', 'adb')
STATUS_BAR = 0.05  # share of the height: 120 of 2400 pixels on the Pixel 7
WIDTH = 540


def main():
    lang, name = sys.argv[1], sys.argv[2]
    if len(sys.argv) > 3:
        image = Image.open(sys.argv[3])
    else:
        image = Image.open(io.BytesIO(subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True, check=True).stdout))
    image = image.convert('RGB')
    w, h = image.size
    image = image.crop((0, int(h * STATUS_BAR), w, h))
    image = image.resize((WIDTH, round(image.height * WIDTH / w)), Image.LANCZOS)
    image = image.quantize(256, method=Image.Quantize.MEDIANCUT)
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
    out = os.path.join(root, 'core', 'i18n', 'src', 'main', 'resources', 'hfm', 'manual', lang, 'images', 'phone-%s.png' % name)
    image.save(out, optimize=True)
    print(os.path.normpath(out), os.path.getsize(out), 'bytes')


if __name__ == '__main__':
    main()
