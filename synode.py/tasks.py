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
import sys
from pathlib import Path
from types import LambdaType
from typing import cast

from anclient.io.odysz.jclient import SessionClient, OnError
from anson.io.odysz.anson import Anson
from anson.io.odysz.common import Utils, LangExt
from invoke import task, Context
from semanticshare.io.odysz.semantic.jprotocol import AnsonMsg, MsgCode
from semanticshare.io.oz.invoke import SynodeTask
from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings
from semanticshare.io.oz.syn.registry import AnRegistry, SynodeConfig, RegistReq, Centralport, RegistResp, SynOrg
from synodepy3 import SynodeUi

ORG = 'ura'
DOMAIN = 'zsu'
'''
Not used?
'''

res_toclean = ['dist', '*egg-info']


@task
def validate(c):
    print('---------     Synode.py3 Validating    --------------')
    # srcpy = os.path.join('src', 'synodepy3', '__main__.py')
    for srcpy in ['src/synodepy3/__main__.py', 'src/synodepy3/prompt.py']:
        with open(srcpy, 'r', encoding='utf-8') as f:
            for lx, line in enumerate(f, start=1):
                if '(__file__)' in line and not line.strip().startswith('#'):
                    Utils.warn('################################################################################\n#')
                    Utils.warn(f'# {srcpy} is supposed to be packaaged as an exe entry, but found it is using itself\'s __file__ property.')
                    Utils.warn(f'# This can be an error as the exe is running in a temp environment.\n#')
                    Utils.warn(f'# {lx}:    {line}')
                    input('  Press Enter to continue...')
    
    from semanticshare.io.oz.invoke import requir_pkg

    requir_pkg("semantics.py3", "0.5.8")
    requir_pkg("anson.py3", "0.5.5")
    requir_pkg("anclient.py3", "0.2.6")
    requir_pkg("jre-mirror", "0.0.8")


@task
def register_org(c: Context, taskcfg: SynodeTask):
    regiserv = f'http://{taskcfg.deploy.central_iport}/{taskcfg.deploy.central_path}'

    def registerOrg(client: SessionClient, func_uri: str, market: str, orgid: str):
        org = SynOrg(orgtype=market, orgid=orgid, orgname=orgid)
        req = RegistReq(RegistReq.A.createOrg, market)
        req.Uri(func_uri).dictionary(SynodeConfig(org=org)).as_jserv(regiserv)
        msg = AnsonMsg(Centralport.regist).Body(req).Header(ssinf=client.ssInf)

        onerr = OnError(on_err= lambda c, e, args: sys.exit(e))
        resp = client.commit(msg, onerr)

        if resp is not None:
            print(client.myservRt, resp.code)
            print(f'<{RegistReq.A.registDom}>', resp.toBlock())

        return cast(RegistResp, resp)

    print("* login   :", regiserv)
    ssclient = SessionClient.loginWithUri(servroot=regiserv,
            uri='/sys/tasks', uid=taskcfg.deploy.admin, pswdPlain=taskcfg.deploy.central_pswd)

    print("* register:", regiserv)
    resp = registerOrg(client=ssclient, func_uri='/sys/tasks',
                       market=taskcfg.deploy.market_id, orgid=taskcfg.deploy.orgid)
    if resp.code != MsgCode.ok:
        print('*', resp.msg())
        sys.exit(f'Cannot create / update org {taskcfg.deploy.orgid} in market {taskcfg.deploy.market_id}')
    else:
        print('* OK!')
        print('*', resp.msg())


@task(validate)
def config(c, abstask_json: str):
    print('--------------    configuration   ------------------')

    this_directory = os.getcwd()

    taskcfg = cast(SynodeTask, Anson.from_file(abstask_json))
    version_file = os.path.join(this_directory, 'src', 'synodepy3', '__version__.py')
    Utils.update_patterns(version_file, {
        'synode_ver = "[0-9\\.]+"': f'synode_ver = "{taskcfg.version}"',
        'jar_ver = "[0-9\\.]+"': f'jar_ver = "{taskcfg.version}"',
        'web_ver = "[0-9\\.]+"': f'web_ver = "{taskcfg.web_ver}"',
        'html_srver = "[0-9\\.]+"': f'html_srver = "{taskcfg.html_jar_v}"',
        'desktop_ver = "[0-9\\.]+"': f'desktop_ver = "{taskcfg.desktop_ver}"',
        'ipcagent_ver = "[0-9\\.]+"': f'ipcagent_ver = "{taskcfg.ipcagent_ver}"'
    })

    synode_settings: AppSettings = cast(AppSettings, Anson.from_file(Path('WEB-INF') / 'settings.json'))
    synode_settings.regiserv = f'http://{taskcfg.deploy.central_iport}/{taskcfg.deploy.central_path}'
    synode_settings.jservs = {}
    synode_settings.market_id = taskcfg.deploy.market_id
    synode_settings.market_name = taskcfg.deploy.market
    synode_settings.jserv_utc = '1911-10-10'
    synode_settings.centralPswd = taskcfg.deploy.central_pswd
    synode_settings.toFile(Path('WEB-INF') / 'settings.json')

    synode_ui = cast(SynodeUi, Anson.from_file(Path('src') / 'synodepy3' / 'synode.github.json'))
    if LangExt.len(taskcfg.deploy.mirror_path) > 0:
        # according to synode_ui, not tasks.json
        for lang, ss in synode_ui.langs.items():
            if lang in taskcfg.deploy.mirror_path:
                inject = taskcfg.deploy.mirror_path[lang]
                ss.update({'jre_mirror': inject})
                print(f'jre_mirror updated: [{lang}: {inject}]')
            else:
                print(f'**** WARING **** : {lang}.jre_mirror is not configured in tasks.json. value: {ss.get("jre_mirror")}')

    synode_ui.toFile(Path('src') / 'synodepy3' / 'synode.json')

    dom_registry: AnRegistry = cast(AnRegistry, Anson.from_file(Path('registry') / 'dictionary.github.json'))
    dom_registry.config.org.orgId = taskcfg.deploy.orgid
    dom_registry.config.org.orgType = taskcfg.deploy.market_id
    dom_registry.toFile(Path('registry') / 'dictionary.json')

    Utils.update_patterns('pyproject.toml',
                          {'version = "[0-9\\.]+" # ': f'version = "{taskcfg.version}" # '})

    print("***********************************************")
    print(f"* Registering Markt Org {taskcfg.deploy.market_id} : {taskcfg.deploy.orgid}")
    register_org(c, taskcfg=taskcfg)
    print("* TODO - to further simplify configuration, let's setup the default domain.")
    print("***********************************************")


@task
def build(c: Context, deploy: str):

    config(c, abstask_json = deploy)

    def py():
        return 'py' if os.name == 'nt' else 'python3'

    def rm_dist():
        for res in res_toclean:
            Utils.rm_any(res)
        return None

    buildcmds = [
        ['.', lambda: rm_dist()],
        ['.', f'{py()} -m build'],
        ['.', f'{py()} pyinstallerw.py'],
    ]

    print('--------------       building synode.py     ------------------')
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

