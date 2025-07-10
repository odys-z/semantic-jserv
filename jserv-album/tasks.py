"""
invoke make
"""
import fnmatch
import re
import shutil
import sys
from types import LambdaType
from anson.io.odysz.common import Utils
from anson.io.odysz.utils import zip2
from invoke import task
import zipfile
import os

from anson.io.odysz.anson import Anson

version = '0.7.5'
"""
synode.py3, jserv-album-0.7.4.jar
"""

apk_ver = '0.7.3'

html_jar_v = '0.1.7'
"""
html-web-#.#.#.jar
"""

web_ver = '0.4.1'
"""
album-web-#.#.#.jar
"""

version_pattern = '[0-9\\.]+'

vol_files = {"volume": ["jserv-main.db", "doc-jserv.db"]}
vol_resource = {"volume": "volume/*"}

dist_dir = f'build-{version}'

android_dir = '../../anclient/examples/example.android'

@task
def create_volume(c):
    for vol, fs in vol_files.items():
        if not os.path.isdir(vol):
            os.mkdir(vol)
        for fn in fs: 
            with open(os.path.join(vol, fn), 'a') as vf:
                print(f'Volume file created: {os.path.join(vol, fn)}')
                vf.close()

def updateApkRes(host_json, apkver):
    """
    Update the APK resource record (ref-link) in the host.json file.
    
    Args:
        host_json (str): Path to the host.json file.
        res (dict): Dictionary containing the APK resource information.
    """
    print('Updating host.json with APK resource...', host_json)

    """
    from importlib.metadata import distribution
    try:
        dist = distribution('src.synodepy3')  # or 'synode-py3'
        print(f"Found {dist.name} version {dist.version}")
    except importlib.metadata.PackageNotFoundError:
        print("synode.py3 not found")
    
    """

    # Must install synode.py3, because
    # need this to deserialize "io.oz.syntier.serv.ExternalHosts".
    # ISSUE: this is a messy as the type, ExternalHosts, used in host_json,
    # is assuming types of synode.py3 are available.
    Anson.java_src('src', ['synode_py3'])

    hosts = Anson.from_file(host_json)
    print(os.getcwd(), host_json)
    print('host.json:', hosts)

    res = {'apk': f'res-vol/portfolio-{apkver}.apk'}
    hosts.resources.update(res)
    print('Updated host.json:', hosts.resources)

    hosts.toFile(host_json)
    print('host.json updated successfully.', hosts)

    return None

@task
def config(c):
    print('--------------    configuration   ------------------')

    # Anson.java_src('src', ['synode_py3'])

    this_directory = os.getcwd()

    print(f'-- synode version: {version} --'),

    version_file = os.path.join(this_directory, 'pom.xml')
    Utils.update_patterns(version_file, {
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version_pattern}</version>':
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version}</version>',
    })

    version_file = os.path.join(android_dir, 'build.gradle')
    Utils.update_patterns(version_file, {
        f"app_ver = '{version_pattern}'": f"app_ver = '{apk_ver}'"
    })

@task
def clean(c):
    if not os.path.exists(dist_dir):
        os.makedirs(dist_dir, exist_ok=True)

    for item in os.listdir(dist_dir):
        item_path = os.path.join(dist_dir, item)
        if os.path.isfile(item_path):
            os.unlink(item_path)
        elif os.path.isdir(item_path):
            shutil.rmtree(item_path)


