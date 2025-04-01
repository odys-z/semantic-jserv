
import os
import re
import signal
import sys
import time
from invoke import Collection, call, task, Context

@task
def run_jserv(c, bin = 'bin'):
    def signal_handler(sig, frame):
        print('Ctrl+C detected. Performing cleanup...')
        time.sleep(2) # TODO accept signal from server
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

@task
def install_winsrv(c, winsrv: str = 'winsrv'):
    cd = winsrv or 'winsrv'
    if os.name == 'nt':
        try:
            ret = c.run(f'cd {cd} && install.bat')
            print(ret.ok)
        except KeyboardInterrupt as e:
            print('KeyboardInterrupt', e)
            time.sleep(.5)

def uninstall_winservice(winsrv: str = 'winsrv'):
    ctx = Context()
    bin = winsrv or 'winsrv'
    cmd = f'{os.path.join(bin, 'uninstall.bat')} {bin}'
    print(cmd)
    ctx.run(cmd)


def install_winservice(synode: str, winsrv: str = 'winsrv'):
    ctx = Context()
    bin = winsrv or 'winsrv'
    cmd = f'{os.path.join(bin, 'install.bat')} {synode}'
    print(cmd)
    ctx.run(cmd)


if __name__ == '__main__':
    # Create a collection of tasks
    ns = Collection()
    ns.add_task(run_jserv)
    ctx = Context()

    # Call a task programmatically
    ns["run_jserv"](ctx)
