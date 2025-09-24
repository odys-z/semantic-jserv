# This Python file uses the following encoding: utf-8
import datetime
import sys
from dataclasses import dataclass

from semanticshare.io.oz.syn import SynodeMode, Synode

from . import SynodeUi

sys.stdout.reconfigure(encoding="utf-8")

import ipaddress
import json
import os
import re
import shutil
import socket
import subprocess
import time
import zipfile
from glob import glob
from pathlib import Path
from typing import cast, Optional

from anson.io.odysz.anson import Anson, AnsonException
from anson.io.odysz.common import Utils, LangExt

from semanticshare.io.oz.srv import WebConfig

from semanticshare.io.odysz.semantic.jprotocol import MsgCode, AnsonMsg, JProtocol
from semanticshare.io.oz.syntier.serv import ExternalHosts
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException,\
    AppSettings, implISettingsLoaded, \
    sys_db, syn_db, syntity_json, getJservUrl, valid_url_port
from semanticshare.io.oz.syn.registry import AnRegistry, SynodeConfig, RegistReq, \
    Centralport, RegistResp, SynOrg, CynodeStats

from anclient.io.odysz.jclient import Clients, OnError, SessionClient

from .__version__ import jar_ver, web_ver, html_srver

path = os.path.dirname(__file__)
synode_ui = cast(SynodeUi, Anson.from_file(os.path.join(path, "synode.json")))
err_uihandlers: list[Optional[OnError]] = [None]

def ping(clientUri: str, peerserv: str, timeout_snd: int = 10):
    Clients.init(jserv=peerserv or f'http://127.0.0.1:8964/{JProtocol.urlroot}', timeout=timeout_snd)

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


def query_domx(client: SessionClient, func_uri: str, market: str, commuid: str):
    req = RegistReq(RegistReq.A.queryDomx, market=market)
    req.Uri(func_uri)
    req.market = market

    org = SynOrg()
    org.orgId = commuid
    req.dictionary(SynodeConfig(org=org))

    msg = AnsonMsg(Centralport.register).Body(req)

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
    msg = AnsonMsg(Centralport.register).Body(req)

    resp = client.commit(msg, err_uihandlers[0])

    if resp is not None:
        print(client.myservRt, resp.code)
        print(f'<{RegistReq.A.queryDomConfig}>', resp.toBlock())

    return cast(RegistResp, resp)


def register(client: SessionClient, func_uri: str, market: str, cfg: SynodeConfig, settings: AppSettings, iport: tuple[str, int]):
    req = RegistReq(RegistReq.A.registDom, market)
    req.Uri(func_uri).dictionary(cfg).jserurl(cfg.https, iport=iport)
    msg = AnsonMsg(Centralport.register).Body(req)

    resp = client.commit(msg, err_uihandlers[0])

    if resp is not None:
        print(client.myservRt, resp.code)
        print(f'<{RegistReq.A.registDom}>', resp.toBlock())

    return cast(RegistResp, resp)


