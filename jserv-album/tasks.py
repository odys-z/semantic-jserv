import shutil
import sys
from types import LambdaType
from typing import cast
from pathlib import Path
from invoke import task, Context
import os

# PyInsertall uses what ever packages in the user's venv, not isolated one like the build module.
from anson.io.odysz.common import requir_pkg, requir_npm_package_resolve, mvn  # mvn: anson.py3 0.6.9+
_tasks = {a.replace('_', '-') for a in sys.argv[1:] if not a.startswith('-')}

# Tasks that don't need the build environment, e.g. github-head.
_independent = 'github-head' in _tasks

# install-py-local is the task that installs / upgrades these packages, so don't require them before it runs.
if 'install-py-local' not in _tasks:
    requir_pkg("anson.py3", "0.6.10")
    requir_pkg("semantics.py3", "0.6.15") # SynodeTask.github, gitprjs {github}, link-json

if 'install-py-local' not in _tasks and not _independent:
    requir_pkg("build")               # by synode.py
    requir_pkg("pyinstaller")         # by synode.py
    requir_pkg("jre-mirror", "0.1.2") # by synode.py
    requir_pkg("pillow", "10.0.0")    # by synode.py
    requir_pkg("qrcode")              # by synode.py
    requir_pkg("psutil")              # by synode.py
    requir_pkg("prompt-toolkit", "3.0.52")      # by synode.py
    requir_pkg("pyside6", ["6.6.0", "6.8.2.1"]) # by synode.py

# install-maven-local is the task that installs these jars, so don't require them before it runs.
if 'install-maven-local' not in _tasks and not _independent:
    mvn.requir_installed("io.github.odys-z:anclient.java", "[0.5.23,)")
    mvn.requir_installed("io.github.odys-z:semantic.jserv", "[1.5.18,)")
    mvn.requir_installed("io.github.odys-z:docsync.jserv", "[0.3.5,)")

from semanticshare.io.oz.invoke import SynodeTask
from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings

from anson.io.odysz.common import LangExt, Utils
from anson.io.odysz.utils import gzip2
from anson.io.odysz.anson import Anson
from semanticshare.io.oz.syntier.serv import ExternalHosts

version_pattern = '[0-9\\.]+'

# synode.json
re_market_id     = '\"market_id\"\\s*:\\s*\"[^"]*\"'
re_central_iport = '\"central_iport\"\\s*:\\s*\"[^"]*\"'
re_central_path  = '\"central_path\"\\s*:\\s*\"[^\"]*\"'

re_mirror_path_deprecated = lambda lang_id: '\"{lang}\"\\s*:\\s*{{\\s*\"jre_mirror\"\\s*:\\s*\"[^\"]*\"'.format(lang=lang_id) 
'''
"en": { "jre_mirror": "value to be replaced"}
ISSUE: regex is to be replaced with Anson's deserialize and serialize.
'''
re_mirror_path = lambda lang_id: '\"jre_mirror.{lang}.re\"\\s*:\\s*\"[^\"]*\"'.format(lang=lang_id) 

# settings.json
re_central_pswd  = '\"centralPswd\"\\s*:\\s*\"[^\"]*\"'
re_install_key   = '\"installkey\"\\s*:\\s*\"[^\"]*\"'
re_webport       = '\"webport\"\\s*:\\s*[0-9]+'
re_jserv_port    = '\"port\"\\s*:\\s*\\d+'

taskcfg = cast(SynodeTask, None)

@task
def check_env(c):
    # The active Python binary executing Invoke
    print(f"Python Executable : {sys.executable}")
    
    # Python version details
    print(f"Python Version    : {sys.version.split()[0]}")
    
    # Virtualenv / Environment base path
    print(f"Prefix / Venv Path: {sys.prefix}")

    print(f"SynodeTask Since Tag: {SynodeTask.since}")

    print("To have invoke run in the curent venv, use")
    print("python -m invoke build --deploy=tasks.pm-king.json")


