"""
invoke make
"""
import shutil
import sys
from types import LambdaType
from typing import cast
from pathlib import Path
from invoke import task
import os

from anson.io.odysz.common import requir_pkg
requir_pkg("anson.py3", "0.6.3")
requir_pkg("semantics.py3", "0.6.1")

from semanticshare.io.oz.invoke import SynodeTask, CentralTask
from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings

from anson.io.odysz.common import LangExt, Utils
from anson.io.odysz.utils import zip2
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
def validate(c, deploy: str = 'tasks.0.8.0.json'):
    print(f'--------------    validate   ------------------')
    global taskcfg
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file(deploy))

    print('taskcfg:', taskcfg.deploy.orgid, taskcfg.version)

    task_cent = cast(CentralTask, Anson.from_file(os.path.join(taskcfg.central_dir, 'tasks.json')))

    if taskcfg.deploy.central_pswd != task_cent.users['admin']['pswd']: # Issue: should be ['admin'].pswd:
        Utils.warn(f'Warning: central_pswd is not set to default value. Override with {taskcfg.deploy.central_pswd}')
        # sys.exit(1)
    else:
        Utils.logi('Central pswd looks fine.')

    java_home = os.path.expanduser(taskcfg.java_home)
    Utils.logi('configure JAVA_HOME: {}', java_home)

    if not LangExt.isblank(taskcfg.java_home):
        c.config['run']['env']['JAVA_HOME'] = java_home
        c.run('echo $JAVA_HOME')
    else:
        if os.name == 'nt':
            c.run('echo %JAVA_HOME% && echo $JAVA_HOME')
        else:
            c.run('echo $JAVA_HOME')


@task
def create_volume(c):
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

    downloads = {f'{taskcfg.deploy.orgid}': [f'{taskcfg.download_root}/{taskcfg.zip_name()}']}
    hosts.synodesetups.update(downloads)
    print('Updated host.json/synodesetups:', hosts.synodesetups)

    hosts.toFile(taskcfg.host_json)
    print('host.json updated successfully.', hosts)

    return None


@task
def config(c, deploy: str = 'tasks.json'):
    validate(c, deploy)

    print(f'--------------    configuration   ------------------')
    print(f'-- synode version: {taskcfg.version} --'),

    version_file = 'pom.xml'
    Utils.update_patterns(version_file, {
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version_pattern}</version>':
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{taskcfg.version}</version>',
    })

    # apk
    version_file = os.path.join(taskcfg.android_dir, 'build.gradle')
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

    # Desktop 0.1.2
    # desk_sets = cast(DesktopSettings, Anson.from_file(Path(taskcfg.desktop_dir) / 'app/settings/app-settings.github.json'))
    # desk_sets.market = taskcfg.deploy.market_id
    # desk_sets.centralPswd = taskcfg.deploy.central_pswd
    # desk_sets.toFile(Path(taskcfg.desktop_dir) / 'app/settings/app-settings.json')