def submit_settings(client: SessionClient, func_uri: str, market: str,
                    cfg: SynodeConfig, sets: AppSettings, iport: tuple[str, int],
                    utc: str='now',
                    stat: str = CynodeStats.create):
    req = RegistReq(RegistReq.A.submitSettings, market)\
        .Uri(func_uri)\
        .protocol_path(JProtocol.urlroot)\
        .jserurl(cfg.https, iport)\
        .Jservtime(datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%d %H:%M:%S') if utc == 'now' else utc)\
        .dictionary(cfg)

    msg = AnsonMsg(Centralport.register).Body(req)

    resp = client.commit(msg, err_uihandlers[0])
    if resp is not None:
        print(client.myservRt, resp.code)
        print(f'<{RegistReq.A.submitSettings}>', resp.toBlock())

    return cast(RegistResp, resp)


def decode(warns: bytes):
    lines = []
    if warns is not None:
        for l in warns.split(b'\n'):
            if isinstance(l, bytes):
                try:
                    s = l.decode('utf-8')
                except UnicodeDecodeError:
                    try:
                        s = l.decode('gbk')
                    except UnicodeDecodeError:
                        s = ''.join(str(l))
                if s is not None:
                    lines.extend(s.split('\n'))
            else:
                lines.append(str(l))
    return lines

def valid_local_reg(reg: AnRegistry):
    """
    config.synid must presents in peers, which should be more or equals 2 nodes.
    :param reg:
    :return:
    """
    # if LangExt.len(reg.config.peers) < 2:
    #     raise PortfolioException("Shouldn't work on at leas 2 peers? (while checking dictionary.json)")
    # if AnRegistry.find_synode(reg.config.peers, reg.config.synid) is None:
    #     raise PortfolioException(
    #         f'Cannot find peer registry of my id: {reg.config.synid}. (while checking dictionary.json.peers)')
    pass

def unzip_file(zip_filepath, extract_to_path):
    """
    Unzips a file to a specified directory.

    Args:
        zip_filepath (str): The path to the zip file.
        extract_to_path (str): The directory to extract the contents to.
    """
    try:
        with zipfile.ZipFile(zip_filepath, 'r') as zip_ref:
            zip_ref.extractall(extract_to_path)
        print(f"Successfully extracted '{zip_filepath}' to '{extract_to_path}'")
    except FileNotFoundError:
        print(f"Error: Zip file '{zip_filepath}' not found.")
    except zipfile.BadZipFile:
        print(f"Error: '{zip_filepath}' is not a valid zip file.")
    except Exception as e:
        print(f"An error occurred: {e}")


def get_zipath(zipath):
    if os.path.isfile(zipath):
        with zipfile.ZipFile(zipath, 'r') as zip_ref:
            for subf in zip_ref.infolist():
                if not subf.is_dir():
                    raise PortfolioException('Not a recognized zip file structure in: {zippath}')
                return subf.filename
    return None


def install_exiftool_win():
    """
    If in Windows, unzip exiftool-13.21_64 and move to root path
    :return: None
    """

    zips = glob(os.path.join('bin', exiftool_zip))
    if len(zips) > 0:
        xtract_files = glob(exiftool_v_exe)
        if len(xtract_files) == 0:
            subfolder = get_zipath(zips[-1])
            unzip_file(zips[-1], '.')
            for res in glob(os.path.join(subfolder, '*')):
                print(res)
                if re.match(f'{subfolder.removesuffix("/")}.exiftool.*', res):
                    # print(res)
                    shutil.move(res, '.')

            try: os.remove(exiftool_exe)
            except FileNotFoundError: pass

            xtract_files = glob(exiftool_v_exe)
            os.rename(xtract_files[0], exiftool_exe)


def check_exiftool():
    if Utils.iswindows():
        return os.path.isfile(exiftool_exe)
    else:
        p = subprocess.Popen(['exiftool', '-ver'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = p.communicate()
        if len(err) > 0:
            Utils.warn(decode(err))
            return False
        print(decode(out))
        return True


def checkinstall_exiftool():
    check = check_exiftool()

    if Utils.iswindows() and not check:
        install_exiftool_win()
        return True
    else:
        return check


"""
    Suppose there are both github/Anclient & github/semantic-jserv,
    then in semantic-jserv/synode.py3:
    ln -s ../../Anclient/examples/example.js/album/web-0.4 web-dist
"""

JProtocol.urlroot = 'jserv-album'

install_uri = 'Anson.py3/test'

#### section will be moved to synode.json ####
host_private = 'private'
mode_hub = 'hub'
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
exiftool_zip = 'exiftool.zip'
exiftool_v_exe = 'exiftool*.exe'
exiftool_exe = 'exiftool.exe'
exiftool_testver = 'exiftool -ver'

pswd_min, pswd_max = 8, 32

class InstallerCli:
    regclient: Optional[SessionClient]
    settings: AppSettings
    registry: AnRegistry

    @staticmethod
    def parsejservstr(jservstr: str) -> list[list[str]]:
        """
        :param jservstr: "x:\\turl-1\\ny:..."
        :return: [[x, url-1], ...]
        """
        return [[kv.strip().removesuffix(':') for kv in line.split('\t')] for line in jservstr.split('\n')]

    def fromat_jservstr_deprecated(jservstr: str):
        return {kv[0]: kv[1] for kv in InstallerCli.parsejservstr(jservstr)}

    def fromat_jservurl(hub: Synode, jservstr: str):
        return {None if hub is None else hub.synid: kv[-1] for kv in InstallerCli.parsejservstr(jservstr)}

    def __init__(self):
        self.regclient = None
        self.httpd = None
        self.webth = None
        self.registry = cast(AnRegistry, None)
        self.settings = cast(AppSettings, None)

    def list_synodes(self):
        return self.settings.jservs.items()

    def Jservs(self, jsrvs: dict) :
        self.settings.Jservs(jsrvs)
        return self

    def load_settings(self):
        """
        Load from res_path/setings.json,
        :return: loaded json object
        """

        web_settings = os.path.join(web_inf, settings_json)
        print("Loading", web_settings)
        if os.path.exists(web_settings):
            try:
                data: AppSettings = cast(AppSettings, Anson.from_file(web_settings))
                data.json = web_settings
                self.settings = data
            except json.JSONDecodeError as e:
                raise PortfolioException(f'Loading Anson data from {web_settings} failed.', e)

            print("Loading registry in", '[registry]')
            self.registry = self.loadRegistry(data.volume, registry_dir)

        else:
            raise PortfolioException("Cannot find settings.json!")

        return self.settings

    @staticmethod
    def loadRegistry(vol_path, deflt_path):
        """
        Load local dictionary.json.
        :param vol_path: if none, load from default path
        :param deflt_path
        :return: AnRegistry
        """
        dir_dict_json = None
        if vol_path is not None:
            dir_dict_json = os.path.join(vol_path, dictionary_json)

        if vol_path is not None and os.path.isdir(vol_path) and Path(dir_dict_json).is_file():
            registry = AnRegistry.load(dir_dict_json)
        else:
            diction_json = os.path.join(deflt_path, dictionary_json)
            registry = AnRegistry.load(diction_json)

        valid_local_reg(registry)
        return registry

    def is_peers_valid(self):
        '''
        There are peers in config.peers[], and domain ids are none or match config.domain
        :return:
        '''
        peers = self.registry.config.peers
        if LangExt.len(peers) > 0:
            for p in peers:
                if p.domain is not None and p.domain != self.registry.config.domain:
                    return False
            return True
        else: return False

    def isinstalled(self):
        return self.validateVol() is None

    def hasrun(self):
        try:
            psys_db, psyn_db, _ = InstallerCli.sys_syn_db_syntity(self.settings.Volume())
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
            raise IOError(f'Volume path is not a folder: {path_v}')

        return Path(os.path.join(path_v, sys_db)), Path(os.path.join(path_v, syn_db)), Path(os.path.join(path_v, syntity_json))

    @staticmethod
    def reportIp() -> str:
        import socket
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            print(ip)
            return ip

    def getProxiedIp(self):
        ip, port = InstallerCli.reportIp(), self.settings.port
        if self.settings.reverseProxy:
            ip, port = self.settings.proxyIp, self.settings.proxyPort
        return ip, port

    def get_iport(self):
        ip, port = self.getProxiedIp()
        return f'{ip}:{port}'

    def validate_domain(self):
        cfg = self.registry.config
        try: LangExt.only_id_len(cfg.domain, minlen=2, maxlen=12)
        except AnsonException as e:
            return {"domain length", f"2 <= Len('{cfg.domain}') <= 12"}

        try: LangExt.only_id_len(cfg.org.orgId, minlen=2, maxlen=12)
        except AnsonException as e:
            return {"community length", f"2 <= Len('{cfg.org.orgId}') <= 12"}
        return None

    def validateVol(self):
        """
        :return: error (invalid)
        """
        volp = self.settings.Volume()
        if volp is None or len(volp) == 0:
            return {"volume", "Volume path is empty."}

        if not Path.is_dir(Path(volp)):
            try:
                os.mkdir(volp)
                return None
            except FileExistsError:
                return {"volume", f'Directory already exists: {volp}'}
            except FileNotFoundError:
                return {"volume", f'Parent directory does not exist: {volp}'}

    def validate_iport(self):
        try:
            if self.settings.reverseProxy:
                if not ipaddress.ip_address(self.settings.proxyIp):
                    return {f'IP address is not valid: {self.settings.proxyIp}' }
                if not valid_url_port(self.settings.proxyPort) or not valid_url_port(self.settings.webProxyPort):
                    return {f'Proxy port must greater than 1024: {self.settings.port}' }

            if not valid_url_port(self.settings.port) or not valid_url_port(self.settings.webport):
                return {f'Port must greater than 1024: {self.settings.port}' }

        except ValueError as e:
            return str(e)

    def check_installed_jar_db(self):
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
            raise FileNotFoundError(f'Synode service package is missing: {p_jar}')

        p_jar = os.path.join('bin/', html_web_jar)
        if not os.path.isfile(p_jar):
            raise FileNotFoundError(f'Synode html server package is missing: {p_jar}')

        if (not os.path.isfile(os.path.join('volume', sys_db))
                or not os.path.isfile(os.path.join('volume', syn_db))):
            raise FileNotFoundError(
                f'Some initial database or configure files cannot be found in volume: {sys_db}, {syn_db}')
        return True

    def find_hubpeer(self):
        return self.registry.find_hubpeer()

    def find_peer(self, pid: str):
        return AnRegistry.find_synode(self.registry.config.peers, pid)

    def find_synuser(self, uid: str):
        return AnRegistry.find_synuser(self.registry.synusers, uid)

    def validate(self):
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

        v = self.validate_domain()
        if v is not None: return v

        p = self.validatePswdOf(cfg.admin)
        if p is not None: return p

        v = self.validateVol()
        if v is not None: return v

        v = self.validate_iport()
        if v is not None: return v

        # TODO 0.7.8 Checking synode-id is domain wide unique, in Central.
        if self.find_peer(self.registry.config.synid) is None:
            return {'peer-id': f'synode id is not a peer id: {self.registry.config.synid}'}

        if cfg.synid not in self.settings.jservs:
            return {'synode-id': f'synode id is not a service node: {self.registry.config.synid}'}

        if not checkinstall_exiftool():
            return {"exiftool": "Check and install exiftool failed!" \
                    if Utils.get_os() == 'Windows' \
                    else "Please install exiftool and test it's working with command 'exiftool -ver'"}

        # 0.7.6
        self.settings.envars['WEBROOT'] = self.registry.config.synid
        cfg.org.webroot = '$WEBROOT'

        if cfg.mode != SynodeMode.hub.name:
            self.ping(self.settings.jservs[cfg.peers[0].synid])

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
                os.path.isfile(sysdb) and os.stat(syndb).st_size == 0:

                self.settings.installkey, self.settings.rootkey = self.settings.rootkey, ''
                self.settings.toFile(os.path.join(web_inf, settings_json))
                return f'Fixed errors: {sysdb} size & {syndb} size = 0, reset flags for setup db.'
            elif os.path.isfile(sysdb) and os.path.isfile(sysdb) and (os.stat(syndb).st_atime > 0 or os.stat(sysdb).st_size > 0):
                raise PortfolioException(f'Find sizes about {syndb} and {sysdb} != 0.')
        return None

    def gen_wsrv_name(self):
        return f'Synode-{jar_ver}-{self.registry.config.synid}'

    def gen_html_srvname(self):
        return f'Synode.web-{web_ver}-{self.registry.config.synid}'

    def update_domain(self, reg_jserv: str, orgid: str, domain: str):
        self.settings.regiserv = reg_jserv
        self.registry.config.org.orgId = orgid
        self.registry.config.domain = domain

    def updateWithUi(self,
                reg_jserv: str,
                admin: str, pswd: str,
                domphrase: str, org: str, domain: str,
                hubmode: bool = True,
                jservss: str = None, synid: str = None,
                reverseProxy=False,
                port: str = None, webport: str = None,
                proxyPort: str = None, proxyIp: str = None,
                volume: str = None,
                syncins: str = None, envars=None, webProxyPort=None):

        self.update_domain(reg_jserv=reg_jserv, orgid=org, domain=domain)

        for u in self.registry.synusers:
            if u.userId == admin:
                u.pswd = domphrase
            u.domain = domain
            u.org = org

        for p in self.registry.config.peers:
            p.domain = domain
            p.org = org

        if hubmode:
            self.registry.config.mode = mode_hub
        else:
            self.registry.config.mode = None

        self.registry.config.synid = synid
        self.settings.reverseProxy = reverseProxy
        self.settings.proxyIp = proxyIp
        self.settings.proxyPort = int(proxyPort)
        self.settings.webProxyPort = int(webProxyPort)

        if not LangExt.isblank(webport):
            self.settings.webport = int(webport)

        if not LangExt.isblank(port):
            self.settings.port = int(port)

        if jservss is not None and len(jservss) > 8:
            # jsvkvs = InstallerCli.fromat_jservstr(jservss)
            jsvkvs = InstallerCli.fromat_jservurl(self.find_hubpeer(), jservss)
            jsvkvs[self.registry.config.synid] = getJservUrl(self.registry.config.https, self.get_iport())
            self.settings.Jservs(jsvkvs)
        else:
            self.settings.Jservs({self.registry.config.synid: getJservUrl(self.registry.config.https, self.get_iport())})

        if envars is None:
            envars = {}

        self.settings.Volume(os.path.abspath(volume))

        if syncins is not None:
            self.registry.config.syncIns = 0.0 if syncins is None or hubmode else float(syncins)

        # 0.7.6
        # if port is not None and len(port) > 1:
        #     InstallerCli.update_private(self.registry.config, self.settings)

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

    def ping(self, jsrv, timeout=20):
        return ping(install_uri, jsrv, timeout_snd=timeout)

    def check_cent_login(self):
        if self.regclient is None or self.regclient.myservRt != self.settings.regiserv:
            self.regclient = SessionClient.loginWithUri(
                uri=install_uri,
                servroot=self.settings.regiserv,
                uid=self.registry.synusers[0].userId,
                pswdPlain=self.registry.synusers[0].pswd)
        return self.regclient

    def query_domx(self, market, commu):
        self.check_cent_login()
        return query_domx(client=self.regclient,
                          func_uri=install_uri,
                          market=market,
                          commuid=commu)

    def query_domconf(self, commuid: str, domid: str):
        self.check_cent_login()
        return query_domconfig(client=self.regclient, func_uri=install_uri,
                               myid=self.registry.config.synid,
                               market=synode_ui.market_id, orgid=commuid, domid=domid)

    def register(self):
        self.check_cent_login()

        return register(client=self.regclient, func_uri=install_uri,
                        market=synode_ui.market_id, cfg=self.registry.config,
                        settings=self.settings, iport=self.getProxiedIp())

    def jesuis_hub(self):
        return self.registry.config.mode == SynodeMode.hub.name

    def submit_mysettings(self):
        self.check_cent_login()

        return submit_settings(client=self.regclient,
                               func_uri=install_uri, market=synode_ui.market_id,
                               cfg=self.registry.config, sets=self.settings,
                               iport=self.getProxiedIp()
                ) # if not self.jesuis_hub() else RegistResp().Code(MsgCode.ok)

    def install(self):
        """
        Install / setup synodepy3, by moving /update dictionary to vol-path (of AppSettings), settings.json
        to WEB-INF, unzip exiftool.zip, and check bin/jar first.
        Note: this is not installing Windows service.
        :return: None
        """

        self.check_src_jar_db()

        ########## settings
        self.settings.startHandler = [implISettingsLoaded, f'{album_web_dist}/{web_host_json}']
        print(self.settings.startHandler)

        self.settings.save()

        sysdb, syndb, syntityjson = InstallerCli.sys_syn_db_syntity(self.settings.Volume())

        # web/host.json
        InstallerCli.update_private(self.registry.config, self.settings)
        InstallerCli.update_htmlsrv(self.registry.config, self.settings)

        # May 15 2025
        # keep db files, save changes anyway
        # Registry's modification is checked by UI, any cli modification is impossible, except direct editing.
        self.registry.toFile(os.path.join(self.settings.Volume(), dictionary_json))

        if not Path.exists(Path(self.settings.Volume())):
            os.mkdir(self.settings.Volume())
        if not Path.exists(syndb):
            shutil.copy2(os.path.join("volume", syn_db), syndb)
        if not Path.exists(sysdb):
            shutil.copy2(os.path.join("volume", sys_db), sysdb)
        if not Path.exists(syntityjson):
            shutil.copy2(os.path.join('registry', syntity_json), syntityjson)

        # Prevent deleting tables by JettypApp's checking installation.
        # This is assuming album-jserv always successfully setup dbs - when db files exist, the tables exist.
        if self.hasrun() and not LangExt.isblank(self.settings.installkey) and LangExt.isblank(
                self.settings.rootkey):
            self.settings.rootkey, self.settings.installkey = self.settings.installkey, None
            Utils.warn(f'Volume is set to {self.settings.Volume()}.\n'
                   f'Ignore existing database:\n{sysdb}\n{syndb}')
            self.settings.toFile(os.path.join(web_inf, settings_json))

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
            # for sid, jurl in settings.jservs.items():
            #     if sid == config.synid:
            #         hosts.syndomx.update({sid: jsrvhost})
            #     else:
            #         hosts.syndomx.update({sid: jurl})

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
        httpdeamon: [socketserver.TCPServer] = []
    
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