@task
def validate(c: Context, deploy: str = 'tasks.0.8.0.json'):
    '''
    Validate central settings & set JAVA_HOME.
    '''
    print(f'--------------    validate   ------------------')
    global taskcfg
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file(deploy))

    print('taskcfg:', taskcfg.deploy.orgid, taskcfg.version)

    # was checked at module level, now paths are from taskcfg
    requir_npm_package_resolve(taskcfg.git_prj('album-web'), '@anclient/anreact', '0.7.1')
    requir_npm_package_resolve(taskcfg.git_prj('album-web'), '@anclient/semantier', '1.0.5')

    # ISSUE central-uid: Synodes log in central as deploy.admin (synusr.uid()), not centralUid.
    # 0.8.0: both must be 'admin'. See ../issues/central-uid-synode-login.md
    if not deploy in ['tasks.0.8.0.json', 'tasks.0.7.8.json', 'tasks.github.json'] and not (taskcfg.deploy.admin == taskcfg.deploy.centralUid == 'admin'):
        bar = '!' * 72
        Utils.warn(f'\n{bar}\n!!  deploy.admin ({taskcfg.deploy.admin}) and deploy.centralUid ({taskcfg.deploy.centralUid})'
                   f" must both be 'admin'.\n"
                   f'!!  Synodes log in central as deploy.admin with central_pswd.\n'
                   f'!!  See ../issues/central-uid-synode-login.md\n{bar}')
        sys.exit(-1)

    if hasattr(taskcfg, 'java_home') and not LangExt.isblank(taskcfg.java_home):
        java_home = taskcfg.java_home
        if java_home == 'JAVA_HOME' or java_home == '$JAVA_HOME' or java_home == '%JAVA_HOME%':
            java_home = os.environ.get('JAVA_HOME', '')
        else:
            java_home = os.path.expanduser(java_home)

        c.config['run']['env']['JAVA_HOME'] = java_home
        c.run('echo $JAVA_HOME')
    else:
        print("Using system environment varialbe JAVA_HOME ...")
        if os.name == 'nt':
            c.run('echo %JAVA_HOME% && echo $JAVA_HOME')
        else:
            c.run('echo $JAVA_HOME')


@task
def create_volume(c: Context):
    for vol, fs in taskcfg.vol_files.items():
        if not os.path.isdir(vol):
            os.mkdir(vol)
        for fn in fs: 
            with open(os.path.join(vol, fn), 'a', encoding='utf-8') as vf:
                print(f'Volume file created: {os.path.join(vol, fn)}')
                vf.close()


def updateApkRes():
    """
    Update the APK resource record (ref-link) in the host.json file.
    
    Args:
        host_json (str): Path to the host.json file.
        res (dict): Dictionary containing the APK resource information.
    """
    print(os.getcwd())
    print('Updating host.json with APK resource => taskcfg.host_json:', taskcfg.host_json)

    hosts = cast(ExternalHosts, Anson.from_file(taskcfg.host_json))
    hosts.marketid = taskcfg.deploy.market_id
    print(os.getcwd(), taskcfg.host_json)

    print('host.json market:', hosts.marketid)
    print('host.json:', hosts)

    res = {'apk': f'res-vol/portfolio-{taskcfg.apk_ver}.apk'}
    hosts.resources.update(res)
    print('Updated host.json/reources:', hosts.resources)

    if hasattr(taskcfg, 'download_root') and len(taskcfg.download_root) > 0:
        downloads = {f'{taskcfg.deploy.orgid}': [f'{taskcfg.download_root}/{taskcfg.zip_name()}']}
        hosts.synodesetups.update(downloads)
        print('Updated host.json/synodesetups:', hosts.synodesetups)
    else:
        print('*** WARN ***\n*')
        print('*** WARN ***: Setting resource downlaoding root path is skipped. taskcfg.download_root is empty.')
        print('*\n*** WARN ***')

    hosts.toFile(taskcfg.host_json)
    print('host.json updated successfully.', hosts)

    return None


