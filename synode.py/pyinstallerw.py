import sys
import PyInstaller.__main__ as pi

if sys.version_info.major < 3 or sys.version_info.minor == 9:
    print('''
    ****************************************************************************
    * WARNING:
    * Tests show that PyInsteller cannot collect dlls for cryptography 46.0.3 in Python 3.9,
    * but works fine with 41.0.7.
    ****************************************************************************
    ''')
    from semanticshare.io.oz.invoke import requir_pkg
    requir_pkg("cryptography", ["41.0.7"])
    

print('Building with setup-gui.spec ...')
pi.run(['setup-gui.spec'])

print('Building setup-cli.spec ...')
pi.run(['setup-cli.spec'])
