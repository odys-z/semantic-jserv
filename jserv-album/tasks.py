"""
invoke make
"""
import shutil
import sys
from types import LambdaType
from typing import cast
from anson.io.odysz.common import Utils
from anson.io.odysz.utils import zip2
from invoke import task, call
import os

from semanticshare.io.oz.invoke import requir_pkg, SynodeTask, CentralTask

requir_pkg("anson.py3", "0.4.3")
requir_pkg("semantics.py3", "0.5.0")

from anson.io.odysz.anson import Anson
from semanticshare.io.oz.syntier.serv import ExternalHosts

version_pattern = '[0-9\\.]+'

# dictionary.json
synuser_pswd_pattern = '\"pswd\"\\s*:\\s*\"[^"]*\"'
org_orgid_pattern    = '\"orgId\"\\s*:\\s*\"[^"]*\"'

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

# post_vals = {}

taskcfg = cast(SynodeTask, None)

@task
def validate(c):
    print(f'--------------    validate   ------------------')
    # print(c)
    global taskcfg
    if taskcfg is None:
        taskcfg = cast(SynodeTask, Anson.from_file('tasks.json'))

    print('taskcfg:', taskcfg.deploy.orgid, taskcfg.version)

    task_cent = cast(CentralTask, Anson.from_file(os.path.join(taskcfg.central_dir, 'tasks.json')))

    if taskcfg.deploy.central_pswd != task_cent.users['admin']['pswd']: # Issue: should be ['admin'].pswd:
        Utils.warn('Warning: central_pswd is not set to default value.', file=sys.stderr)
        sys.exit(1)


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
    print('Updating host.json with APK resource...', taskcfg.host_json)

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

# synode_json_bak = os.path.join(os.getcwd(), 'synode.json.bak')
# synode_json = ''

@task(pre=[call(validate)])
def config(c):
    print(f'--------------    configuration   ------------------')

    # this_directory = os.getcwd()

    print(f'-- synode version: {taskcfg.version} --'),

    # version_file = os.path.join(this_directory, 'pom.xml')
    version_file = 'pom.xml'
    Utils.update_patterns(version_file, {
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{version_pattern}</version>':
        f'<!-- auto update token TASKS.PY/CONFIG --><version>{taskcfg.version}</version>',
    })

    version_file = os.path.join(taskcfg.android_dir, 'build.gradle')
    Utils.update_patterns(version_file, {
        f"app_ver = '{version_pattern}'": f"app_ver = '{taskcfg.apk_ver}'"
    })

    # FIXME This is not correct. To be moved to synode.py tasks.py
    # global synode_json_bak, synode_json
    # synode_json = os.path.join(this_directory, '../synode.py/src/synodepy3/synode.json')
    # shutil.copy2(synode_json, synode_json_bak)
    synode_json = taskcfg.backup('../synode.py/src/synodepy3/synode.json')
    Utils.update_patterns(synode_json, {
        re_market_id: f'"market_id": "{taskcfg.deploy.market_id}"',
        re_mirror_path('en'): f'"jre_mirror": "{taskcfg.deploy.mirror_path}"',
        re_central_iport: f'"central_iport": "{taskcfg.deploy.central_iport}"',
        re_central_path:  f'"central_path" : "{taskcfg.deploy.central_path}"'
    })

    diction_file = taskcfg.backup(os.path.join(taskcfg.registry_dir, 'dictionary.json'))
    Utils.update_patterns(diction_file, {
        org_orgid_pattern   : f'"orgId": "{taskcfg.deploy.orgid}"',
        synuser_pswd_pattern: f'"pswd": "{taskcfg.deploy.syn_admin_pswd}"'
    })

    settings_json = taskcfg.backup(os.path.join(taskcfg.web_inf_dir, 'settings.json'))
    Utils.update_patterns(settings_json, {
        re_central_pswd: f'"centralPswd" : "{taskcfg.deploy.central_pswd}"',
        re_webport     : f'"webport"     : {taskcfg.deploy.web_port}',
        re_jserv_port  : f'"port"        : {taskcfg.deploy.jserv_port}',
        re_install_key : f'"installkey"  : "{taskcfg.deploy.root_key}"'
    })

    ''' And save tasks-central.json
    central_settings = cast(SynodeTask, Anson.from_file('central/settings.json'))
    taskcfg.config_central(central_settings)
    central_settings.toFile('central/settings.json')
    '''