@task
def config(c: Context, deploy: str = 'tasks.json'):
    validate(c, deploy)

    print(f'--------------    configuration   ------------------')
    print(f'-- synode version: {taskcfg.version} --'),

    version_file = 'pom.xml'
    Utils.update_patterns(version_file, {
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version_pattern}</version>':
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{taskcfg.version}</version>',
    })

    # apk
    version_file = taskcfg.git_prj('album-android', 'build.gradle')
    Utils.update_patterns(version_file, {
        f"app_ver = '{version_pattern}'": f"app_ver = '{taskcfg.apk_ver}'"
    })

    synode_settings: AppSettings = cast(AppSettings, Anson.from_file(
        Path(taskcfg.web_inf_dir) / 'settings.github.json'))
    synode_settings.regiserv = f'http://{taskcfg.deploy.central_iport}/{taskcfg.deploy.central_path}'
    synode_settings.jservs = {}
    # In 0.8.0, market_id is also configured in settings.json for client Apps.
    synode_settings.market_id = taskcfg.deploy.market_id
    synode_settings.market_name = taskcfg.deploy.market
    synode_settings.jserv_utc = '1911-10-10'
    synode_settings.centralPswd = taskcfg.deploy.central_pswd
    synode_settings.webport = taskcfg.deploy.web_port
    synode_settings.port = taskcfg.deploy.jserv_port
    synode_settings.rootkey = ''
    synode_settings.installkey = taskcfg.deploy.root_key
    synode_settings.toFile(Path(taskcfg.web_inf_dir) / 'settings.json')

    # ipc-agent.jar
    version_file = 'pom.xml'
    Utils.update_patterns(version_file, {
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version_pattern}</version>':
            f'<!-- auto update token TASKS.PY/CONFIG --><version>{taskcfg.version}</version>',
    })


@task
def clean(c: Context):
    if not os.path.exists(taskcfg.package_dir):
        os.makedirs(taskcfg.package_dir, exist_ok=True)

    for item in os.listdir(taskcfg.package_dir):
        item_path = os.path.join(taskcfg.package_dir, item)
        print('cleaning', item_path, taskcfg.zip_name())
        if item_path == taskcfg.zip_name():
            if os.path.isfile(item_path):
                os.unlink(item_path)
            elif os.path.isdir(item_path):
                shutil.rmtree(item_path)


@task
def install_maven_local(c: Context, deploy: str='tasks.0.8.0.json', gpg: str = None):
    '''
    Install jserv-album's depending jars locally.

    [INFO] --------------------< io.github.odys-z:jserv-album >--------------------
    [INFO] io.github.odys-z:jserv-album:jar:0.8.0
    [INFO] +- io.github.odys-z:docsync.jserv:jar:0.3.3:compile
    [INFO] |  +- io.github.odys-z:semantic.DA:jar:1.5.24:compile
    [INFO] |  |  +- io.github.odys-z:semantics.transact:jar:1.5.77:compile
    [INFO] |  |  |  |- io.github.odys-z:antson:jar:1.0.8:compile
    [INFO] |  |- io.github.odys-z:synodict.jclient:jar:0.1.8:compile
    [INFO] +- io.github.odys-z:syndoc-lib:jar:0.5.20:compile
    [INFO] |  |- io.github.odys-z:semantic.jserv:jar:1.5.17:compile
    [INFO] +- io.github.odys-z:albumtier:jar:0.5.4:test              - For Android
    [INFO] +- io.github.odys-z:anclient.java:jar:0.5.20:compile
    [INFO] |- io.github.odys-z:synodict.central:jar:0.1.8:test       X

    Also install html-service
    :param c:
    :param gpg: gpg-passphrase
    :return: None
    '''

    if LangExt.isblank(gpg):
        Utils.warn("gpg-passphrase is blank!")
        sys.exit(-1)
    
    validate(c, deploy=deploy)

    git = taskcfg.git_prj
    pom_locations = [
        git('antson', 'antson.java'),
        git('semantic-transact'),
        git('semantic-DA'),
        git('semantic-jserv'),
        git('jserv-album-lib'),
        git('anclient.jserv'),
        git('registry-jclient'),
        git('registry-central'),
        git('docsync.jserv'),
        git('album-android', 'albumtier'),

        git('html-service')
    ]

    print('----------  Install Local Maven ---------')
    for pth in pom_locations:
        mvn = f'mvn clean compile package install -Dgpg.passphrase={gpg} -DskipTests'
        print('****************************************************************************')
        print('*', pth, ":", mvn)
        print('****************************************************************************')
        ret = c.run(f'cd {pth} && {mvn}')
        print('OK:', ret.ok, ret.stderr)

    c.run('mvn clean dependency:tree | grep io.github.odys-z')


