# -*- mode: python ; coding: utf-8 -*-
'''
One-dir bundle shared by setup-gui.exe, setup-cli.exe and uninstall-srv.exe.

    dist/synode-setup/
        setup-gui.exe
        setup-cli.exe
        uninstall-srv.exe
        _internal/          python runtime, PySide6/Qt, synodepy3/synode.json, res/, ...

All three exes are shipped at the package root, next to _internal/, and are run with the
package root as cwd (WEB-INF, winsrv, vol, ...), so the layout must be kept as is.

upgrade.exe is NOT in this bundle (see upgrade-srv.spec): it unpacks the update package over
the package root, including _internal/, whose dlls and pyds would be locked if upgrade.exe
loaded them from there.
'''

datas = [('src/synodepy3/synode.json', 'synodepy3'),
         ('src/synodepy3/res', 'res')]


def analysis(script: str, hiddenimports: list):
    return Analysis(
        [script],
        pathex=[],
        binaries=[],
        datas=datas,
        hiddenimports=hiddenimports,
        hookspath=[],
        hooksconfig={},
        runtime_hooks=[],
        excludes=[],
        noarchive=False,
        optimize=0,
    )


def exe(a, name: str, icon: str):
    return EXE(
        PYZ(a.pure),
        a.scripts,
        [],
        exclude_binaries=True,
        name=name,
        debug=False,
        bootloader_ignore_signals=False,
        strip=False,
        upx=True,
        console=True,
        disable_windowed_traceback=False,
        argv_emulation=False,
        target_arch=None,
        codesign_identity=None,
        entitlements_file=None,
        icon=[icon],
    )


a_gui = analysis('src/synodepy3/__main__.py', [])
a_cli = analysis('src/synodepy3/prompt.py', ['prompt-toolkit'])
a_uni = analysis('src/synodepy3/uninstall_cli.py', ['prompt-toolkit'])

coll = COLLECT(
    exe(a_gui, 'setup-gui', 'setup.ico'), a_gui.binaries, a_gui.datas,
    exe(a_cli, 'setup-cli', 'setup.ico'), a_cli.binaries, a_cli.datas,
    exe(a_uni, 'uninstall-srv', 'uninstall.ico'), a_uni.binaries, a_uni.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='synode-setup',
)