@task(config)
def build(c):
    def cmd_build_synodepy3(version:str, web_ver:str, html_jar_v:str) -> str:
        """
        Get the command to build the synode.py3 package.
        
        Returns:
            str: The command to build the package.
        """
        print(f'Building synode.py3 {version} with web-dist {web_ver}, html-service.jar {html_jar_v}...')

        if os.name == 'nt':
            return f'set SYNODE_VERSION={version} & set JSERV_JAR_VERSION={version} & set WEB_VERSION={web_ver} & set HTML_JAR_VERSION={html_jar_v} & invoke build'
        else:
            return f'export SYNODE_VERSION="{version}" JSERV_JAR_VERSION="{version}" WEB_VERSION="{web_ver}" HTML_JAR_VERSION="{html_jar_v}" && invoke build'

    buildcmds = [
        # replace app_ver with apk_ver?
        [android_dir, 'gradlew assembleRelease' if os.name == 'nt' else 'echo Android APK building skipped.'],

        # link: web-dist -> anclient/examples/example.js/album/web-dist
        ['.', f'rm -f web-dist/res-vol/portfolio-*.apk'],
        ['.', f'cp -f {android_dir}/app/build/outputs/apk/release/app-release.apk web-dist/res-vol/portfolio-{apk_ver}.apk' \
                if os.name == 'nt' else f'touch web-dist/res-vol/portfolio-{apk_ver}.apk' ],

        ['web-dist/private', lambda: updateApkRes('host.json', apk_ver)],
        ['.', 'cat web-dist/private/host.json'],

        ['../../anclient/examples/example.js/album', 'webpack'],
        ['.', 'mvn clean compile package -DskipTests'],
        ['../../html-service/java', 'mvn clean compile package'],

        # use vscode bash for Windows
        ['../synode.py', cmd_build_synodepy3(version, web_ver, html_jar_v)],

        # ['../synode.py', 'invoke zipRegistry'],
        ['.', f'mv ../synode.py/registry-ura-zsu-{version}.zip {dist_dir}']
    ]

    print('--------------  package  ------------------')
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
def package(c, zip=f'jserv-portfolio-{version}.zip'):
    """
    Create a ZIP file (jserv.zip).
    
    Args:
        c: Invoke Context object for running commands.
        zip: Name of the output ZIP file (default: "jserv-album.zip").
    """
    resources = {
        f'bin/html-web-{html_jar_v}.jar': f'../../html-service/java/target/html-web-{html_jar_v}.jar', # clone at github/html-service
        f'bin/jserv-album-{version}.jar': f'target/jserv-album-{version}.jar',
        
        # https://exiftool.org/index.html
        'bin/exiftool.zip': './task-res-exiftool-13.21_64.zip',

        'bin/synode_py3-0.7-py3-none-any.whl': f'../synode.py/dist/synode_py3-{version}-py3-none-any.whl',
        'WEB-INF': f'src/main/webapp/WEB-INF-0.7.3/*', # Do not replace with version.
        'winsrv': '../synode.py/winsrv/*',
        'web-dist': 'web-dist/*'    # use a link for different Anclient folder name
                                    # ln -s ../Anclient/examples/example.js/album web-dist
                                    # mklink /D web-dist ..\anclient\examples\example.js\album
    }

    excludes = ['*.log', 'report.html']

    try:

        print('-------------- resources ------------------')
        print(resources)

        err = False

        # Ensure the output directory for the ZIP exists
        output_dir = os.path.dirname(zip) or "."
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
        
        if os.path.isfile(zip):
            os.remove(zip)

        zip2(zip, {**resources, **vol_resource}, excludes)

        if not os.path.exists(dist_dir):
            os.makedirs(dist_dir, exist_ok=True)
        distzip = os.path.join(dist_dir, zip)
        if os.path.isfile(distzip):
            os.remove(distzip)

        print(zip, "->", distzip)
        os.rename(zip, distzip)

        print(f'Created ZIP file successfully: {distzip}' if not err else 'Errors while making target (creaded zip file)')

    except Exception as e:
        print(f"Error creating ZIP file: {str(e)}", file=sys.stderr)
        raise


