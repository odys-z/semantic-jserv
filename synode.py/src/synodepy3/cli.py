import sys

from .commands import uninstall_wsrv_byname, winsrv_synode, winsrv_websrv, update_srv
# See https://stackoverflow.com/a/28154841
from .installer_api import InstallerCli, posix_path


def uninst_srv():
    import invoke
    cli = InstallerCli()
    cli.load_settings()

    if winsrv_websrv not in cli.settings.envars:
        print(f'Error: cannot find target service name. The configuration are damaged.')
        print(f'Please follow the tips for uninstalling services manually:')
        print(f'https://odys-z.github.io/products/portfolio/synode/setup.html#uninstall-windows-service-manually')
        return

    try:
        srvname = cli.settings.envars[winsrv_synode]
        uninstall_wsrv_byname(srvname)
    except invoke.exceptions.UnexpectedExit as e:
        print(f"Error uninstalling jserv-album: {e}", file=sys.stderr)

    try:
        srvname = cli.settings.envars[winsrv_websrv]
        uninstall_wsrv_byname(srvname)
    except invoke.exceptions.UnexpectedExit as e:
        print(f"Error uninstalling html-service: {e}", file=sys.stderr)


def upgrade_srv(zip_path: str = None):
    """
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
    """
    args = sys.argv[1:]
    zpath = zip_path if zip_path is not None else (args[0] if args else None)
    if zpath is None:
        print('Usage: synode-upgrade-srv <path-to-update.zip or .tar.gz>')
        return
    update_srv(posix_path(zpath))


def startweb(port: int = 8900):
    """
    Entry-point: synode-start-web
    """
    worker = None
    httpd = None
    try:
        # httpd = InstallerCli.start_web(8900 if port is None else port)
        httpd, worker = InstallerCli.start_web(port)
        input("Press Enter to close web server...")
    finally:
        if httpd is not None:
            httpd.shutdown()
        if worker is not None:
            worker.join()

