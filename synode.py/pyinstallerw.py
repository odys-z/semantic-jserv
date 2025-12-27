import os
import sys
from pathlib import Path

import PyInstaller.__main__ as pi

from semanticshare.io.oz.invoke import requir_pkg
if sys.version_info.major < 3 or sys.version_info.minor == 9:
    print('''
    ****************************************************************************
    * WARNING:
    * Tests show that PyInsteller cannot collect dlls for cryptography 46.0.3 in Python 3.9,
    * but works fine with 41.0.7.
    ****************************************************************************
    ''')
    requir_pkg("cryptography", ["41.0.7"])


# PyInstaller won't stop building if required or hidden modules are missing
requir_pkg("prompt_toolkit")
requir_pkg("semantics.py3")
requir_pkg("anson.py3")
requir_pkg("jre-mirror")
requir_pkg("pyside6")
requir_pkg("pillow")
requir_pkg("qrcode")
requir_pkg("psutil")

Path.unlink(Path('dist/setup-cli.exe'), missing_ok=True)
Path.unlink(Path('dist/setup-gui.exe'), missing_ok=True)

print('Building with setup-gui.spec ...')
pi.run(['setup-gui.spec'])

print('Building setup-cli.spec ...')
pi.run(['setup-cli.spec'])
