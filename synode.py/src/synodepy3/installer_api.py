# This Python file uses the following encoding: utf-8
import datetime
import sys
from dataclasses import dataclass

from jre_mirror.temurin17 import Temurin17Release
from semanticshare.io.odysz.jclient import AnclientSettings
from semanticshare.io.oz.anclient.app import DesktopSettings, UIResources
from semanticshare.io.oz.syn import SynodeMode, Synode

from .jre_downloader import JreDownloader, _jre_, java_cmd, validate_jre

sys.stdout.reconfigure(encoding="utf-8")

import ipaddress
import json
import os
import shutil
import socket
import subprocess
import time
from pathlib import Path
from typing import List, cast, Optional, Callable, Final, Iterable, Tuple

from anson.io.odysz.anson import Anson, AnsonException
from anson.io.odysz.common import Utils, LangExt

from semanticshare.io.oz.srv import WebConfig

from semanticshare.io.odysz.semantic.jprotocol import MsgCode, AnsonMsg, JProtocol, JServUrl
from semanticshare.io.oz.syntier.serv import ExternalHosts
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException,\
    AppSettings, implISettingsLoaded, \
    sys_db, syn_db, syntity_json, getJservUrl, valid_url_port
from semanticshare.io.oz.syn.registry import AnRegistry, SynodeConfig, RegistReq, \
    Centralport, RegistResp, SynOrg, CynodeStats

from anclient.io.odysz.jclient import Clients, OnError, SessionClient

from semanticshare.io.oz.syn.pkgresource import PkgResources

from .__version__ import jar_ver, web_ver, html_srver, ipcagent_ver

from . import jre_mirror_key
from .prerequisites import check_exiftool, checkinstall_exiftool, exiftool_exe, exiftool_missing_msg
from .validators import check_domain_id

mypath : str = os.path.dirname(__file__)


def readable_state(s: str = ''):
    """
    The registered state of a synode id, for users choosing the id (setup-cli & setup-gui).
    """
    return '' if LangExt.len(s) == 0 \
            else '✅ Available planned node' if s == CynodeStats.create \
            else '⛔ Already running as a Hub node' if s == CynodeStats.asHub \
            else '⛔ Already running as a Peer node' if s == CynodeStats.asPeer \
            else '[❗] Unknown state (Dangerous! Create a new domain if possible)'


def available_state(s: str = '') -> bool:
    """
    Can a synode id of the state be installed (setup-cli & setup-gui)?
    Only a planned (create) or unknown-yet (blank) one. A running synode (Java, AppSettings.synotifyCentral())
    is the only one setting 'h' or 'p' at the central, while the installers submit no state.
    """
    return LangExt.len(s) == 0 or s == CynodeStats.create


def state_mark(s: str = ''):
    """
    The short mark of readable_state(), prefixing synode ids where the space is limited.
    """
    return '' if LangExt.len(s) == 0 \
            else '✅' if s == CynodeStats.create \
            else '⛔' if s in (CynodeStats.asHub, CynodeStats.asPeer) \
            else '❗'

'''
this script file path
'''

@dataclass
class CfgPaths:
    '''
    Configuration file paths' memory
    '''
    vol_dict_json: str #Optional[str]
    web_settings: str # Optional[str]
    def __init__(self):
        pass
        # self.vol_dict_json = cast(Optional[str], None)
        # self.web_settings = cast(Optional[str], None)

cfgpaths = CfgPaths()
'''
vol_dict_json: file path to dictionary.json,
web_settings: file path to settings.json
'''

def posix_path(path: Optional[str]) -> Optional[str]:
    """
    Convert a user input path to a posix path, e.g. 'C:\\Users\\vol' -> 'C:/Users/vol'.
    MEMO: Anson writes strings unescaped, so a Windows path saved in settings.json
    breaks the json (Invalid \\escape), and setup-gui can't load it after setup-cli.
    """
    return path if LangExt.isblank(path) else os.path.normpath(path.strip()).replace('\\', '/')


upgrade_pkg: Final = 'upgrade.pkg'
upgrade_exe: Final = 'upgrade.exe'


def promote_upgrade_pkg(folder: str = '.') -> bool:
    """
    Move <folder>/upgrade.pkg, the upgrade.exe packed in the package (jserv-album/tasks.py), over
    <folder>/upgrade.exe. The package carries it as upgrade.pkg so that unpacking it over an
    installation (commands.update_srv(), run by upgrade.exe) can't hit the running upgrade.exe.
    :param folder: the installation folder, default the working folder (where setup-* runs).
    :return: True if upgrade.exe has been replaced; False if there is no upgrade.pkg, or upgrade.exe
             is locked (running), in which case upgrade.pkg is kept for the next try.
    """
    pkg, exe = os.path.join(folder, upgrade_pkg), os.path.join(folder, upgrade_exe)
    if not os.path.isfile(pkg):
        return False
    try:
        os.replace(pkg, exe)
        print(f'{upgrade_pkg} -> {os.path.abspath(exe)}')
        return True
    except OSError as e:
        Utils.warn(f'Cannot replace {exe} with {upgrade_pkg} (is it running?), {upgrade_pkg} is kept: {e}')
        return False


desktop_dir: Final = 'desktop'
desktop_app: Final = 'album_gui'
"""
The desktop app packaged in <installation>/desktop (Anclient/examples/example.slint, dist/), which
reads settings/app-settings.json relative to the working folder, so it must start in desktop/.
"""


def desktop_exe(folder: str = '.') -> Optional[Path]:
    """
    :param folder: the installation folder, default the working folder (where setup-* runs).
    :return: <folder>/desktop/album_gui(.exe), or None if the desktop is not packaged.
    """
    exe = Path(folder) / desktop_dir / (desktop_app + '.exe' if Utils.iswindows() else desktop_app)
    return exe if exe.is_file() else None


def desktop_running(folder: str = '.') -> bool:
    """
    Windows: whether <folder>/desktop/album_gui.exe is running (a running exe can't be opened for
    writing), which would stop an upgrade's unpack half way. Elsewhere a running binary can be
    replaced, so False.
    """
    exe = desktop_exe(folder)
    if exe is None or not Utils.iswindows():
        return False
    try:
        with open(exe, 'r+b'):
            return False
    except OSError:
        return True


