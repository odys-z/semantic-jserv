
import os
import re
import signal
import sys
import time

from anson.io.odysz.common import Utils
from invoke import Collection, task, Context
from src.synodepy3.__version__ import jar_ver, html_srver

winsrv = 'winsrv'
install_html_w_bat = os.path.join(winsrv, "install-html-w.bat")
install_jserv_w_bat = os.path.join(winsrv, "install-jserv-w.bat")

@task
def run_jserv(c, bin = 'bin'):
    def signal_handler(sig, frame):
        print('Ctrl+C detected. Performing cleanup...')
        time.sleep(2) # TODO accept signal from service
        print('Cleanup finished. Exiting.')
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    cd = bin or 'bin'
    if os.name == 'nt':
        cd = re.sub('/', '\\\\', cd)

    try:
        ret = c.run(f'cd {cd} && java -jar bin/jserv-album-0.7.1.jar')
        print(ret.ok)
    except KeyboardInterrupt as e:
        print('KeyboardInterrupt', e)
        time.sleep(.5)


def uninstall_wsrv_byname(srvname: str):
    ctx = Context()
    cmd = f'{install_jserv_w_bat} uninstall {srvname}'
    print(cmd)
    ctx.run(cmd)


def install_wsrv_byname(srvname: str):
    Utils.update_patterns(install_jserv_w_bat, {'@set jar_ver=[0-9\\.]+': jar_ver})

    ctx = Context()
    cmd = f'{install_jserv_w_bat} install {srvname}'
    print(cmd)
    ctx.run(cmd)


def uninstall_htmlsrv(srvname: str):
    ctx = Context()
    cmd = f'{install_html_w_bat} uninstall {srvname}'
    print(cmd)
    ctx.run(cmd)


def install_htmlsrv(srvname: str):
    Utils.update_patterns(install_html_w_bat, {'@set jar_ver=[0-9\\.]+': html_srver})

    ctx = Context()
    cmd = f'{install_html_w_bat} install {srvname}'
    print(cmd)
    ctx.run(cmd)


if __name__ == '__main__':
    # Create a collection of tasks
    ns = Collection()
    ns.add_task(run_jserv)
    ctx = Context()

    # Call a task programmatically
    ns["run_jserv"](ctx)