def packagev72(c, zip=f'jserv-portfolio-{version}.zip'):
    """
    Create a ZIP file (jserv.zip).
    
    Args:
        c: Invoke Context object for running commands.
        zip: Name of the output ZIP file (default: "jserv-album.zip").
    """
    resources = {
        f"bin/html-web-{html_jar_v}.jar": f"../../html-service/java/target/html-web-{html_jar_v}.jar", # clone at github/html-service
        f"bin/jserv-album-{version}.jar": f"target/jserv-album-{version}.jar",
        
        # https://exiftool.org/index.html
        "bin/exiftool.zip": "./task-res-exiftool-13.21_64.zip",

        "bin/synode_py3-0.7-py3-none-any.whl": f"../synode.py/dist/synode_py3-{version}-py3-none-any.whl",
        "WEB-INF": f"src/main/webapp/WEB-INF-0.7.3/*", # Do not replace with version.
        "winsrv": "../synode.py/winsrv/*",
        "web-dist": "web-dist/*"    # use a link for different Anclient folder name
                                    # ln -s ../Anclient/examples/example.js/album web-dist
                                    # mklink /D web-dist ..\anclient\examples\example.js\album
    }

    excludes = ["*.log", "report.html"]

    def matches_patterns(filename, patterns):
        """
        Check if a filename matches any of the given patterns.
        
        Args:
            filename (str): The filename to check (e.g., "data.logs").
            patterns (list): List of patterns (e.g., ["*.logs", "*.dat"]).
        
        Returns:
            bool: True if the filename matches any pattern, False otherwise.
        """
        return any(fnmatch.fnmatch(os.path.basename(filename), pattern) for pattern in patterns)

    try:

        print('-------------- resources ------------------')
        print(resources)

        err = False

        # Ensure the output directory for the ZIP exists
        output_dir = os.path.dirname(zip) or "."
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
        
        if os.path.isfile(zip):
            os.remove(zip)

        with zipfile.ZipFile(zip, 'w', zipfile.ZIP_DEFLATED) as zipf:
            # resources
            for rk, rv in resources.items():
                if "*" in rv:  
                    count = 0
                    srcroot = re.sub('\\*$', '', rv.replace('\\', '/'))
                    for pth, _dir, fs in os.walk(srcroot):
                        for file in fs:
                            if not matches_patterns(file, excludes):
                                file_path = os.path.join(pth, file)
                                relative_path = os.path.relpath(file_path, srcroot)
                                # print(file, file_path, pth, srcroot, relative_path)
                                
                                visited = set()
                                while os.path.islink(file_path):
                                     if file_path in visited:
                                          raise ValueError(f"Cycle detected in symbolic links at {relative_path}")
                                     visited.add(file_path)
                                     print(file_path, '->', os.path.realpath(file_path))
                                     file_path = os.path.realpath(file_path)

                                relative_path = os.path.relpath(relative_path)
                                arcname = os.path.join(rk, relative_path)
                                zipf.write(file_path, arcname)
                                count += 1
                                print(f"Added to ZIP: {relative_path} as {arcname}")
                    if count == 0:
                        err = True
                        raise FileNotFoundError(f'[ERROR] No files found in {rv}.')
                else:  # Handle single files (jserv.jar and exiftool.exe)
                    file = rk if rv == '.' else rv
                    if os.path.exists(file):
                        zipf.write(file, rk)
                        print(f"Added to ZIP: {file} as {rk}")
                    else:
                        err = True
                        raise FileNotFoundError(f"[ERROR]: Resource '{rk}': '{file}' not found.")

            for rk, files in vol_files.items():
                for file in files:
                    file = os.path.join(rk, file)
                    if os.path.exists(file):
                        zipf.write(file, file)
                        print(f"Added volume file to ZIP: {file} as {rk}")
                    else:
                        err = True
                        print(f"[ERROR]: volume file '{file}' not found, skipping.", file=sys.stderr)

        if not os.path.exists(dist_dir):
            os.makedirs(dist_dir, exist_ok=True)
        distzip = os.path.join(dist_dir, zip)
        if os.path.isfile(distzip):
            os.remove(distzip)
        os.rename(zip, distzip)

        print(f'Created ZIP file successfully: {distzip}' if not err else 'Errors while making target (creaded zip file)')

    except Exception as e:
        print(f"Error creating ZIP file: {str(e)}", file=sys.stderr)
        raise

@task(clean, create_volume, build, package)
def make(c):
    """
    Create a ZIP file (jserv.zip) with the specified resources.
    
    Args:
        c: Invoke Context object for running commands.
    """
    print('Package created successfully.')

    # ret = c.run(f'cp ../snodepy3/registry-zsu-{version}.zip {dist_dir}')
    # print('OK:', ret.ok, ret.stderr)


if __name__ == '__main__':
    from invoke import Program
    Program(namespace=globals()).run()