@task
def install_py_local(c: Context, venv_build: str = None, deploy: str = 'tasks.0.8.0.json'):
    '''
    Install python packages locally in the target venv.

    To make sure everything is re-built locally,

    :: bash
        inv install-py-local --venv-build=.venv391
    ..

    To install the latest wheel in dist/ without re-building, ignore the venv_build parameter:
    
    :: bash
        inv install-py-local
    ..

    :param c: Context object
    :param venv_build: optional venv path for building wheel packages (e.g., ".venv391").
                        If None (default), skipping build and directly installing the latest wheel in dist/
    :param deploy: task json, for the source projects' paths (github, gitprjs)
    '''
    # Debug Note: semantics.py3 (SynodeTask) is one of the packages upgraded here, and the installed
    # one may have no SynodeTask.github / gitprjs / resolve_gitprjs() yet. Read the 2 fields with json,
    # not Anson, and resolve "link-json" here, the same as semanticshare.io.oz.invoke.resolve_gitprjs().
    import json

    def load_gitprjs(jsonpath: str, linking: tuple = ()) -> tuple:
        jsonpath = os.path.abspath(jsonpath)
        if jsonpath in linking:
            Utils.warn('Circular gitprjs[link-json]: {}', ' -> '.join(linking + (jsonpath,)))
            sys.exit(-1)
        with open(jsonpath, 'r', encoding='utf-8') as jf:
            js = json.load(jf)
        prjs = js.get('gitprjs', {})
        resolved = {}
        if 'link-json' in prjs:
            resolved.update(load_gitprjs(os.path.join(os.path.dirname(jsonpath), prjs['link-json']),
                                         linking + (jsonpath,))[1])
        resolved.update({k: v for k, v in prjs.items() if k != 'link-json' and not k.lstrip().startswith('//')})
        return js.get('github', '../..'), resolved

    github, gitprjs = load_gitprjs(deploy)

    def git(prj: str, *subpaths: str) -> str:
        if prj not in gitprjs:
            Utils.warn('Source project "{}" is not configured in {}/gitprjs: {}', prj, deploy, gitprjs)
            sys.exit(-1)
        return os.path.join(gitprjs[prj].replace('{github}', github), *subpaths)

    import subprocess

    def get_venv_python(venv_name: str) -> str:
        """Returns absolute path to python executable inside target venv (cross-platform)."""
        venv_path = Path(venv_name).resolve()
        python_bin = venv_path / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        return str(python_bin) if python_bin.exists() else sys.executable

    py_exec = get_venv_python(venv_build) if venv_build else sys.executable
    orig_cwd = Path.cwd()

    def run_cmd(cmd: list[str], check: bool = True) -> None:
        print(f"==> Running: {' '.join(cmd)}")
        subprocess.run(cmd, check=check)

    printings = []

    def install_pkg(dst_pth: Path, pkg_name: str) -> None:
        abs_dst = dst_pth.resolve()
        try:
            os.chdir(abs_dst)
            print(f"\nWorking directory: {abs_dst}")

            if venv_build is not None:
                # 1. Clean old build artifacts
                run_cmd([py_exec, "-c",
                        "import shutil, glob; [shutil.rmtree(p, ignore_errors=True) for p in ['dist', 'build'] + glob.glob('*.egg-info')]"
                ])

                # 2. Build the wheel
                run_cmd([py_exec, "-m", "build"])

            # 3. Uninstall previous version
            run_cmd([py_exec, "-m", "pip", "uninstall", "-y", pkg_name], check=False)

            # 4. Locate newest wheel file
            wheels = sorted(Path("dist").glob("*.whl"), key=os.path.getmtime, reverse=True)
            if not wheels:
                print(f"Error: No wheel file found in {abs_dst / 'dist'}", file=sys.stderr)
                sys.exit(1)

            latest_wheel = wheels[0]

            # 5. Install newest wheel
            run_cmd([py_exec, "-m", "pip", "install", str(latest_wheel)])
            # print(f"==> Installed: {latest_wheel}")
            printings.append(f"==> Installed: {latest_wheel}")

        finally:
            os.chdir(orig_cwd)

    packages = [
        (Path(git('antson', 'py3')), "anson.py3"),
        (Path(git('antson', 'semantics.py3')), "semantics.py3"),
        (Path(git('JRE-Mirror')), "jre-mirror"),
        (Path(git('anclient.py3')), "anclient.py3"),
    ]

    print('----------  Install Local Python Packages  ---------')
    for p, n in packages:
        install_pkg(p, n)

    for p in printings:
        print(p)


