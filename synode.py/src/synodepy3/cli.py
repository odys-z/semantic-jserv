import sys

from anson.io.odysz.common import Utils

# See https://stackoverflow.com/a/28154841
from .installer_api import InstallerCli, ping
from .commands import install_wsrv_byname, uninstall_wsrv_byname, install_htmlsrv, winsrv_synode, winsrv_websrv
from .installer_api import InstallerCli


def uninst_srv():
    import invoke
    cli = InstallerCli()
    cli.loadInitial()

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


def clean(vol: str = None):
    cli = InstallerCli()
    cli.loadInitial()
    cli.clean_install(vol)


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


if __name__ == '__main__':

    cmd = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else 'list'
    arg = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] else None
    arg2= sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None

    cmds = {
        "list": "list known synodes.",
        "load": "load deploy-path, load configurations",
        "install": "arg[0]: res-path, arg[1] volume-path\n\
        - setup the synode service resources by registering settings path and volume",

        "install-winsrv": "or i-w, arg[0] synode id (readable alais only), arg[1] bin resources path, default 'winsrv'\n\
        - install the synode service as a Windows service",

        # "install-web": "or i-web, arg[0] port, default 8900, install web service",
        "uninstall-srvname": "or i-srv, arg[0] port, default 8900, install web service",

        "uninstall-winsrv": "or ui-w, arg[0] bin resources path, default 'winsrv'\n\
        - uninstall the synode service installed as a Windows service",

        "start": "arg[0]: volume\n\
        - Start the node. Configurations must be setup correctly.\n\
          Wrong configuration may results in fail forever for this node.\n\
          Use test command for test starting.",

        "test": "try bring up the synode without triggering synchronization.",

        "start-web": "start-web <web-port, e.g. 8900> <jserv-port, e.g. 8964>\
        - start the python3 web server, album-web 0.4, at localhost.",

        "sync_in": "arg[0]: volume, arg[1]: number of seconds\n\
        - Update synchronization interval's setting.",

        "showip": "show local ip to be bound",
        "ping": "ping peer-id, peer-jserv, ping configured jserv"
    }

    cli = InstallerCli()
    cli.loadInitial()

    if cmd == 'list':
        print(cli.list_synodes())
    if cmd == 'load':
        print(cli.loadInitial(arg))

    elif cmd == 'install' or cmd == 'i':
        Utils.logi("Install synodes with settings:")
        cli = InstallerCli(arg)
        Utils.logi(cli.settings.toBlock(beautify=True))
        cli.install(arg, arg2) # setup

    elif cmd == 'uninstall-winsrv' or cmd == 'ui-w':
        if arg is not None:
            srvname = arg
        else:
            srvname = cli.settings.envars[winsrv_synode] #.gen_wsrv_name()

        print("Uninstalling ", srvname, "at port", cli.settings.port)
        try: uninstall_wsrv_byname(srvname)
        except: Utils.warn(f'Failed to uninstall service {srvname}')

        srvname = cli.settings.envars[winsrv_websrv]
        print("Uninstalling ", srvname, "at port", cli.settings.port)
        try: uninstall_wsrv_byname(srvname)
        except: Utils.warn(f'Failed to uninstall service {srvname}')

    elif cmd == 'install-winsrv' or cmd == 'i-w':
        srvname = cli.gen_wsrv_name()
        print("Installing ", srvname, "at port", cli.settings.port)
        install_wsrv_byname(srvname)
        install_htmlsrv(cli.gen_html_srvname())

    elif cmd == 'clean':
        clean(arg)

    elif cmd == 'start-web':
        startweb(int(arg))

    # elif cmd == 'install-web' or cmd == 'i-web':
    #     install_htmlsrv()

    elif cmd == 'uninstall-srvname' or cmd == 'u-srv':
        if arg is not None:
            srvname = arg
        else:
            srvname = cli.gen_wsrv_name()
        print("Uninstalling ", srvname)
        uninstall_wsrv_byname(srvname)

    elif cmd == 'sync_in':
        cli.loadInitial()
        sins = cli.registry.config.syncIns

    elif cmd == 'showip':
        InstallerCli.reportIp()
    elif cmd == 'ping':
        ping(arg, arg2)
    else:
        print('Usage: python3 installer-cli.py cmd.\nCommands:')
        for c in cmds:
            print(f'{c}\t: {cmds[c]}')
