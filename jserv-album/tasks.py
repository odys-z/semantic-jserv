"""
invoke make
"""
import fnmatch
import re
import sys
from types import LambdaType
from invoke import task
import zipfile
import os
from glob import glob

from anson.io.odysz.anson import Anson

version = '0.7.2'
"""
synode.py3, jserv-album-0.7.2.jar
"""

html_jar_v = '0.1.5'
"""
html-web-#.#.#.jar
"""

web_ver = '0.4.1'
"""
album-web-#.#.#.jar
"""

vol_files = {"volume": ["jserv-main.db", "doc-jserv.db"]}
dist_dir = 'deploy-x29'

@task
def create_volume(c):
    for vol, fs in vol_files.items():
        if not os.path.isdir(vol):
            os.mkdir(vol)
        for fn in fs: 
            with open(os.path.join(vol, fn), 'a') as vf:
                print(f'Volume file created: {os.path.join(vol, fn)}')
                vf.close()

def updateApkRes(host_json, res):
    """
    Update the APK resource in the host.json file.
    
    Args:
        host_json (str): Path to the host.json file.
        res (dict): Dictionary containing the APK resource information.
    """
    print('Updating host.json with APK resource...', host_json)

    import src.synodepy3 # type: ignore
    Anson.java_src('src')
    hosts = Anson.from_file(host_json)
    print('host.json:', hosts)

    hosts.resources.update(res)
    print('Updated host.json:', hosts.resources)

    hosts.toFile(host_json)
    print('host.json updated successfully.', hosts)

    return None

def updateJarVersion(file, pattern, repl):
    """
    Update the version in a JAR file.
    
    Args:
        file (str): Path to the JAR file.
        pattern (str): Regular expression pattern to match the version string.
        repl (str): Replacement string for the version.
    """
    print('Updating JAR version...', file)

    lines = []
    with open(file, 'r') as f:
        lines = f.readlines()

    # updated_content = re.sub(pattern, repl, content)
    for i, line in enumerate(lines):
        if re.search(pattern, line):
            lines[i] = re.sub(pattern, repl, line)
            print('Updated line:', lines[i])
            break

    with open(file, 'w') as f:
        f.writelines(lines)

    print('JAR version updated successfully.', file)

    return None

@task
def build(c):
    buildcmds = [
        ['../../anclient/examples/example.android', 'gradlew assembleRelease'],
        ['.', f'cp -f ../../anclient/examples/example.android/app/build/outputs/apk/release/app-release.apk web-dist/res-vol/portfolio-{version}.apk'],
        ['web-dist/private', lambda: updateApkRes('host.json', {'apk': f'res-vol/portfolio-{version}.apk'})],
        ['.', 'cat web-dist/private/host.json'],

        ['../../anclient/examples/example.js/album', 'webpack'],
        ['.', 'mvn clean compile package -DskipTests'],
        ['../../html-service/java', 'mvn clean compile package'],
        ['../synode.py', lambda: f'set SYNODE_VERSION={version} && rm -rf dist && py -m build' if os.name == 'nt' else f'export SYNODE_VERSION={version} && rm -rf dist && python3 -m build'],

        ['../synode.py/winsrv', lambda: updateJarVersion('install-html-w.bat', '@set jar-ver=.*', f'@set jar-ver={html_jar_v}')],
        ['../synode.py/winsrv', lambda: updateJarVersion('install-jserv-w.bat', '@set jar-ver=.*', f'@set jar-ver={version}')],
    ]

    print('--------------    buid   ------------------')
    for pth, cmd in buildcmds:
        # try:
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
        # except Exception as e:
        #     print('Error:', e, file=sys.stderr)
        #     return True
    return False


@task(create_volume, build)
def make(c, zip=f'jserv-portfolio-{version}.zip'):
    """
    Create a ZIP file (jserv.zip).
    
    Args:
        c: Invoke Context object for running commands.
        zip: Name of the output ZIP file (default: "jserv-album.zip").
    """
    resources = {
        f"bin/html-web-{html_jar_v}.jar": f"../../html-service/java/target/html-web-{html_jar_v}.jar", # clone at github/html-service
        f"bin/jserv-album-{version}.jar": f"target/jserv-album-{version}.jar",
        "bin/exiftool.zip": "./exiftool-13.21_64.zip",
        "bin/synode_py3-0.7-py3-none-any.whl": f"../synode.py/dist/synode_py3-{version}-py3-none-any.whl",
        "WEB-INF": f"src/main/webapp/WEB-INF-{version}/*",
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

if __name__ == '__main__':
    from invoke import Program
    Program(namespace=globals()).run()
