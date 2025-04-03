import platform
import sys
import time

from src.synodepy3.installer_api import InstallerCli, ping
# Why above line works but not this? See https://stackoverflow.com/a/28154841
from src.synodepy3.commands import install_jserv, uninstall_jserv, run_htmlsrv
from src.synodepy3 import websrv_delete
from src.synodepy3.websrv_delete import AlbumWeb
from synodepy3.commands import install_htmlsrv, uninstall_htmlsrv
from synodepy3.websrv_delete import album_web_dir


# def is_winadmin():
#     try:
#         # Open the current process token
#         import win32security
#         import win32con
#         token = win32security.OpenProcessToken(
#             win32api.GetCurrentProcess(),
#             win32con.TOKEN_QUERY
#         )
#         # Get elevation information
#         elevation = win32security.GetTokenInformation(token, win32security.TokenElevation)
#         return elevation  # Returns True if elevated, False if not
#     except:
#         return False


def clean(vol: str = None):
    cli = InstallerCli()
    cli.loadInitial()
    cli.clean_install(vol)


def startweb(port: int = 8900):
    worker = None
    httpd = None
    try:
        # httpd = InstallerCli.start_web(8900 if port is None else port)
        httpd, worker = AlbumWeb.start_web(port)
        input("Press Enter to close web server...")
    finally:
        if httpd is not None:
            httpd.shutdown()
        if worker is not None:
            worker.join()


if __name__ == '__main__':

    cmd = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else 'list'
    arg = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] else None
    arg2= sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None

    cmds = {
        "list": "list known synodes.",
        "load": "load deploy-path, load configurations",
        "install": "arg[0]: res-path, arg[1] volume-path\n\
        - install the synode service with registration in res-path, volume at volume-path",

        "install-winsrv": "or i-w, arg[0] synode id (readable alais only), arg[1] bin resources path, default 'winsrv'\n\
        - install the synode service as a Windows service",

        "install-web": "or i-web, arg[0] port, default 8900, install web service",

        "uninstall-web": "or u-web, uninstall web service",

        "uninstall-winsrv": "or ui-w, arg[0] bin resources path, default 'winsrv'\n\
        - uninstall the synode service installed as a Windows service",

        "start": "arg[0]: volume\n\
        - Start the node. Configurations must be setup correctly.\n\
          Wrong configuration may results in fail forever for this node.\n\
          Use test command for test starting.",

        "test": "try bring up the synode without triggering synchronization.",

        "start-web": "start-web <web-port, e.g. 8900> <jserv-port, e.g. 8964>\
        - start the web server, album-web 0.4, at localhost.",

        "sync_in": "arg[0]: volume, arg[1]: number of seconds\n\
        - Update synchronization interval's setting.",

        "showip": "show local ip to be bound",
        "ping": "ping peer-id, peer-jserv, ping configured jserv"
    }

    cli = InstallerCli()
    if cmd == 'list':
        print(cli.list_synodes())
    if cmd == 'load':
        print(cli.loadInitial(arg))

    elif cmd == 'install' or cmd == 'i':
        cli = InstallerCli(arg)
        cli.install(arg, arg2)

    elif cmd == 'uninstall-winsrv' or cmd == 'ui-w':
        cli.loadInitial()
        uninstall_jserv(arg)

    elif cmd == 'install-winsrv' or cmd == 'i-w':
        cli.loadInitial()
        install_jserv(cli.registry.config.synid, arg)

    elif cmd == 'clean':
        clean(arg)

    elif cmd == 'start-web':
        run_htmlsrv()
    elif cmd == 'install-web' or cmd == 'i-web':
        install_htmlsrv(album_web_dir)
    elif cmd == 'uninstall-web' or cmd == 'u-web':
        uninstall_htmlsrv()

    # deleting
    # elif cmd == 'start-web':
    #     startweb(8900 if arg is None else arg)
    # elif cmd == 'install-web' or cmd == 'i-web':
    #     import win32api
    #     import win32com.shell.shell as shell
    #
    #     print(1, cmd)
    #
    #     if platform.system() == "Windows" and not is_winadmin():
    #         py_args = '-m src.synodepy3.cli i-web'
    #         print(2, f"Requesting administrative privileges for {py_args}...")
    #         shell.ShellExecuteEx(
    #             lpVerb="runas",
    #             lpFile=sys.executable,
    #             lpParameters=py_args,
    #             nShow=1  # Show the window
    #         )
    #     else:
    #         input(3)
    #         exe_cmd = f'{" ".join(sys.argv)}'
    #         print(exe_cmd)
    #         print('===========================')
    #         AlbumWeb.install()
    #         input('pausing elevated...')
    #
    # elif cmd == 'uninstall-web' or cmd == 'u-web':
    #     import win32api
    #     import win32com.shell.shell as shell
    #
    #     print(1, cmd)
    #
    #     if platform.system() == "Windows" and not is_winadmin():
    #         py_args = '-m src.synodepy3.cli u-web'
    #         print(2, f"Requesting administrative privileges for py {py_args}...")
    #         shell.ShellExecuteEx(
    #             lpVerb="runas",
    #             lpFile=sys.executable,
    #             lpParameters=py_args,
    #             nShow=1  # Show the window
    #         )
    #         # sys.exit(0)
    #     else:
    #         input(3)
    #         AlbumWeb.uninstall()
    #         input('pausing elevated...')

    elif cmd == 'sync_in':
        cli.loadInitial()
        sins = cli.registry.config.syncIns

    elif cmd == 'showip':
        cli.reportIp()
    elif cmd == 'ping':
        ping(arg, arg2)
    else:
        print('Usage: python3 installer-cli.py cmd.\nCommands:')
        for c in cmds:
            print(f'{c}\t: {cmds[c]}')