@task
def build(c: Context, deploy: str = 'tasks.json'):
    '''
    Build with build commands.
    - desktop app
    invoke shallo-pack, replace att-setings.json with invoke pack-settings, wsport = ...
    :param c: context
    '''
    global taskcfg

    if not os.path.exists(deploy):
        Utils.warn(f"[ERROR] Configure file for deploying doesn't exist: {deploy}")
        return

    config(c, deploy)

    absdeploy = Path(deploy).absolute()
    web_root = taskcfg.git_prj('album-web')
    web_dist = Path(web_root) / 'web-dist'
    desktop = taskcfg.git_prj('album-desktop')
    wsagent = taskcfg.git_prj('album-wsagent')
    synode_py = taskcfg.git_prj('synode.py')

    def cmd_build_synodepy3() -> str:
        """
        Get the command to build the synode.py3 package.
        input:
            web_ver: for web srv id
        Returns:
            str: The command to build the package.
        """
        print(f'Building synode.py3 {taskcfg.version}, web-dist {taskcfg.web_ver}, html-service.jar {taskcfg.html_jar_v}...')
        cmd = f"invoke build --deploy={absdeploy}"
        return cmd

    def cmd_cp_wsagent_jar() -> None:
        def src_wsagent_jar() -> str:
            '''
            Get ws-agent/target/ws-agent-#.#.#.jar fullpath.
            '''
            global taskcfg
            return os.path.join(wsagent, 'target', f'ws-agent-{taskcfg.ipcagent_ver}.jar')

        def desk_dist_res_dir() -> str:
            global taskcfg
            return os.path.join(desktop, taskcfg.desktop_dist_dir, 'res')

        def desk_res_dir() -> str:
            global taskcfg
            return os.path.join(desktop, 'tests', 'res')

        print(src_wsagent_jar(), "=>", desk_res_dir())
        shutil.copy(src_wsagent_jar(), desk_res_dir())
        print(src_wsagent_jar(), "=>", desk_dist_res_dir())
        shutil.copy(src_wsagent_jar(), desk_dist_res_dir())

    buildcmds = [
        # desktop
        # - desktop.ipc-agent
        [wsagent, 'mvn clean compile package -DskipTests'],
        # - desktop.ext, app-settings.json -> dist; create the desktop setting here is necessary for standalone clients
        [desktop, f'invoke shallow-pack --deploy={absdeploy}'],
        ['.', cmd_cp_wsagent_jar], # issue: gitprjs['album-wsagent'] cannot be undstand by slint/tasks.py

        # apk
        ['.', f'rm -f web-dist/res-vol/portfolio-*.apk'],
        # JAVA_HOME is set in validate()
        [taskcfg.git_prj('album-android'), 'gradlew.bat assembleRelease' if os.name == 'nt' else './gradlew assembleRelease'],

        ['.', f'cp -f {taskcfg.get_gradleprj_apk()} {web_dist}/res-vol/{taskcfg.get_apk_name()}' \
                if os.name == 'nt' else f'touch {web_dist}/res-vol/portfolio-{taskcfg.apk_ver}.apk' ], # TODO build apk in Linux...

        [f'{web_dist}', 'rm -f login*.min.js* portfolio*.min.js* report.html'],
        [web_root, 'webpack'],

        [web_dist, updateApkRes],
        ['.', f'cat {web_dist}/private/host.json'],

        #
        ['.', 'mvn clean compile package -DskipTests'],
        [taskcfg.git_prj('html-service'), 'mvn clean compile package'],

        [synode_py, cmd_build_synodepy3],
    ]

    print('--------------  build  ------------------')
    for pth, cmd in buildcmds:
        print('****************************************************************************')
        if isinstance(cmd, LambdaType):
            cwd = os.getcwd()
            os.chdir(pth)
            cmd = cmd()
            print('*', pth, '&&', cmd)
            if cmd is not None:
                print(pth, '&&', cmd)
                ret = c.run(f'cd {pth} && {cmd}')
            os.chdir(cwd)
        else:
            print('*', pth, '&&', cmd)
            ret = c.run(f'cd {pth} && {cmd}')
            print('OK:', ret.ok, ret.stderr)
    print('****************************************************************************')
    return False


