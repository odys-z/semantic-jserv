
import os
import re
import signal
import sys
import time
from invoke import Collection, call, task, Context

@task
def run_jserv(c):
    def signal_handler(sig, frame):
        print('Ctrl+C detected. Performing cleanup...')
        time.sleep(5)  # Wait for 5 seconds
        print('Cleanup finished. Exiting.')
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    # cd = '../../../semantic-jserv/jserv-album/bin'
    cd = 'bin'
    if os.name == 'nt':
        cd = re.sub('/', '\\\\', cd)
    # ret = c.run(f'cd {cd} && java -jar bin/jserv-album-0.7.1.jar')
    try:
        ret = c.run(f'cd {cd} && java -jar bin/jserv-album-0.7.1.jar')
        print(ret.ok)
    except KeyboardInterrupt as e:
        print('KeyboardInterrupt', e)
        time.sleep(.5)


if __name__ == '__main__':
    # Create a collection of tasks
    ns = Collection()
    ns.add_task(run_jserv)
    ctx = Context()

    # Call a task programmatically
    ns["run_jserv"](ctx)
