import sys
from pathlib import Path

# pi.run default pi.run(['--distpath', 'dist', '*.spec'])
dist_setup_dir = 'dist/synode-setup'
'''
One-dir bundle of setup-gui.exe, setup-cli.exe and uninstall-srv.exe, see synode-setup.spec.
'''
dist_setup_cli_exe = f'{dist_setup_dir}/setup-cli.exe'
dist_setup_gui_exe = f'{dist_setup_dir}/setup-gui.exe'
dist_uninstall_srv_exe = f'{dist_setup_dir}/uninstall-srv.exe'
dist_setup_internal = f'{dist_setup_dir}/_internal'

dist_upgrade_srv_exe = 'dist/upgrade.exe'
'''
Onefile, upgrade-srv.spec, name='upgrade'.
'''

'''
These two constants are kept at module level (cheap, no side effects) so that
other modules, e.g. tasks.py, can `from pyinstallerw import dist_setup_cli_exe,
dist_setup_gui_exe` WITHOUT triggering the actual PyInstaller build below.

Everything that has a side effect (requir_pkg checks, deleting old exes,
running PyInstaller) lives inside build_exes(), which only runs when this
file is executed directly (`python pyinstallerw.py`), not on import.
'''

def build_exes():
    import shutil
    import PyInstaller.__main__ as pi
    from anson.io.odysz.common import requir_pkg

    if sys.version_info.major < 3 or sys.version_info.minor == 9:
        print('''
        ****************************************************************************
        * WARNING:
        * Tests show that PyInsteller cannot collect dlls for cryptography 46.0.3 in Python 3.9,
        * but works fine with cryptography 41.0.7.
        ****************************************************************************
        ''')
        requir_pkg("cryptography", ["41.0.7"])

    # PyInstaller won't stop building if required or hidden modules are missing
    requir_pkg("prompt_toolkit")
    requir_pkg("semantics.py3")
    requir_pkg("anson.py3")
    requir_pkg("jre-mirror", "0.1.2")
    requir_pkg("pyside6")
    requir_pkg("pillow")
    requir_pkg("qrcode")
    requir_pkg("psutil")

    shutil.rmtree(dist_setup_dir, ignore_errors=True)
    Path.unlink(Path(dist_upgrade_srv_exe), missing_ok=True)

    print('Building synode-setup.spec (setup-gui, setup-cli, uninstall-srv) ...')
    pi.run(['--noconfirm', 'synode-setup.spec'])

    print('Building upgrade-srv.spec ...')
    pi.run(['--noconfirm', 'upgrade-srv.spec'])


if __name__ == '__main__':
    build_exes()