def pth_packagedir(taskconfig: SynodeTask = None) -> Path:
    global taskcfg
    if taskconfig is None:
        taskconfig = taskcfg

    if taskconfig is None:
        warn("No task configure can be found")
        sys.exit(-1)

    return Path(taskconfig.package_dir) / taskconfig.zip_name()


@task
def package(c: Context, deploy: str = 'tasks.json'):
    """
    Create a ZIP file.
    
    Args:
        c: Invoke Context object for running commands.
        zip: Name of the output ZIP file.
    """
    global  taskcfg
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file(deploy))

    jre_img = taskcfg.jre_release.split('/')[-1]
    temp_jre_path = f'jre17-temp/{jre_img}'

    zip = taskcfg.zip_name()

    html_target = taskcfg.git_prj('html-service', 'target')
    synode_py = taskcfg.git_prj('synode.py')
    desktop = taskcfg.git_prj('album-desktop')

    def gen_readme() -> str:
        """
        Generate the package's README.md from synodepy3/commands_help.py, the same source of synode-cli --help.
        :return: path of the generated README.md, in a temporary folder
        """
        import importlib.util, tempfile
        spec = importlib.util.spec_from_file_location('commands_help', os.path.join(synode_py, 'src', 'synodepy3', 'commands_help.py'))
        commands_help = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(commands_help)
        md = os.path.join(tempfile.mkdtemp(prefix='synode-readme-'), 'README.md')
        with open(md, 'w', encoding='utf-8') as fo:
            fo.write(commands_help.release_readme())
        return md

    readme_md = gen_readme()

    resources = {
        f'bin/html-web-{taskcfg.html_jar_v}.jar': f'{html_target}/html-web-{taskcfg.html_jar_v}.jar', # clone at github/html-service
        f'bin/jserv-album-{taskcfg.version}.jar': f'target/jserv-album-{taskcfg.version}.jar',

        'WEB-INF': f'{taskcfg.web_inf_dir}/*',

        'bin/synode_py3-0.8-py3-none-any.whl': f'{synode_py}/dist/synode_py3-{taskcfg.version}-py3-none-any.whl',
        "registry": f"{synode_py}/registry/*",
        'winsrv': f'{synode_py}/winsrv/*',
        "res": f"{synode_py}/src/synodepy3/res/*",

        'web-dist': f'{taskcfg.git_prj("album-web")}/web-dist/*',

        'README.md': readme_md,
    }

    if os.name == 'nt': resources.update({
        'bin/exiftool.zip': './task-res-exiftool-13.21_64.zip', # https://exiftool.org/index.html
        temp_jre_path: taskcfg.check_local_resource(taskcfg.jre_release),
        'desktop': f'{os.path.join(desktop, taskcfg.desktop_dist_dir, "*")}',
        'setup-gui.exe': f'{synode_py}/dist/setup-gui.exe',
        'setup-cli.exe': f'{synode_py}/dist/setup-cli.exe',
        'uninstall-srv.exe': f'{synode_py}/dist/uninstall-srv.exe'
        # 'upgrade.exe': f'{synode_py}/dist/upgrade.exe'
    })
    else:
        print("[*** TODO *** 0.8.0 POSIX]  desktop [album-gui, ws-agent.jar, settings], requires exiftool, jre-posix")

    excludes = ['*.log', 'report.html', '*.github.json', '.gitignore']

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

        gzip2(zip, {**resources, **taskcfg.vol_resource}, excludes)
        shutil.rmtree(os.path.dirname(readme_md), ignore_errors=True)

        zip = Utils.move_anyway(zip, pth_packagedir(taskcfg), log=True)

        print('****************************************************************************************************',
             f'* Distribution ZIP file is created successfully: {zip}' if not err else 'Errors while making target (creaded zip file)',
              sep='\n')

        # Also build desktop standalone
        print('****************************************************************************************************')
        if os.name == 'nt': # not POSIX 0.8.0
            c.run(f"cd {desktop} && invoke zip-standalone --deploy={Path(deploy).absolute()}")
            Utils.copy_anyway(taskcfg.get_deskapp_zip(), taskcfg.package_dir, log=True)
        else:
            print("[*** TODO *** 0.8.0]  skip building & packaging desktop-posix")

        Utils.copy_anyway(taskcfg.get_gradleprj_apk(), Path(taskcfg.package_dir) / taskcfg.get_apk_name(), log=True)
        print('****************************************************************************************************')

    except Exception as e:
        print(f"Error creating ZIP file: {str(e)}", file=sys.stderr)
        raise