def start_desktop(folder: str = '.') -> Optional[subprocess.Popen]:
    """
    Start the desktop app, detached from setup-*, in its own folder, <folder>/desktop.
    :return: the process, or None if the desktop is not packaged.
    :raise OSError: if it can't be started.
    """
    exe = desktop_exe(folder)
    if exe is None:
        return None
    exe = exe.absolute()

    if not Utils.iswindows():
        return subprocess.Popen([str(exe)], cwd=exe.parent, start_new_session=True,
                                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    # A frozen setup-* (PyInstaller) may have set the DLL directory to its _internal/, which the child
    # inherits - the desktop must load its own Qt / runtime DLLs, not setup-gui's PySide6 ones.
    import ctypes
    k32 = ctypes.windll.kernel32
    buf = ctypes.create_unicode_buffer(32768)
    dlldir = k32.GetDllDirectoryW(len(buf), buf)
    k32.SetDllDirectoryW(None)
    try:
        return subprocess.Popen([str(exe)], cwd=exe.parent, close_fds=True,
                                creationflags=subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP)
    finally:
        if dlldir:
            k32.SetDllDirectoryW(buf.value)


def promote_upgrade_whl(folder: str = '.') -> bool:
    """
    The Linux counterpart of promote_upgrade_pkg(). On Linux unpacking a new package only brings the new
    wheel, <folder>/bin/synode_py3-*.whl. This installs it, with the Python running this, so the
    next upgrade runs the new version. pip --upgrade keeps a re-deployment of the same version as is.

    Linux can replace the files of the running package (the process keeps the modules it has loaded),
    so this can be the last step of the running upgrade (commands.update_srv()).
    :param folder: the installation folder, default the working folder.
    :return: True if pip succeeded.
    """
    whls = sorted(Path(folder, 'bin').glob('synode_py3-*.whl'), key=os.path.getmtime, reverse=True)
    if not whls:
        print(f'No bin/synode_py3-*.whl in {os.path.abspath(folder)}, the upgrade tool is kept as is.')
        return False
    cmd = [sys.executable, '-m', 'pip', 'install', '--upgrade', str(whls[0])]
    print(' '.join(cmd))
    r = subprocess.run(cmd)
    if r.returncode != 0:
        Utils.warn(f'Cannot install {whls[0]} (pip exit code {r.returncode}); install it by hand for the next '
                   f'upgrade:\n    {" ".join(cmd)}')
    return r.returncode == 0


synode_ui = cast(UIResources, Anson.from_file(os.path.join(mypath, "synode.json")))
err_uihandlers: list[OnError] = cast(list, [None])

def ping(clientUri: str, peerserv: str, timeout_snd: int = 10):
    print(f'pinging {peerserv} with timeout {timeout_snd} seconds...')
    Clients.init(jserv=peerserv, timeout=timeout_snd)

    def err_ctx(c: MsgCode, e: str, *args: str) -> None:
        print(c, e.format(args), file=sys.stderr)

        if len(err_uihandlers) > 0 and err_uihandlers[0] is not None:
            for h in err_uihandlers:
                h(c, e, *args)

    resp = Clients.pingLess(clientUri or install_uri, err_ctx)

    if resp is not None:
        print(Clients.servRt, '<echo>', resp.toBlock())
        print('code', resp.code)
    return resp


# ISSUE central-login: requests to central below carry no AnsonHeader, see issues/central-client-login.md
def query_domx(client: SessionClient, func_uri: str, market: str, commuid: str):
    req = RegistReq(RegistReq.A.queryDomx, market=market)
    req.Uri(func_uri)
    req.market = market

    org = SynOrg()
    org.orgId = commuid
    req.dictionary(SynodeConfig(org=org))

    msg = AnsonMsg(Centralport.regist).Body(req)

    resp = client.commit(msg, err_uihandlers[0])

    if resp is not None:
        print(client.myservRt, resp.code)
        print(f'<{RegistReq.A.queryDomx}>', resp.toBlock())

    return cast(RegistResp, resp)


def query_domconfig(client: SessionClient, func_uri: str, market: str, orgid: str, domid: str, myid:str):
    req = RegistReq(RegistReq.A.queryDomConfig, market)
    req.Uri(func_uri)
    req.diction = SynodeConfig(synode=myid, domain=domid)
    req.diction.org = SynOrg(orgid=orgid, orgname=domid, orgtype=market)
    msg = cast(AnsonMsg, AnsonMsg(Centralport.regist).Body(req))

    resp = client.commit(msg, err_uihandlers[0])

    if resp is not None:
        print("query_domconfig() respons:", client.myservRt, resp.code)
        print(f'<{RegistReq.A.queryDomConfig}>', resp.toBlock())

    return cast(RegistResp, resp)


def register(client: SessionClient, func_uri: str, market: str, cfg: SynodeConfig,
             s: AppSettings, iport: tuple[str, int], jprotocl: JProtocol):
    '''
    Ask central for registering a domain, expecting a reply with planned synodes (peers).
    :param client:
    :param func_uri:
    :param market:
    :param cfg:
    :param s:
    :param iport:
    :return:
    '''
    req = RegistReq(RegistReq.A.registDom, market)
    req.Uri(func_uri).dictionary(cfg).jserurl(cfg.https, iport=iport, jprotcl=jprotocl)
    msg = AnsonMsg(Centralport.regist).Body(req)

    resp = client.commit(msg, err_uihandlers[0])

    if resp is not None:
        print(client.myservRt, resp.code)
        print(f'<{RegistReq.A.registDom}>', resp.toBlock())

    return cast(RegistResp, resp)


def submit_settings(client: SessionClient, func_uri: str, market: str,
                    cfg: SynodeConfig, s: AppSettings, iport: tuple[str, int], syn_protocol: JProtocol,
                    stat: str = CynodeStats.create):
    req = RegistReq(RegistReq.A.submitSettings, market)\
        .Uri(func_uri)\
        .jserurl(https=cfg.https, iport=iport, jprotcl=syn_protocol)\
        .dictionary(cfg)\
        .mystate(stat)\
        .Jservtime(s.jserv_utc) # .protocol_path(syn_jprotocol.protocolpath)

    msg = AnsonMsg(Centralport.regist).Body(req)

    resp = client.commit(msg, err_uihandlers[0])
    if resp is not None:
        print(client.myservRt, resp.code)
        print(f'<{RegistReq.A.submitSettings}>', resp.toBlock())

    return cast(RegistResp, resp)


def valid_local_reg(reg: AnRegistry):
    """
    config.admin must presents in syn-users, which should be more than or equal 1 record.
    :param reg:
    :return:
    """
    # 0.7.6
    if LangExt.len(reg.synusers) < 1 or AnRegistry.find_synuser(reg.synusers, id=reg.config.admin) is None:
        raise PortfolioException('Dictionary Synuser configuration is invalid.')
    pass


jserv_album: Final[str] = 'jserv-album'

install_uri = 'Anson.py3/test'

host_private = 'private'
web_host_json = f'{host_private}/host.json'

album_web_dist = 'web-dist'
index_html = 'index.html'

dictionary_json = 'dictionary.json'
web_inf = 'WEB-INF'
registry_dir = 'registry'
settings_json = 'settings.json'

html_service_json = 'html-service.json'
html_web_jar = f'html-web-{html_srver}.jar'

serv_port0 = 8964
web_port0 = 8900
jserv_07_jar = f'jserv-album-{jar_ver}.jar'

resource_json = os.path.join('bin', 'resource.json')
'''
The package's resources, semanticshare PkgResources, generated by jserv-album/tasks.py package()
(SynodeTask.pkg_res()): files {"synode", "html-web", "synode.py"}, paths relative to the installation
folder; versions {"jar_ver", "web_ver", "html_srver", "synodepy3"}; upgrade_from {"min", "max"}.
'''
res_synode, res_htmlweb, res_synodepy = 'synode', 'html-web', 'synode.py'


def load_resources(path: str = resource_json) -> Optional[PkgResources]:
    '''
    :return: the package bin/resource.json, None if there is none (packages built before it).
    '''
    if not os.path.isfile(path):
        return None
    return cast(PkgResources, Anson.from_file(path))


def running_resources() -> PkgResources:
    '''
    The package resources of the version of this synodepy3 (__version__.py), for an installed package without
    bin/resource.json: the upgrade tool (upgrade.exe, synode-upgrade-srv) is the installed one.
    '''
    return PkgResources(files={res_synode: f'bin/{jserv_07_jar}', res_htmlweb: f'bin/{html_web_jar}'},
                           versions={'jar_ver': jar_ver, 'web_ver': web_ver, 'html_srver': html_srver})


def read_package_res(pkg_path: str) -> Optional[PkgResources]:
    '''
    Read bin/resource.json out of an update package (zip or tar.gz), without unpacking it, so that the
    upgrade can be checked before anything is stopped or changed.
    :return: the package's resources, None if it has none (packages built before it).
    '''
    import tarfile
    import tempfile
    import zipfile

    want = resource_json.replace(os.sep, '/')
    data = None
    if zipfile.is_zipfile(pkg_path):
        with zipfile.ZipFile(pkg_path) as z:
            for n in z.namelist():
                if n.removeprefix('./') == want:
                    data = z.read(n)
                    break
    else:
        with tarfile.open(pkg_path) as t:
            for m in t.getmembers():
                if m.isfile() and m.name.removeprefix('./') == want:
                    data = t.extractfile(m).read()
                    break
    if data is None:
        return None
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, 'resource.json')
        with open(p, 'wb') as f:
            f.write(data)
        return load_resources(p)

