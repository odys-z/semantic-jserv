"""
    Helper for setup environment to build Wheel.

    What to
    -------
    configure version, override by caller's envrionment variables.

    #. invoke make:
        -> export env varialbles, SYNODE_VERSION, JSERV_JAR_VERSION, HTML_JAR_VERSION, WEB_VERSION,
        -> run build.

    #. py -m build:
        -> use default vers in __version__.py & *.bat, build wheel.

    How to
    ------

        invoke build

    Don't directly build with:

        pip install wheel
        python -m build
"""

import errno
import os
import shutil
from types import LambdaType

from anson.io.odysz.common import Utils
from anson.io.odysz.utils import zip2
from invoke import task, Context

SYNODE_VERSION = 'SYNODE_VERSION'
JSERV_JAR_VERSION = 'JSERV_JAR_VERSION'
HTML_JAR_VERSION = 'HTML_JAR_VERSION'
WEB_VERSION = 'WEB_VERSION'
REGISTRY_ZIP = 'REGISTRY_ZIP'

ORG = 'ura'
DOMAIN = 'zsu'

"""
    Versions configured locally, overriden by environment variables.
"""
vers = {
    SYNODE_VERSION:    '0.7.6',
    JSERV_JAR_VERSION: '0.7.5',
    HTML_JAR_VERSION:  '0.1.8',
    WEB_VERSION:       '0.4.2',
    # REGISTRY_ZIP: f'registry-{ORG}-{DOMAIN}-0.7.3.zip'
}

res_toclean = ['dist', '*egg-info']

@task
def config(c):
    print('--------------    configuration   ------------------')

    this_directory = os.getcwd()

    version = (os.getenv(SYNODE_VERSION) or vers[SYNODE_VERSION]).strip()
    vers[SYNODE_VERSION] = version
    # vers[REGISTRY_ZIP] = f'registry-{ORG}-{DOMAIN}-{vers[SYNODE_VERSION]}.zip'
    print(f'-- synode version: {version} --'),

    serv_jar_ver = (os.getenv(JSERV_JAR_VERSION) or vers[JSERV_JAR_VERSION]).strip()
    vers[JSERV_JAR_VERSION] = serv_jar_ver
    print(f'-- jserv version: {serv_jar_ver} --'),

    html_srver = (os.getenv(HTML_JAR_VERSION) or vers[HTML_JAR_VERSION]).strip()
    print(f'-- html web service version: {html_srver} --'),

    web_ver = (os.getenv(WEB_VERSION) or vers[WEB_VERSION]).strip()
    print(f'-- web version: {web_ver} --'),

    version_file = os.path.join(this_directory, 'src', 'synodepy3', '__version__.py')
    Utils.update_patterns(version_file, {
        'synode_ver = "[0-9\\.]+"': f'synode_ver = "{version}"',
        'jar_ver = "[0-9\\.]+"': f'jar_ver = "{serv_jar_ver}"',
        'web_ver = "[0-9\\.]+"': f'web_ver = "{web_ver}"',
        'html_srver = "[0-9\\.]+"': f'html_srver = "{html_srver}"'
    })

    Utils.update_patterns('src/synodepy3/synode.json', {'"version"\\s*:\\s*"[0-9\\.]+",': f'"version": "{version}",'})

    Utils.update_patterns('pyproject.toml', {'version = "[0-9\\.]+" # ': f'version = "{version}" # '})

@task
def zipRegistry(c):
    print('config =', vers, "zip =", vers[REGISTRY_ZIP])
    zip2(vers[REGISTRY_ZIP], {"zsu": "registry-deploy/*"}, ['*.zip'])


@task(config)
def build(c: Context):
    def py():
        return 'py' if os.name == 'nt' else 'python3'

    def rm_any(res):
        try:
            if os.path.isfile(res):
                os.remove(res)
            else:
                shutil.rmtree(res, ignore_errors=False)
            print(f"Successfully removed {res}")
        except FileNotFoundError:
            pass
        except PermissionError:
            print(f"Permission denied: Unable to remove {res}")
        except OSError as e:
            if e.errno != errno.ENOENT:  # Ignore "No such file or directory" errors
                pass
            else:
                print(f"Path {res} does not exist")
        pass

    def rm_dist():
        for res in res_toclean:
            rm_any(res)
        return None

    # from src.synodepy3.__version__ import synode_ver
    buildcmds = [
        ['.', lambda: rm_dist()],
        ['.', f'{py()} -m build']
    ]

    print('--------------       building     ------------------')
    for pth, cmd in buildcmds:
        print("[Build in]", pth, '&&', cmd)
        if isinstance(cmd, LambdaType):
            cwd = os.getcwd()
            os.chdir(pth)
            cmd = cmd()
            print(pth, f'cmd finished, cmd request: {cmd}')
            if cmd is not None:
                print(pth, '&&', cmd)
                ret = c.run(f'cd {pth} && {cmd}')
                print('OK:', ret.ok, ret.stderr)
            else:
                print('OK: cmd <- None')
            os.chdir(cwd)
        else:
            ret = c.run(f'cd {pth} && {cmd}')
            print('OK:', ret.ok, ret.stderr)
    return False

