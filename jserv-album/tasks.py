"""
invoke make
"""
import shutil
import sys
from types import LambdaType
from dataclasses import dataclass
from typing import cast
from anson.io.odysz.common import Utils
from anson.io.odysz.utils import zip2
from invoke import task
import os

from anson.io.odysz.anson import Anson

# version = '0.7.6'
"""
synode.py3, jserv-album-0.7.#.jar
"""

# apk_ver = '0.7.3'

# html_jar_v = '0.1.8'
"""
html-web-#.#.#.jar
"""

# web_ver = '0.4.2'
"""
album-web-#.#.#.jar
"""

# vol_files = {"volume": ["jserv-main.db", "doc-jserv.db"]}
# vol_resource = {"volume": "volume/*"}
# dist_dir = f'build-{version}'
# android_dir = '../../anclient/examples/example.android'


version_pattern = '[0-9\\.]+'
synuser_pswd_pattern = '\"pswd\": \"[^"]*\"'

# @dataclass
# class TaskConfig(Anson):
#     version: str
#     apk_ver: str
#     html_jar_v: str
#     web_ver: str
#     host_json: str
#     vol_files: dict
#     vol_resource: dict
#     registry_dir: str
#     android_dir: str
#     default_pswd: str
#     dist_dir: str
#     '''
#     Replacing dictionary.json/registry/synusers[0].pswd, pattern of tasks.synuser_pswd_pattern

#     '''

#     # def __init__(self, version: str, apk_ver: str, html_jar_v: str, web_ver: str, host_json: str):
#     #     self.version = version
#     #     self.apk_ver = apk_ver
#     #     self.html_jar_v = html_jar_v
#     #     self.web_ver = web_ver
#     #     self.host_json = host_json

#     def __init__(self):
#         super().__init__()

try: import semanticshare
except ImportError:
    print('Please install the semantics sharing layer: pip install semantics.py3')
    sys.exit(1)

from semanticshare.io.oz.invoke import SynodeTask

taskcfg = cast(SynodeTask, Anson.from_file('tasks.config'))

@task
def create_volume(c):
    for vol, fs in taskcfg.vol_files.items():
        if not os.path.isdir(vol):
            os.mkdir(vol)
        for fn in fs: 
            with open(os.path.join(vol, fn), 'a') as vf:
                print(f'Volume file created: {os.path.join(vol, fn)}')
                vf.close()


# def updateApkRes(host_json, apkver):
def updateApkRes():
    """
    Update the APK resource record (ref-link) in the host.json file.
    
    Args:
        host_json (str): Path to the host.json file.
        res (dict): Dictionary containing the APK resource information.
    """
    print('Updating host.json with APK resource...', taskcfg.host_json)

    """
    from importlib.metadata import distribution
    try:
        dist = distribution('src.synodepy3')  # or 'synode-py3'
        print(f"Found {dist.name} version {dist.version}")
    except importlib.metadata.PackageNotFoundError:
        print("synode.py3 not found")
    
    """

    # Must install semantics.py3, because of
    # needing this to deserialize "io.oz.syntier.serv.ExternalHosts".
    hosts = Anson.from_file(taskcfg.host_json)
    print(os.getcwd(), taskcfg.host_json)
    print('host.json:', hosts)

    res = {'apk': f'res-vol/portfolio-{taskcfg.apk_ver}.apk'}
    hosts.resources.update(res)
    print('Updated host.json:', hosts.resources)

    hosts.toFile(taskcfg.host_json)
    print('host.json updated successfully.', hosts)

    return None

@task
def config(c):
    print('--------------    configuration   ------------------')

    this_directory = os.getcwd()

    print(f'-- synode version: {taskcfg.version} --'),

    version_file = os.path.join(this_directory, 'pom.xml')
    Utils.update_patterns(version_file, {
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version_pattern}</version>':
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{taskcfg.version}</version>',
    })

    version_file = os.path.join(taskcfg.android_dir, 'build.gradle')
    Utils.update_patterns(version_file, {
        f"app_ver = '{version_pattern}'": f"app_ver = '{taskcfg.apk_ver}'"
    })

    diction_file = os.path.join(taskcfg.registry_dir, 'dictionary.json')
    Utils.update_patterns(diction_file, {
        synuser_pswd_pattern: f'"pswd": "{taskcfg.default_pswd}"'
    })


@task
def clean(c):
    if not os.path.exists(taskcfg.dist_dir):
        os.makedirs(taskcfg.dist_dir, exist_ok=True)

    for item in os.listdir(taskcfg.dist_dir):
        item_path = os.path.join(taskcfg.dist_dir, item)
        if os.path.isfile(item_path):
            os.unlink(item_path)
        elif os.path.isdir(item_path):
            shutil.rmtree(item_path)