@task
def clean(c):
    if not os.path.exists(taskcfg.dist_dir):
        os.makedirs(taskcfg.dist_dir, exist_ok=True)

    for item in os.listdir(taskcfg.dist_dir):
        item_path = os.path.join(taskcfg.dist_dir, item)
        print('cleaning', item_path, taskcfg.zip_name())
        if item_path == taskcfg.zip_name():
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
def package(c):
    """
    Create a ZIP file.
    
    Args:
        c: Invoke Context object for running commands.
        zip: Name of the output ZIP file.
    """

    jre_img = taskcfg.jre_release.split('/')[-1]
    temp_jre_path = f'jre17-temp/{jre_img}'

    # dist_name = f'{taskcfg.jre_name if not LangExt.isblank(taskcfg.jre_release) else "online"}-{taskcfg.deploy.market_id}-{taskcfg.deploy.orgid}'
    # if zip is None:
    #     zip = f'portfolio-synode-{taskcfg.version}-{dist_name}.zip'
    zip = taskcfg.zip_name()

    resources = {
        f'bin/html-web-{taskcfg.html_jar_v}.jar': f'../../html-service/java/target/html-web-{taskcfg.html_jar_v}.jar', # clone at github/html-service
        f'bin/jserv-album-{taskcfg.version}.jar': f'target/jserv-album-{taskcfg.version}.jar',
        
        # https://exiftool.org/index.html
        'bin/exiftool.zip': './task-res-exiftool-13.21_64.zip',
        
        temp_jre_path: taskcfg.jre_release,

        # 'WEB-INF': 'src/main/webapp/WEB-INF-0.7/*', # Do not replace with version.
        'WEB-INF': f'{taskcfg.web_inf_dir}/*',

        'bin/synode_py3-0.7-py3-none-any.whl': f'../synode.py/dist/synode_py3-{taskcfg.version}-py3-none-any.whl',
        "registry": "../synode.py/registry/*",
        'winsrv': '../synode.py/winsrv/*',
        "res": "../synode.py/src/synodepy3/res/*",

        'web-dist': 'web-dist/*',   # use a link for different Anclient folder name
                                    # ln -s ../Anclient/examples/example.js/album web-dist
                                    # mklink /D web-dist ..\anclient\examples\example.js\album

        'setup-gui.exe': '../synode.py/dist/setup-gui.exe',
        'setup-cli.exe': '../synode.py/dist/setup-cli.exe'
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
        # distzip = os.path.join(taskcfg.dist_dir, zip)
        distzip = taskcfg.get_distzip()

        if os.path.isfile(distzip):
            os.remove(distzip)

        print(zip, "->", distzip)
        os.rename(zip, distzip)
        taskcfg.distzip = distzip

        print('****************************************************************************************************',
             f'* Distribution ZIP file is created successfully: {distzip}' if not err else 'Errors while making target (creaded zip file)',
              '****************************************************************************************************',
              sep='\n')

    except Exception as e:
        print(f"Error creating ZIP file: {str(e)}", file=sys.stderr)
        raise

@task
def post_package(c):
    print('--------------    post build   ------------------')
    taskcfg.restore_backups()
    taskcfg.run_deploycmds(c)
    taskcfg.run_deployscps()


@task(clean, create_volume, build, package, post_package)
def make(c):
    """
    Create a ZIP file with the specified resources.
    
    Args:
        c: Invoke Context object for running commands.
    """
    print('Package be created successfully.')
    print('********************************************************************************\n'
          '* But Task make is deprecated, please use: invoke deploy --deploy tasks.json . *\n'
          '********************************************************************************')

@task(post=[clean, create_volume, build, package, post_package])
def deploy(c, deploy: str = 'tasks.json'):
    global taskcfg
    taskcfg = cast(SynodeTask, Anson.from_file(deploy))
    print(f'deploying {deploy}, central task: {taskcfg.central_dir} ...')

@task
def landing(c, deploy: str = None):
    global taskcfg
    print(deploy)
    if taskcfg is None:
        if deploy is None:
            deploy = 'tasks.json'

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
