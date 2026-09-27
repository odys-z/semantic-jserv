'''
The wrapper of cli:upgrade_srv().

Entry-point: synode-upgrade-srv / upgrade.exe

ISSUE
=====

1. Currently, the windows service's bin/jar has version, so cannot stop/restart to upgrade a synode on Windows

    - services name has version number (not the name for new version)
    - registered jar file for service has version number

    e.g. bin/jserv-album-0.7.10.jsr -> 0.8.0

2. The administration permission confirm dialog is irritating 

3. The jar version is updated while installation, by commands.py

    def install_wsrv_byname(srvname: str):
        Utils.update_patterns(install_jserv_w_bat, {'@set jar_ver=[0-9\\.]+': f'@set jar_ver={jar_ver}'})
        ctx = Context()
        cmd = f'{install_jserv_w_bat} install {srvname}'
        ctx.run(cmd)
    
    The version replacement should happen at build time.

    - note: this is also why the unzipped package come with winsrv/install-jserv-w.bat has line

            jar_ver = 0.7.7

    For the future design, should the jar file contain version number?
    If yes, the windows service has to ask 8 times of permission to stop, uninstall and reinstall, strart the 2 services 

4. The most important one is the updating is danger to interfere the jserv propogation and registry updating.

    This will make the synchronizing cannot resolve doc-refs even after the syn-exchange has completed.

    The correct way to solve this is built upon Mesh or P2P.

    Note: The offline RDBMS synchronization is still essential, as a PC can be shutting down while uploading a doc.
'''

from synodepy3.cli import upgrade_srv

if __name__ == '__main__':
    upgrade_srv()
    input('Press Enter to quit ...')