@task
def run_scps(c: Context, deploy:str = 'task.json'):
    '''
    Run taskcfg.deploy_cmds and taskcfg.deploy_scps.
    :param c:
    :param deploy: default is 'task.json', where the scp commands are configured.
    '''
    print('--------------   run-scps  ------------------')
    global taskcfg
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file(deploy))

    if taskcfg.deploy_scps:
        requir_pkg('paramiko')
        requir_pkg('scp')

    ok, err = taskcfg.run_deploycmds(c)
    print(f"Run deploy_cmds, ok: {ok}, error: {err}")

    taskcfg.run_deployscps(str(taskcfg.get_distzip()))
    taskcfg.run_deployscps(str(Path(taskcfg.package_dir) / taskcfg.get_apk_name()))

    if os.name == 'nt': # not posix 0.8.0
        taskcfg.run_deployscps(str(Path(taskcfg.git_prj('album-desktop')) / taskcfg.package_dir / taskcfg.deskzip_name()))

    print('', sep='\n')
    print(f"Run deploy_cmds, 3 package copyied.")


@task
def make(c: Context, deploy: str = 'tasks.json', gpg: str = None):
    '''
    call build & package (no post-scp of deploy).
    This task is for separating python 3.9 for build & packaging;
    and from python 3.10 (3.9.1?) and above for scp command in cfg.deploy_scps.
    '''
    if gpg is not None:
        install_maven_local(c, gpg=gpg)

    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))
    clean(c)
    create_volume(c)
    build(c, deploy=deploy)
    package(c, deploy=deploy)


@task
def deploy(c: Context, deploy: str = 'tasks.json', gpg: str = None):
    make(c, deploy=deploy, gpg=gpg)
    run_scps(c, deploy=deploy)
    print(f'Deployed: {deploy}, central task: {taskcfg.git_prj("registry-central")} ...')


@task
def github_head(c: Context, deploy: str = 'tasks.0.8.0.json'):
    '''
    Report all source projects' (gitprjs) git head commit id and branch name,
    and update {github}/source.tree, the git-tracked files of all repositories in github.
    Independent of the build environment (no validate, JAVA_HOME, maven or npm checks).

    :: bash
        inv github-head --deploy=tasks.pm-king.json
    ..

    :param c: Context
    :param deploy: task json, where github & gitprjs are configured
    '''
    import subprocess

    # not listed in source.tree: a repository folder, or a path relative to github
    ignores = ['vcpkg', 'odys-z.github.io', 'Ever-connecting/connects/docs', 'semantics-jserv/docs']

    cfg = cast(SynodeTask, Anson.from_file(deploy))
    print(f'--------------   github heads: {Path(cfg.github).resolve()}   ------------------')

    def git(pth: str, *args: str) -> str:
        r = subprocess.run(['git', '-C', pth, *args], capture_output=True, text=True)
        return r.stdout.strip() if r.returncode == 0 else None

    # projects in the same repository are reported once, e.g. synode.py & semantic-jserv
    repos, errs = {}, []
    for prj in cfg.prjs():
        pth = cfg.git_prj(prj)
        if not os.path.isdir(pth):
            errs.append((prj, '[not found]', pth))
            continue
        top = git(pth, 'rev-parse', '--show-toplevel')
        if top is None:
            errs.append((prj, '[not a git repo]', pth))
            continue
        if top not in repos:
            branch = git(top, 'rev-parse', '--abbrev-ref', 'HEAD')
            repos[top] = {'branch': '(detached)' if branch == 'HEAD' else branch,
                          'commit': git(top, 'rev-parse', 'HEAD'), 'prjs': []}
        repos[top]['prjs'].append(prj)

    rows = [(os.path.basename(top), r['branch'], r['commit'], ', '.join(r['prjs'])) for top, r in repos.items()]
    w0 = max([len('repository')] + [len(r[0]) for r in rows])
    w1 = max([len('branch')] + [len(r[1]) for r in rows])
    print(f'{"repository":<{w0}}  {"branch":<{w1}}  {"commit":<40}  projects')
    for repo, branch, commit, prjs in rows:
        print(f'{repo:<{w0}}  {branch:<{w1}}  {commit:<40}  {prjs}')

    for prj, err, pth in errs:
        print(f'{err} {prj}: {pth}')

    write_source_tree(cfg.github, ignores)