pswd_min, pswd_max = 8, 32

class InstallerCli:
    regclient: Optional[SessionClient]
    settings: AppSettings
    registry: AnRegistry
    reg_jserv: JServUrl

    syn_protocol: JProtocol
    '''
    Jserv to synodes can be load, modified later, but the protocol is for sure must setup at the first place
    '''
    syn_jserv: JServUrl
    '''
    Can be load, modified later.
    '''

    @staticmethod
    def parsejservstr(jservstr: str) -> list[list[str]]:
        """
        :param jservstr: "x:\\turl-1\\ny:..."
        :return: [[x, url-1], ...]
        """
        return [[kv.strip().removesuffix(':') for kv in line.split('\t')] for line in jservstr.split('\n')]

    # def fromat_jservstr_deprecated(jservstr: str):
    #     return {kv[0]: kv[1] for kv in InstallerCli.parsejservstr(jservstr)}

    @classmethod
    def fromat_jservurl(cls, hub: Synode, jservstr: str):
        return {None if hub is None else hub.synid: kv[-1] for kv in cls.parsejservstr(jservstr)}

    def __init__(self):
        self.regclient = None
        self.httpd = None
        self.webth = None
        # self.registry = cast(AnRegistry, None)
        # self.settings = cast(AppSettings, None)
        self.syn_protocol = JProtocol(jserv_album)
        self.syn_jserv = JServUrl(jservurl='http://localhost', jprotocol=self.syn_protocol)

    def list_synodes(self):
        return self.settings.jservs.items()

    def Jservs(self, jsrvs: dict) :
        self.settings.Jservs(jsrvs)
        return self

    def load_settings(self) -> AppSettings:
        """
        Load from res_path/setings.json,
        :return: loaded settings
        """

        cfgpaths.web_settings = os.path.join(web_inf, settings_json)
        print("Loading", cfgpaths.web_settings)
        if cfgpaths.web_settings and os.path.exists(cfgpaths.web_settings):
            try:
                data: AppSettings = cast(AppSettings, Anson.from_file(cfgpaths.web_settings))
                self.settings = data
            except json.JSONDecodeError as e:
                raise PortfolioException(f'Loading Anson data from {cfgpaths.web_settings} failed.', e)

            print("Loading registry in", '[registry]')
            self.registry = self.loadRegistry(data.volume, registry_dir)

        else:
            raise PortfolioException(f"Cannot find settings.json: {cfgpaths.web_settings}")

        # if LangExt.isblank(self.settings.regiserv):
        #     regiserv = f'{"https" if self.registry.config.https else "http"}://{synode_ui.central_iport}/{synode_ui.central_path}'
        #     self.settings.regiserv = regiserv if JServUrl.valid(regiserv, synode_ui.central_path) else "htt       #
        self.reg_jserv = JServUrl(self.settings.regiserv)
        return self.settings

    @staticmethod
    def loadRegistry(vol_path, deflt_path):
        """
        Load local dictionary.json.
        :param vol_path: if none, load from default path
        :param deflt_path
        :return: AnRegistry
        """
        # cfgpaths.vol_dict_json = None
        if vol_path is not None:
            cfgpaths.vol_dict_json = os.path.join(vol_path, dictionary_json)

        if vol_path is not None and os.path.isdir(vol_path) and Path(cfgpaths.vol_dict_json).is_file():
            registry = AnRegistry.load(cfgpaths.vol_dict_json)
        else:
            diction_json = os.path.join(deflt_path, dictionary_json)
            registry = AnRegistry.load(diction_json)

        valid_local_reg(registry)
        return registry

    def vol_valid(self):
        return self.validateVol() is None

    def chk_synid(self, synid: str) -> Optional[dict]:
        """
        Check the synode id to be saved / installed, while the volume has never run (see hasrun()).
        :return: error, {'synid': reason}, or None if the id is planned (create) or not registered yet.
        """
        n = self.registry.find_peer(synid)
        if n is not None and not available_state(n.stat):
            return {'synid': f'{synid}: {readable_state(n.stat)}. Select another synode id.'}
        return None

    def chk_hub(self) -> Optional[dict]:
        """
        Check the hub synode's jserv of a peer. A peer is allowed to be saved without it (warned),
        but its services can't be installed (chk_srv_install()) until the hub's jserv is saved,
        e.g. saving again once the hub is running.
        :return: {'hub-node': reason} if this peer has no hub's jserv, or None (also for a hub).
        """
        if self.registry.config.mode == SynodeMode.hub.name:
            return None
        hub_node = self.registry.find_hubpeer()
        if hub_node is None:
            return {'hub-node': 'The hub synode is missing in the domain. Save again when the hub node is running.'}
        if LangExt.isblank(self.settings.jservs.get(hub_node.synid) if self.settings.jservs else None):
            return {'hub-node': f'The hub synode, {hub_node.synid}, has no jserv (service address). Save again when the hub node is running.'}
        return None

    def chk_service_resources(self) -> Optional[dict]:
        """
        Check the resources for running the services, by file existence only (no subprocess),
        as it is called for each setup-gui widgets update.
        :return: {item: missing reason}, or None if all are found.
        """
        err = {}
        for jar in (jserv_07_jar, html_web_jar):
            if not os.path.isfile(os.path.join('bin', jar)):
                err[jar] = f'Service package is missing: bin/{jar}'

        if validate_jre() is not None:
            err['jre'] = 'JRE 17 is not available.'

        if Utils.iswindows():
            if not os.path.isfile(exiftool_exe):
                err['exiftool'] = f'Depending tool, exiftool is missing: {exiftool_exe}'
            from .winsrv import install_jserv_w_bat, install_html_w_bat # winsrv imports this module
            for bat in (install_jserv_w_bat, install_html_w_bat):
                if not os.path.isfile(bat):
                    err[bat] = f'Service script is missing: {bat}'
        elif shutil.which('exiftool') is None:
            err['exiftool'] = 'Depending tool, exiftool is not installed.'

        volume = self.settings.Volume()
        if self.validateVol() is not None or LangExt.isblank(volume):
            err['volume'] = f'Volume is not valid: {volume}'
        else:
            for f in (sys_db, syn_db, syntity_json, dictionary_json):
                if not os.path.isfile(os.path.join(volume, f)):
                    err[f] = f'Not saved yet, missing in volume: {f}'

        return err if len(err) > 0 else None

    def chk_srv_install(self, synid: str, installed_srvs: Callable[[], List[str]]) -> Optional[dict]:
        """
        Can the services of the synode id be installed (setup-gui & setup-cli)?
        Services can't be re-installed, other than by upgrading.
        :param synid: the selected synode id
        :param installed_srvs: returns the names of the services already installed, for this synode,
               e.g. winsrv.winsrvs_installed(); called only if all the other checks are passed.
        :return: {reason: details}, or None
        """
        if self.hasrun():
            return {'hasrun': 'The synode has run. Services can only be re-installed by upgrading.'}

        if LangExt.isblank(synid) or synid != self.registry.config.synid:
            return {'saved': f'Save the settings of {synid} first.'}

        err = self.chk_synid(synid) or self.chk_hub() or self.chk_service_resources()
        if err is not None:
            return err

        srvs = installed_srvs()
        if LangExt.len(srvs) > 0:
            return {'installed': f'Service(s) already installed: {", ".join(srvs)}. '
                                 'Services can only be re-installed by upgrading.'}
        return None

    def hasrun(self, override_vol: str = None):
        try:
            psys_db, psyn_db, _ = InstallerCli.sys_syn_db_syntity(self.settings.Volume() if override_vol is None else override_vol)
            return LangExt.len(self.settings.rootkey) > 0 and os.path.exists(psys_db) and os.path.getsize(psys_db) > 0
        except FileNotFoundError:
            return False

    @staticmethod
    def sys_syn_db_syntity(volpath: str):
        """
        Get db paths
        :param volpath:
        :return: path-sys.db, path-syn.db
        """
        path_v = Path(volpath)

        if not Path.exists(path_v):
            Path.mkdir(path_v)
        elif not Path.is_dir(path_v):
            Utils.warn(f'**** ERROR **** Volume path is not a folder: {path_v}')
            raise IOError(f'Volume path is not a folder: {path_v}')

        return Path(os.path.join(path_v, sys_db)), Path(os.path.join(path_v, syn_db)), Path(os.path.join(path_v, syntity_json))

    @staticmethod
    def reportIp() -> str:
        import socket
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            return ip

    def getProxiedIp(self) -> Tuple[str, int]:
        ip, port = InstallerCli.reportIp(), self.settings.port
        if self.settings.reverseProxy:
            ip, port = self.settings.proxyIp, self.settings.proxyPort
        return ip, port

    def get_iport(self):
        ip, port = self.getProxiedIp()
        return f'{ip}:{port}'

    def validate_domain(self, creating: bool = False) -> Optional[dict]:
        '''
        :param creating: True if the domain is new (to be created in central): its id is at most
        validators.domain_maxlen chars, see validators.check_domain_id().
        '''
        # 0.7.7 central-pswd must be re-configured when building
        if LangExt.isblank(self.settings.centralPswd):
            return {"Central Password is invalid (0.7.7 not a valid distribution): ": self.settings.centralPswd}
        try: LangExt.only_passwdlen(self.settings.centralPswd, minlen=6, maxlen=32)
        except AnsonException as e:
            return {"Central Password is invalid, len (6-32)": self.settings.centralPswd}

        cfg = self.registry.config
        err = check_domain_id(cfg.domain, creating)
        if err is not None:
            return {"domain id": err}

        try: LangExt.only_id_len(cfg.org.orgId, minlen=2, maxlen=12)
        except AnsonException as e:
            return {"community length": f"2 <= Len('{cfg.org.orgId}') <= 12"}
        return None

    def validateVol(self) -> Optional[dict]:
        """
        :return: error (invalid)
        """
        volp = self.settings.Volume()
        if volp is None or len(volp) == 0:
            return {"volume": "Volume path is empty."}

        if not Path.is_dir(Path(volp)):
            try:
                os.mkdir(volp)
                return None
            except FileExistsError:
                return {"volume": f'Directory already exists: {volp}'}
            except FileNotFoundError:
                return {"volume": f'Parent directory does not exist: {volp}'}

    def validate_iport(self) -> Optional[dict]:
        try:
            if self.settings.reverseProxy:
                if not ipaddress.ip_address(self.settings.proxyIp):
                    return {'proxy-ip': f'IP address is not valid: {self.settings.proxyIp}' }
                if not valid_url_port(self.settings.proxyPort) or not valid_url_port(self.settings.webProxyPort):
                    return {'proxy-ip': f'Proxy port must greater than 1024: {self.settings.port}' }

            if not valid_url_port(self.settings.port) or not valid_url_port(self.settings.webport):
                return {'proxy-ip': f'Port must greater than 1024: {self.settings.port}' }

        except ValueError as e:
            return {'value error': str(e)}

    def validate_synins(self, syncins_override: str=None):
        '''
        Notes 0.7.6.1: this is a new pattern of validation for both CLI & GUI?
        :param syncins_override: target to be validated, None for validating config.syncIns.
        :return: error, {'config.syncIns': '{syncins_override} is out of range {mns}:{mxs}'}, or None
        '''
        if syncins_override is None:
            syncins_override = self.registry.config.syncIns

        mns, mxs = 10, 3600
        try: syncins_override = float(syncins_override)
        except: return {'config.syncIns': f'{syncins_override} is out of range [{mns} - {mxs}]'}
        if syncins_override < mns or syncins_override > mxs:
            return {'config.syncIns': f'{syncins_override} is out of range [{mns} - {mxs}]'}
        return None

    def check_installed_jar_db(self) -> bool:
        """
        Check jar & vol/*.db resources in folder of settings.valume.
        :return: None
        :raise: FileNotfoundError
        """
        p_jar = os.path.join('bin/', jserv_07_jar)
        if not os.path.isfile(p_jar):
            raise FileNotFoundError(f'Synode service package is missing: {p_jar}')

        # p_exif = os.path.join('bin/', exiftool_exe)
        check_exiftool()
        if Utils.iswindows() and not os.path.isfile(exiftool_exe):
            raise FileNotFoundError(f'Depending tool, exiftool is missing: {exiftool_exe}')
        else:
            Utils.warn('TODO: check exiftool on {0}', Utils.get_os())

        volume = self.settings.Volume()
        if (not os.path.isfile(os.path.join(volume, sys_db))
                or not os.path.isfile(os.path.join(volume, syn_db))
                or not os.path.isfile(os.path.join(volume, syntity_json))):
            raise FileNotFoundError(
                'Some initial database or configure files cannot be found '
                f'in {volume}: {sys_db}, {syn_db}, {syntity_json}')
        return True

    def check_src_jar_db(self):
        """
        check jar & volume/*.db resources
        :return: None
        :raise: FileNotfoundError
        """
        p_jar = os.path.join('bin/', jserv_07_jar)
        if not os.path.isfile(p_jar):
            Utils.warn(f'**** ERROR **** Synode service package is missing: {p_jar}')
            raise FileNotFoundError(f'Synode service package is missing: {p_jar}')

        p_jar = os.path.join('bin/', html_web_jar)
        if not os.path.isfile(p_jar):
            Utils.warn(f'**** ERROR **** Synode html server package is missing: {p_jar}')
            raise FileNotFoundError(f'Synode html server package is missing: {p_jar}')

        if not os.path.isfile(os.path.join('volume', sys_db)) or \
            not os.path.isfile(os.path.join('volume', syn_db)):
            Utils.warn(f'**** ERROR **** Some initial database or configure files cannot be found in volume: {sys_db}, {syn_db}')
            raise FileNotFoundError(
                f'Some initial database or configure files cannot be found in volume: {sys_db}, {syn_db}')
        return True

    def is_hub(self, sid):
        return self.find_hubpeer().synid == sid if LangExt.len(self.registry.config.peers) > 0 else False

    def find_hubpeer(self):
        return self.registry.find_hubpeer()

    def find_peer(self, pid: str):
        return AnRegistry.find_synode(self.registry.config.peers, pid)

    def find_synuser(self, uid: str):
        return AnRegistry.find_synuser(self.registry.synusers, uid)

    def validate(self, ping_hub: bool=True, ping_timeout: int=12) -> Optional[dict]:
        """
        Validate my congig and settings. Must be called after the data models has been updated.
        NOTE 0.7.6 org.webroot will be forced to be '$WEBROOT' and settings.envars['WEBROOT'] = this.synode
        # :param warn:
        # :param synid:
        # :param volpath:
        # :param peerjservs:
        # :param warn: if None, return jserv error if ping failed, otherwise warn by calling this function
        :return: error {name: input-data} if there are errors.
        """
        cfg = self.registry.config

        # The package's resources, checked again by install(); here before the time-consuming
        # JRE download, check_install_jre(), which both setup-cli and setup-gui do after validating.
        try: self.check_src_jar_db()
        except FileNotFoundError as e:
            return {'package': str(e)}

        v = self.validate_domain()
        if v is not None: return v

        p = self.validatePswdOf(cfg.admin)
        if p is not None: return p

        v = self.validateVol()
        if v is not None: return v

        v = self.validate_iport()
        if v is not None: return v

        if LangExt.len(cfg.peers) == 0:
            return {'domain': f'No synodes of domain "{cfg.domain}" are loaded from the central. '
                              'Select a domain, or create one.'}

        # TODO 0.7.8 Check the synode id is domain wide unique, in Central.
        if self.find_peer(self.registry.config.synid) is None:
            return {'peer-id': f'synode id is not a peer id: {self.registry.config.synid}'}

        if cfg.synid not in self.settings.jservs:
            return {'synode-id': f'synode id is not a service node: {self.registry.config.synid}'}

        if not checkinstall_exiftool():
            return {"exiftool": exiftool_missing_msg()}

        if cfg.mode != SynodeMode.hub.name:
            v = self.validate_synins()
            if v is not None: return v
            # self.ping(self.settings.jservs[cfg.peers[0].synid])
            # A peer can be saved and started without the hub's jserv, see chk_hub().
            if ping_hub and self.chk_hub() is None:
                hub_node = self.registry.find_hubpeer()
                self.ping(self.settings.jservs[hub_node.synid], timeout=ping_timeout)

        if LangExt.len(self.registry.synusers) == 0:
            return {'synusers': 'Admin must present'}
        else:
            for u in self.registry.synusers:
                u.org, u.domain = cfg.org.orgId, cfg.domain

        return None

    def matchPswds(self, p1: str, p2: str):
        if p1 == p2:
            return p1
        else: raise PortfolioException("Not equals.")

    def validatePswdOf(self, userId):
        admin = self.find_synuser(userId)

        if admin is not None:
            try: LangExt.only_passwdlen(admin.pswd, minlen=pswd_min, maxlen=pswd_max)
            except AnsonException as e:
                return e.err
            return None
        return 'Empty user Id'

    def postFix(self):
        """
        Verify the installation after finished, e.g. for errors introduced when failed to start Jetty App.
        1. check volume/*.db size. If size is 0, then set root-key = null, for forcing Jetty App re-setup dbs.
        :return: error details, fixed
        :exception: found errors, unable to fix
        """
        if LangExt.isblank(self.settings.installkey) and not LangExt.isblank(self.settings.rootkey):
            sysdb, syndb, _ = InstallerCli.sys_syn_db_syntity(self.settings.Volume())
            if  os.path.isfile(sysdb) and os.stat(sysdb).st_size == 0 and \
                os.path.isfile(syndb) and os.stat(syndb).st_size == 0:

                self.settings.installkey, self.settings.rootkey = self.settings.rootkey, ''
                self.settings.toFile(os.path.join(web_inf, settings_json))
                return f'Fixed errors: {sysdb} size & {syndb} size = 0, reset flags for setup db.'
            elif os.path.isfile(sysdb) and os.path.isfile(syndb) and (os.stat(syndb).st_size > 0 or os.stat(sysdb).st_size > 0):
                raise PortfolioException(f'Find sizes about {syndb} and {sysdb} != 0.')
        return None

    def gen_wsrv_name(self, jver: str = None) -> str:
        '''
        :param jver: the jar version, default this synodepy3's (__version__.jar_ver); an upgrade
                     takes the new package's, bin/resource.json versions.jar_ver.
        '''
        return f'Synode-{jver or jar_ver}-{self.registry.config.synid}'

    def gen_html_srvname(self, wver: str = None) -> str:
        '''
        :param wver: the web version, default this synodepy3's (__version__.web_ver); an upgrade
                     takes the new package's, bin/resource.json versions.web_ver.
        '''
        return f'Synode.web-{wver or web_ver}-{self.registry.config.synid}'

    def update_domain(self, orgtype,
                      orgid: Optional[str]=None,
                      reg_jserv: Optional[str]=None,
                      domain: Optional[str]=None ):
        '''
        update data model
        :param reg_jserv:
        :param orgid:
        :param domain:
        :return:
        '''
        if reg_jserv is not None:
            self.settings.regiserv = reg_jserv
        if orgid is not None:
            self.registry.config.set_org(orgid=orgid, orgtype=orgtype)
        if domain is not None:
            self.registry.config.set_domain(domain)
            for u in self.registry.synusers:
                u.domain = domain

    def updateWithUi(self,
            market: str, org: Optional[str] = None, domain: Optional[str]=None,
            reg_jserv: Optional[str] = None,
            admin: Optional[str]=None, domphrase: Optional[str]=None,
            volume: Optional[str]=None,
            hubmode: Optional[bool]=None,
            jservss: Optional[str]=None, synid: Optional[str]=None,
            reverseProxy=None,
            port: Optional[str]=None, webport: Optional[str]=None,
            proxyPort: Optional[str]=None, proxyIp: Optional[str]=None,
            syncins: Optional[str]=None, envars=None, webProxyPort=None):

        self.update_domain(reg_jserv=reg_jserv, orgtype=market, orgid=org, domain=domain)

        for u in self.registry.synusers:
            if u.userId == admin and admin is not None:
                u.pswd = domphrase
            if domain is not None:
                u.domain = domain
            if org is not None:
                u.org = org

        for p in self.registry.config.peers or []:
            if domain is not None:
                p.domain = domain
            if org is not None:
                p.org = org

        if hubmode is not None:
            self.registry.config.mode = SynodeMode.hub.name if hubmode else SynodeMode.peer.name
        # else:
        #     self.registry.config.mode = None

        if synid is not None:
            self.registry.config.synid = synid
        if reverseProxy is not None:
            self.settings.reverseProxy = reverseProxy
        if proxyIp is not None:
            self.settings.proxyIp = proxyIp
        if proxyPort is not None:
            self.settings.proxyPort = int(proxyPort)
        if webProxyPort is not None:
            self.settings.webProxyPort = int(webProxyPort)

        if not LangExt.isblank(webport):
            self.settings.webport = int(webport) # type: ignore

        if not LangExt.isblank(port):
            self.settings.port = int(port) # type: ignore

        if jservss is not None and len(jservss) > 8:
            jsvkvs = InstallerCli.fromat_jservurl(self.find_hubpeer(), jservss)
            jsvkvs[self.registry.config.synid] = getJservUrl(self.registry.config.https, self.get_iport())
            self.settings.Jservs(jsvkvs)
        else:
            self.settings.Jservs({self.registry.config.synid: getJservUrl(self.registry.config.https, self.get_iport())})

        if envars is None:
            envars = {}

        if volume is not None:
            # MEMO: settings.json's volume MUST be saved as an absolute path.
            #   The synode (Java, AppSettings.checkInstall()) resolves a relative volume against WEB-INF,
            #   while Python (settings.Volume(), the installers and commands.update_srv()) resolves it
            #   against cwd, so they would use different folders. The upgrade (upgrade.exe /
            #   synode-upgrade-srv) refuses a relative volume, see commands.installed_settings_error().
            self.settings.Volume(posix_path(os.path.abspath(volume)))

        if syncins is not None:
            self.registry.config.syncIns = 0.0 if syncins is None or hubmode else float(syncins)

        for k in envars:
            self.settings.envars[k] = envars[k]

        web_settings = os.path.join(web_inf, settings_json)
        if os.path.exists(web_settings):
            try:
                data: AppSettings = cast(AppSettings, Anson.from_file(web_settings))
                self.settings.rootkey, self.settings.installkey = data.rootkey, data.installkey
            except Exception as e:
                Utils.warn("Checking existing runtime settings, settings.json, failed.")
                print(e)

        if not os.path.isdir(web_inf):
            raise PortfolioException(f'Folder {web_inf} dose not exist, or not a folder.')

    def ping(self, jsrv, timeout=10):
        return ping(install_uri, jsrv, timeout_snd=timeout)

    def central_uid(self) -> str:
        """
        The central (registry) user id, configured at build time as tasks.json deploy.centralUid,
        and packaged in desktop/settings/app-settings.json (AnclientSettings.centralUid).
        Falls back to the domain admin, registry.synusers[0], if the desktop is not packaged.
        """
        desk_sets = Path('desktop') / 'settings' / 'app-settings.json'
        if desk_sets.exists():
            try:
                csets = cast(AnclientSettings, Anson.from_file(desk_sets))
                if hasattr(csets, 'centralUid') and not LangExt.isblank(csets.centralUid):
                    return csets.centralUid
            except Exception as e:
                Utils.warn(f'Cannot load centralUid from {desk_sets}: {e}')
        return self.registry.synusers[0].userId

    def update_central_pswd(self, pswd: str):
        """
        Set the password of Central, save it to WEB-INF/settings.json, and drop the central client,
        so the next request logs in with it.
        """
        self.settings.centralPswd = pswd
        self.regclient = None
        self.settings.toFile(cfgpaths.web_settings)

    def check_cent_login(self) -> SessionClient:
        # ISSUE central-login: loginWithUri() makes no login request, see issues/central-client-login.md
        if self.regclient is None or self.regclient.myservRt != self.settings.regiserv:
            self.regclient = SessionClient.loginWithUri(
                uri=install_uri,
                servroot=self.settings.regiserv,
                uid=self.central_uid(),
                pswdPlain=self.settings.centralPswd)
        return self.regclient # type: ignore

    def query_orgs(self) -> Tuple[list[str], str]:
        # 0.7.6
        oid = self.registry.config.org.orgId
        return [oid], oid

    def query_domx(self, market: str, commu: str):
        # self.check_cent_login()
        # return query_domx(client=self.regclient,
        return query_domx(client= self.check_cent_login(),
                          func_uri=install_uri,
                          market=market,
                          commuid=commu)

    def query_domconf(self, commuid: str, domid: str):
        # self.check_cent_login()
        return query_domconfig(client=self.check_cent_login(),
                               func_uri=install_uri,
                               myid=self.registry.config.synid,
                               market=self.settings.market_id, orgid=commuid, domid=domid)

    def register(self):
        '''
        Ask central for registering a domain, expecting a reply with planned synodes (peers).
        :return: RegistResp
        '''
        # self.check_cent_login()

        return register(client=self.check_cent_login(),
                        func_uri=install_uri,
                        market=self.settings.market_id, cfg=self.registry.config,
                        s=self.settings, iport=self.getProxiedIp(), jprotocl=self.reg_jserv.jprotocol)

    def jesuis_hub(self) -> bool:
        """
        I am hub or not
        :return: true or false
        """
        return self.registry.config.mode == SynodeMode.hub.name

    def submit_mysettings(self):
        # self.check_cent_login()
        return submit_settings(client=self.check_cent_login(),
                               func_uri=install_uri, market=self.settings.market_id,
                               cfg=self.registry.config, s=self.settings,
                               iport=self.getProxiedIp(),
                               syn_protocol=self.syn_protocol,
                               # leave the state unchanged when using setup API.
                               # see also java/AppSettings.synotifyCentral()
                               stat=None # type: ignore
                               ) # if not self.jesuis_hub() else RegistResp().Code(MsgCode.ok)

    def install(self):
        """
        Install / setup synodepy3, by moving /update dictionary to vol-path (of AppSettings), settings.json
        to WEB-INF, unzip exiftool.zip, and check bin/jar first.
        Note: this is not installing Windows service.
        :return: None
        """

        cfg = self.registry.config
        self.settings.envars['WEBROOT'] = cfg.synid
        cfg.org.webroot = '$WEBROOT'
        # TODO remove this after verified in GUI version
        for p in cfg.peers:
            # This is a wrong design of redundant data copies, and is bug prone.
            p.org = cfg.org.orgId

        self.check_src_jar_db()

        ########## settings
        self.settings.startHandler = [implISettingsLoaded, f'{album_web_dist}/{web_host_json}']
        print(self.settings.startHandler)

        # Let's wrap into self.settings.Jservs()
        iport_str = f'{self.getProxiedIp()[0]}:{self.getProxiedIp()[1]}'
        myjserv = JServUrl(iport=iport_str, jprotocol=JProtocol(jserv_album))
        self.settings.Jservs({self.registry.config.synid: myjserv.jserv()})

        self.settings.save(cfgpaths.web_settings)

        sysdb, syndb, syntityjson = InstallerCli.sys_syn_db_syntity(self.settings.Volume())

        # web/host.json
        InstallerCli.update_private(self.registry.config, self.settings)
        InstallerCli.update_htmlsrv(self.registry.config, self.settings)

        if not Path.exists(Path(self.settings.Volume())):
            os.mkdir(self.settings.Volume())
        # May 15 2025
        # keep db files, save changes anyway
        # Registry's modification is checked by UI, any cli modification is impossible, except direct editing.
        self.registry.toFile(os.path.join(self.settings.Volume(), dictionary_json))

        if not Path.exists(syndb):
            shutil.copy2(os.path.join("volume", syn_db), syndb)
        if not Path.exists(sysdb):
            shutil.copy2(os.path.join("volume", sys_db), sysdb)
        if not Path.exists(syntityjson):
            shutil.copy2(os.path.join('registry', syntity_json), syntityjson)

        # Prevent deleting tables by JettypApp's checking installation.
        # This is to assume album-jserv always can successfully setup dbs - when db files exist, the tables exist.
        if self.hasrun() and not LangExt.isblank(self.settings.installkey) and LangExt.isblank(
                self.settings.rootkey):
            self.settings.rootkey, self.settings.installkey = self.settings.installkey, None
            Utils.warn(f'Volume is set to {self.settings.Volume()}.\n'
                       f'Ignore existing database:\n{sysdb}\n{syndb}')
            self.settings.toFile(os.path.join(web_inf, settings_json))

        # upgrade.exe, packed as upgrade.pkg (Windows packages only)
        promote_upgrade_pkg()

        if (Path(mypath) / 'desktop').exists():
            self.update_clients([(Path('desktop'), 'settings/app-settings.json')])
        else:
            print(f'*** Updating destop ignored in os type {os.name}')

    def update_clients(self, clients_sets: Iterable[tuple[Path, str]]) -> None:
        for apppath, setpath in clients_sets:
            csets = cast(AnclientSettings, Anson.from_file(apppath / setpath))
            try: print("Update client settings", (apppath / setpath).absolute())
            except: pass

            # ISSUE: package should care only one settings, and installer translates, setups.
            csets.market_id = self.registry.config.org.orgType  # self.settings.market_id
            if hasattr(self.settings, 'market_name'):
                csets.market_name = self.settings.market_name

            csets.org = self.registry.config.org.orgId
            csets.domain = self.registry.config.domain
            csets.centralPswd = self.settings.centralPswd
            csets.regiserv = self.settings.regiserv
            csets.admin = self.registry.config.admin
            csets.domain_token = self.find_synuser(csets.admin).pswd
            print("leave csets.device untouched")

            print("csets/AnclientSettings.__type__", csets.__type__,
                  "DesktopSettings().__type__     ", DesktopSettings().__type__ )
            if csets.__type__ == DesktopSettings().__type__:
                csets = cast(DesktopSettings, csets)
                csets.synode_id = self.registry.config.synid
                csets.synode_jserv = self.find_peer(csets.synode_id).jserv
                csets.synode_vol = str(Path(self.settings.volume).absolute().as_posix())
                csets.album_web = str(self.settings.webport)
                csets.java_path = str(java_cmd().absolute().as_posix())
                csets.wsagent_jar = f'res/ws-agent-{ipcagent_ver}.jar'
            else:
                Utils.warn("**** ERROR ****: Any chances to reach here?")
                Utils.warn("**** ERROR ****: Missed value for saving: {}, {}, {}, ...",
                           self.registry.config.synid, self.settings.volume, java_cmd())

            csets.toFile((apppath / setpath).absolute())
            print("Saved", Path(apppath) / setpath, ":", csets.synode_id, csets.synode_jserv)

    '''
    def clean_install(self, vol: str = None):
        clean = False if self.settings is None or vol is None else os.path.samefile(self.settings.volume, vol)
        if clean:
            print(self.settings.volume)
            try: shutil.rmtree(self.settings.volume)
            except (FileNotFoundError, IOError): pass

        pathsettings = os.path.join(web_inf, settings_json)
        print(pathsettings)
        try: os.remove(pathsettings)
        except (FileNotFoundError, IOError): pass

        for res in glob('exiftool*'):
            print(res)
            if os.path.isdir(res):
                try: shutil.rmtree(res)
                except FileNotFoundError or IOError or OSError: pass
            else:
                try: os.remove(res)
                except FileNotFoundError or IOError or OSError: pass
    '''

    def test_in_term(self):
        system = Utils.get_os()
        jar = os.path.join('bin', jserv_07_jar)
        command = f'java -jar -Dfile.encoding=UTF-8 {jar}'
        if system == "Windows":
            wincmd = ['cmd', '/k', f'chcp 65001 && {command}']
            print(wincmd)
            return subprocess.Popen(wincmd, creationflags=subprocess.CREATE_NEW_CONSOLE)
        elif system == "Linux" or system == "macOS":
            return subprocess.Popen(['gnome-terminal', '--', 'bash', '-c', command],
                                    start_new_session=True)
        else:
            raise PortfolioException(f"Unsupported operating system: {system}")

    @staticmethod
    def closeWeb(httpd, thrd):
        try:
            if httpd is not None: httpd.shutdown()
        except Exception as e:
            print(e)

        try:
            if thrd is not None: thrd.join(5)
        except Exception as e:
            print(e)

    @staticmethod
    def update_private(config: SynodeConfig, settings: AppSettings):
        """
        Update private/host.json/{host: url}.
        Write boilerplate to host.json, not solid values

        Deprected since 0.7.6, this task is moved to jar, and can be causing problem if users
        tried different synode-ids.
        :param config:
        :param settings:
        :return:
        """
        prv_path = os.path.join(album_web_dist, host_private)
        if not os.path.exists(prv_path):
            os.mkdir(prv_path)

        webhost_pth: str = os.path.join(album_web_dist, web_host_json)

        if webhost_pth is not None:
            jsrvhost: str = getJservUrl(config.https, f'%s:{settings.port}')

            hosts: ExternalHosts
            try: hosts = cast(ExternalHosts, Anson.from_file(webhost_pth))
            except: hosts = ExternalHosts()

            hosts.host = config.synid
            hosts.syndomx.update({'domain': config.domain})
            hosts.syndomx.update({config.synid: jsrvhost})
            hosts.toFile(webhost_pth)

    @staticmethod
    def update_htmlsrv(config: SynodeConfig, settings: AppSettings):
        jsn_path = os.path.join(web_inf, html_service_json)

        web_cfg: WebConfig
        try: web_cfg = cast(WebConfig, Anson.from_file(jsn_path))
        except: web_cfg = WebConfig()

        web_cfg.port = settings.webport
        web_cfg.toFile(jsn_path)

    def stop_web(self):
        if self.httpd is not None:
            try:
                self.httpd.shutdown()
                if self.webth is not None:
                    self.webth.join()
            except OSError as e:
                print(e)

    @staticmethod
    def start_web(webport=8900):
        import http.server
        import socketserver
        import threading
    
        # PORT = 8900
        httpdeamon: List[socketserver.TCPServer] = []
    
        # To serve gzip, see
        # https://github.com/ksmith97/GzipSimpleHTTPServer/blob/master/GzipSimpleHTTPServer.py#L244
        class WebHandler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, directory=album_web_dist, **kwargs)
                self.extensions_map.update({".mjs": "text/javascript"})
    
            def send_response(self, code, message=None):
                """Add the response header to the headers buffer and log the
                response code.
    
                Also send two standard headers with the server software
                version and the current date.
    
                """
                self.log_request(code)
                self.send_response_only(code, message)
                self.send_header("server", "Portfolio Synode 0.7/web")
                self.send_header('Date', self.date_time_string())
    
            def end_headers(self):
                self.send_header("Access-Control-Allow-Origin", "*")
                super().end_headers()
    
        def create_server():
            try:
                with socketserver.TCPServer(("", webport), WebHandler) as httpd:
                    print("Starting web at port", webport)
                    try:
                        httpdeamon.insert(0, httpd) #[0] = httpd
                        httpd.serve_forever()
                        Utils.logi(f'Web service at port {webport} stopped.')
                    except OSError:
                        httpdeamon[0] = None
                        httpd.shutdown()
                        Utils.warn("Address already in use? (Possible html-web service is running.)")
            except socket.error as e:
                if e.errno == 98 or e.errno == 48: # errno 98 on Linux, 48 on macOS 
                    print(f"Port {webport} is already in use.")
                else:
                    print(f"An unexpected socket error occurred: {e}")
            except Exception as e:
                print(f"An unexpected error occurred: {e}")

        if not os.path.isdir(album_web_dist):
            raise PortfolioException(f'Cannot find web root folder: {album_web_dist}')
        if not os.path.isfile(os.path.join(album_web_dist, index_html)):
            raise FileNotFoundError(f'Cannot find {index_html} in {album_web_dist}')
    
        thr = threading.Thread(target=create_server)
        thr.start()

        count = 0
        while count < 5 and (len(httpdeamon) == 0 or httpdeamon[0] is None):
            count += 1
            time.sleep(0.2)

        print(httpdeamon[0] if len(httpdeamon) > 0 else 'httpd == None')
        return httpdeamon[0] if len(httpdeamon) > 0 else None, thr

    def after_submit(self, resp):
        '''
        After submit, update jservs to hub, peers and save.
        :param resp:
        :return:
        '''
        self.registry.config.overlay(resp.diction)
        self.settings.acceptj_butme(self.registry.config.synid, self.registry.config.peers)
        self.settings.save(cfgpaths.web_settings)
        self.registry.save(cfgpaths.vol_dict_json)

    def check_install_jre(self, jredownloader: Optional[JreDownloader], prog_label=None,
                          cli_progress: Callable[[int, int, int], None]=None):
        '''
        Check and isntall jre. The JreDownloader will use the proxy if configured in cwd().
        :param jredownloader:
        :param prog_label:
        :param cli_progress:
        :return: return downloader (with background thread) or None if already done
        '''

        if jredownloader and jredownloader.isrunning():
            return

        from semanticshare.io.oz.edge import Temurin17Release

        temurin = Temurin17Release()
        temurin.path = synode_ui.langstr(jre_mirror_key)
        if os.path.exists('proxy.json'):
            temurin.proxy = 'proxy.json'

        jreimg = temurin.set_jre()
        print('JRE:', jreimg)

        if Temurin17Release.guess_jretree(_jre_) != Path(_jre_):
            jredownloader = JreDownloader(prog_label)
            if prog_label:
                jredownloader.start_download_gui(temurin)
            else:
                jredownloader.start_download_cli(temurin, cli_progress)

            return jredownloader

        else: return None

    def isjre_ready(self):
        '''
        Find out is the jre for running available.
        This is different from Jre-Mirror's zip/gz package checking while working as the downloader.
        :return: True if the required jre is ready.
        '''
        try:
            if Temurin17Release.guess_jretree(_jre_) == Path(_jre_):
                return True
            else: return False
        except: return False