@task
def clean(c):
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
def install_maven_local(c, deploy: str='tasks.0.8.0.json', gpg: str = None):
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

    pom_locations = [
        '../../antson/antson.java',
        '../../semantic-transact/semantic.transact',
        '../../semantic-DA/semantic.DA',
        '../../semantic-jserv/jserv-album-lib',
        '../../anclient/java/eclipse-workspace/anclient.jserv',
        '../../Semantic-Network/registration/jclient',
        '../../Semantic-Network/registration/jserv',
        '../../semantic-jserv/docsync.jserv',
        '../../anclient/examples/example.android/albumtier',

        '../../html-service/java'
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
def build(c, deploy: str = 'tasks.json'):
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
    web_dist = Path(taskcfg.web_root_dir) / 'web-dist'

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
            return os.path.join(taskcfg.ipcagent_dir, 'target', f'ws-agent-{taskcfg.ipcagent_ver}.jar')

        def desk_dist_res_dir() -> str:
            global taskcfg
            return os.path.join(taskcfg.desktop_dir, taskcfg.desktop_dist_dir, 'res')

        def desk_res_dir() -> str:
            global taskcfg
            return os.path.join(taskcfg.desktop_dir, 'tests', 'res')

        print(src_wsagent_jar(), "=>", desk_res_dir())
        shutil.copy(src_wsagent_jar(), desk_res_dir())
        print(src_wsagent_jar(), "=>", desk_dist_res_dir())
        shutil.copy(src_wsagent_jar(), desk_dist_res_dir())

    buildcmds = [
        # desktop
        # - desktop.ipc-agent
        [taskcfg.ipcagent_dir, 'mvn clean compile package -DskipTests'],
        # - desktop.ext, app-settings.json -> dist; create the desktop setting here is necessary for standalone clients
        [taskcfg.desktop_dir, f'invoke shallow-pack --deploy={absdeploy}'],
        ['.', cmd_cp_wsagent_jar], # issue: taskcfg.ipcagent_dir cannot be undstand by slint/tasks.py

        # apk
        ['.', f'rm -f web-dist/res-vol/portfolio-*.apk'],
        # [taskcfg.android_dir, 'gradlew assembleRelease' if os.name == 'nt' else 'echo Android APK building skipped.'],
        [taskcfg.android_dir, f'{"" if LangExt.isblank(taskcfg.java_home) else "export JAVA_HOME=" + taskcfg.java_home} && ./gradlew assembleRelease'],

        # ['.', f'cp -f {taskcfg.android_dir}/app/build/outputs/apk/release/app-release.apk web-dist/res-vol/portfolio-{taskcfg.apk_ver}.apk' \
        ['.', f'cp -f {taskcfg.get_gradleprj_apk()} {web_dist}/res-vol/{taskcfg.get_apk_name()}' \
                if os.name == 'nt' else f'touch {web_dist}/res-vol/portfolio-{taskcfg.apk_ver}.apk' ], # TODO build apk in Linux...

        [f'{web_dist}', 'rm -f login*.min.js* portfolio*.min.js* report.html'],
        # ['../../anclient/examples/example.js/album', 'webpack'],
        [taskcfg.web_root_dir, 'webpack'],

        # ['web-dist/res-vol', lambda: updateApkRes()],
        [web_dist, updateApkRes],
        ['.', f'cat {web_dist}/private/host.json'],

        #
        ['.', 'mvn clean compile package -DskipTests'],
        ['../../html-service/java', 'mvn clean compile package'],

        ['../synode.py', cmd_build_synodepy3],
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
def package(c, deploy: str = 'tasks.json'):
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

    resources = {
        f'bin/html-web-{taskcfg.html_jar_v}.jar': f'../../html-service/java/target/html-web-{taskcfg.html_jar_v}.jar', # clone at github/html-service
        f'bin/jserv-album-{taskcfg.version}.jar': f'target/jserv-album-{taskcfg.version}.jar',

        # https://exiftool.org/index.html
        'bin/exiftool.zip': './task-res-exiftool-13.21_64.zip',
        
        temp_jre_path: taskcfg.jre_release,

        'WEB-INF': f'{taskcfg.web_inf_dir}/*',

        'bin/synode_py3-0.8-py3-none-any.whl': f'../synode.py/dist/synode_py3-{taskcfg.version}-py3-none-any.whl',
        "registry": "../synode.py/registry/*",
        'winsrv': '../synode.py/winsrv/*',
        "res": "../synode.py/src/synodepy3/res/*",

        'web-dist': 'web-dist/*',   # use a link for different Anclient folder name
                                    # ln -s ../Anclient/examples/example.js/album web-dist
                                    # mklink /D web-dist ..\anclient\examples\example.js\album

        'desktop': f'{os.path.join(taskcfg.desktop_dir, taskcfg.desktop_dist_dir, "*")}',

        'setup-gui.exe': '../synode.py/dist/setup-gui.exe',
        'setup-cli.exe': '../synode.py/dist/setup-cli.exe',
        'uninstall-srv.exe': '../synode.py/dist/uninstall-srv.exe'
    }

    excludes = ['*.log', 'report.html', '*.github.json']

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

        zip = Utils.move_anyway(zip, pth_packagedir(taskcfg), log=True)

        print('****************************************************************************************************',
             f'* Distribution ZIP file is created successfully: {zip}' if not err else 'Errors while making target (creaded zip file)',
              '****************************************************************************************************',
              sep='\n')

        # Also build desktop standalone
        print('****************************************************************************************************')
        c.run(f"cd {taskcfg.desktop_dir} && invoke zip-standalone --deploy={Path(deploy).absolute()}")

        Utils.copy_anyway(taskcfg.get_deskapp_zip(), taskcfg.package_dir, log=True)
        Utils.copy_anyway(taskcfg.get_gradleprj_apk(), Path(taskcfg.package_dir) / taskcfg.get_apk_name(), log=True)
        print('****************************************************************************************************')

    except Exception as e:
        print(f"Error creating ZIP file: {str(e)}", file=sys.stderr)
        raise


@task
def post_package(c, deploy:str = 'task.json'):
    '''
    Run scp etc.
    :param c:
    :param deploy:
    :return:
    '''
    print('--------------    post package   ------------------')
    global taskcfg
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file(deploy))

    ok, err = taskcfg.run_deploycmds(c)
    print(f"Run deploy_cmds, ok: {ok}, error: {err}")

    taskcfg.run_deployscps(str(taskcfg.get_distzip()))
    taskcfg.run_deployscps(str(Path(taskcfg.package_dir) / taskcfg.get_apk_name()))
    taskcfg.run_deployscps(str(Path(taskcfg.package_dir) / taskcfg.deskzip_name()))
    print('', sep='\n')
    print(f"Run deploy_cmds, 3 package copyied.")


@task
def make(c, deploy: str = 'tasks.json', gpg: str = None):
    '''
    This task is for separating python 3.9 for build & packaging,
    from python 3.10 and above for scp command in post_package.
    '''
    if gpg is not None:
        install_maven_local(c, gpg)

    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))
    clean(c)
    create_volume(c)
    build(c, deploy=deploy)
    package(c, deploy=deploy)


@task
def deploy(c, deploy: str = 'tasks.json', gpg: str = None):
    make(c, deploy=deploy, gpg=gpg)
    post_package(c, deploy=deploy)
    print(f'deploying {deploy}, central task: {taskcfg.central_dir} ...')


@task
def landing(c, deploy: str = 'tasks.json'):
    global taskcfg
    print(deploy)
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file(deploy))

    print(f'deploying {deploy}, central task: {taskcfg.central_dir} ...')
    taskcfg.publish_landings()


@task
def pause(c):
    input('Press Enter to continue...')


@task(post=[config, pause, post_package])
def config_post(c, deploy: str = 'tasks.json'):
    print(f'Testing : {deploy}')
    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))


@task(post=[clean])
def test_clean(c, deploy: str = 'tasks.json'):
    print(f'Testing : {deploy}')
    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))


if __name__ == '__main__':
    from invoke import Program
    Program(namespace=globals()).run()