def write_source_tree(github: str, ignores: list) -> Path:
    '''
    Write {github}/source.tree, git-tracked files of every repository in github, except ignores.
    An ignore without '/' is a repository folder, e.g. 'vcpkg'; with '/', a folder or file path
    relative to github, e.g. 'Ever-connecting/connects/docs'.
    Format (also in the file's header):
        # <repo>  <branch>  <commit>     starts a repository
        <dir>/                           a folder relative to the repository, './' for the root
          <file>                         files in the folder above
    :return: path of source.tree
    '''
    import subprocess
    from datetime import date

    def git(pth: Path, *args: str) -> str:
        r = subprocess.run(['git', '-C', str(pth), *args], capture_output=True)
        return r.stdout.decode('utf-8', errors='replace') if r.returncode == 0 else None

    root = Path(github)
    lines = [f'# github sources, {date.today().isoformat()}. Git-tracked files only.',
             "# Format: '# <repo> <branch> <commit>' starts a repo; '<dir>/' is a folder (relative to the repo,",
             "#         './' = repo root); the indented names below it are that folder's files."]

    prefixes = tuple(ig.strip('/') + '/' for ig in ignores if '/' in ig.strip('/'))

    def ignored(repo: str, f: str) -> bool:
        rel = f'{repo}/{f}'
        return rel.startswith(prefixes) or (rel + '/').startswith(prefixes)

    for d in sorted(p for p in root.iterdir() if p.is_dir() and p.name not in ignores):
        files = git(d, 'ls-files', '-z')
        if files is None:
            continue    # not a git repository
        branch = git(d, 'rev-parse', '--abbrev-ref', 'HEAD').strip()
        commit = git(d, 'rev-parse', '--short', 'HEAD').strip()
        lines += ['', f'# {d.name}  {branch}  {commit}']

        last = None
        for folder, name in sorted((f.rpartition('/')[0] + '/' if '/' in f else './', f.rpartition('/')[2])
                                   for f in files.split('\0') if f and not ignored(d.name, f)):
            if folder != last:
                lines.append(folder)
                last = folder
            lines.append(f'  {name}')

    tree = root / 'source.tree'
    with open(tree, 'w', encoding='utf-8', newline='\n') as fo:
        fo.write('\n'.join(lines) + '\n')
    print(f'Source tree updated: {tree.resolve()}, ignored: {ignores}')
    return tree


@task
def pause(c: Context):
    input('Press Enter to continue...')


@task(post=[config, pause, run_scps])
def config_post(c: Context, deploy: str = 'tasks.json'):
    print(f'Testing : {deploy}')
    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))


@task(post=[clean])
def test_clean(c: Context, deploy: str = 'tasks.json'):
    print(f'Testing : {deploy}')
    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))


@task
def help(c: Context):
    """
    Print a succinct RST-style usage memo for every task in this file.
    
    :param c: Invoke context.
    :return: None
    """
    import inspect
    from invoke import Collection

    ns = Collection.from_module(sys.modules[__name__])

    for name in sorted(ns.tasks):
        fn = ns.tasks[name].body
        sig = inspect.signature(fn)
        params = [
            pname if p.default is inspect.Parameter.empty else f'{pname}={p.default!r}'
            for pname, p in sig.parameters.items() if pname != 'c'
        ]
        argstr = ', '.join(params)

        title = f'{name}({argstr})'
        print(title)
        print('=' * len(title))

        doc = inspect.getdoc(fn)
        print(doc if doc else '    (undocumented)')
        print()


if __name__ == '__main__':
    from invoke import Program
    Program(namespace=globals()).run()