@task(config)
def build(c):
    # def cmd_build_synodepy3(version:str, web_ver:str, html_jar_v:str) -> str:
    def cmd_build_synodepy3() -> str:
        """
        Get the command to build the synode.py3 package.
        
        Returns:
            str: The command to build the package.
        """
        print(f'Building synode.py3 {taskcfg.version} with web-dist {taskcfg.web_ver}, html-service.jar {taskcfg.html_jar_v}...')

        if os.name == 'nt':
            return f'set SYNODE_VERSION={taskcfg.version} & set JSERV_JAR_VERSION={taskcfg.version} & set WEB_VERSION={taskcfg.web_ver} & set HTML_JAR_VERSION={taskcfg.html_jar_v} & invoke build'
        else:
            return f'export SYNODE_VERSION="{taskcfg.version}" JSERV_JAR_VERSION="{taskcfg.version}" WEB_VERSION="{taskcfg.web_ver}" HTML_JAR_VERSION="{taskcfg.html_jar_v}" && invoke build'

    buildcmds = [
        # replace app_ver with apk_ver?
        [taskcfg.android_dir, 'gradlew assembleRelease' if os.name == 'nt' else 'echo Android APK building skipped.'],

        # link: web-dist -> anclient/examples/example.js/album/web-dist
        ['.', f'rm -f web-dist/res-vol/portfolio-*.apk'],
        ['.', f'cp -f {taskcfg.android_dir}/app/build/outputs/apk/release/app-release.apk web-dist/res-vol/portfolio-{taskcfg.apk_ver}.apk' \
                if os.name == 'nt' else f'touch web-dist/res-vol/portfolio-{apk_ver}.apk' ],

        ['web-dist/private', lambda: updateApkRes()],
        ['.', 'cat web-dist/private/host.json'],
        ['web-dist', 'rm -f login-*.min.js* portfolio-*.min.js* report.html'],
        ['../../anclient/examples/example.js/album', 'webpack'],

        ['.', 'mvn clean compile package -DskipTests'],
        ['../../html-service/java', 'mvn clean compile package'],

        # use vscode bash for Windows
        # ['../synode.py', cmd_build_synodepy3(version, web_ver, html_jar_v)],
        ['../synode.py', cmd_build_synodepy3()],

        # ['../synode.py', 'invoke zipRegistry'],
        # ['.', f'mv ../synode.py/registry-ura-zsu-{version}.zip {dist_dir}']
    ]

    print('--------------  build  ------------------')
    for pth, cmd in buildcmds:
        if isinstance(cmd, LambdaType):
            print(pth, '&&', cmd)
            cwd = os.getcwd()
            os.chdir(pth)
            cmd = cmd()
            if cmd is not None:
                print(pth, '&&', cmd)
                ret = c.run(f'cd {pth} && {cmd}')
            os.chdir(cwd)
        else:
            print(pth, '&&', cmd)
            ret = c.run(f'cd {pth} && {cmd}')
            print('OK:', ret.ok, ret.stderr)
    return False

@task
def package(c, zip=f'portfolio-synode-{taskcfg.version}.zip'):
    """
    Create a ZIP file.
    
    Args:
        c: Invoke Context object for running commands.
        zip: Name of the output ZIP file.
    """
    resources = {
        f'bin/html-web-{taskcfg.html_jar_v}.jar': f'../../html-service/java/target/html-web-{taskcfg.html_jar_v}.jar', # clone at github/html-service
        f'bin/jserv-album-{taskcfg.version}.jar': f'target/jserv-album-{taskcfg.version}.jar',
        
        # https://exiftool.org/index.html
        'bin/exiftool.zip': './task-res-exiftool-13.21_64.zip',

        'WEB-INF': 'src/main/webapp/WEB-INF-0.7/*', # Do not replace with version.

        'bin/synode_py3-0.7-py3-none-any.whl': f'../synode.py/dist/synode_py3-{taskcfg.version}-py3-none-any.whl',
        "registry": "../synode.py/registry/*",
        'winsrv': '../synode.py/winsrv/*',
        "res": "../synode.py/src/synodepy3/res/*",

        'web-dist': 'web-dist/*'    # use a link for different Anclient folder name
                                    # ln -s ../Anclient/examples/example.js/album web-dist
                                    # mklink /D web-dist ..\anclient\examples\example.js\album
    }

    excludes = ['*.log', 'report.html']

    try:

        print('------------ package resources --------------')
        print(resources)

        err = False

        # Ensure the output directory for the ZIP exists
        output_dir = os.path.dirname(zip) or "."
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
        
        if os.path.isfile(zip):
            os.remove(zip)

        zip2(zip, {**resources, **taskcfg.vol_resource}, excludes)

        if not os.path.exists(taskcfg.dist_dir):
            os.makedirs(taskcfg.dist_dir, exist_ok=True)
        distzip = os.path.join(taskcfg.dist_dir, zip)
        if os.path.isfile(distzip):
            os.remove(distzip)

        print(zip, "->", distzip)
        os.rename(zip, distzip)

        print(f'Created ZIP file successfully: {distzip}' if not err else 'Errors while making target (creaded zip file)')

    except Exception as e:
        print(f"Error creating ZIP file: {str(e)}", file=sys.stderr)
        raise


@task(clean, create_volume, build, package)
def make(c):
    """
    Create a ZIP file with the specified resources.
    
    Args:
        c: Invoke Context object for running commands.
    """
    print('Package be created successfully.')

    # ret = c.run(f'cp ../snodepy3/registry-zsu-{version}.zip {dist_dir}')
    # print('OK:', ret.ok, ret.stderr)


if __name__ == '__main__':
    from invoke import Program
    Program(namespace=globals()).run()
