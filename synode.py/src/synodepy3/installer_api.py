# This Python file uses the following encoding: utf-8
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
from socketserver import TCPServer
from typing import cast

from anson.io.odysz.ansons import Anson
from anson.io.odysz.common import Utils, LangExt

from src.io.oz.syntier.serv import ExternalHosts
from src.io.oz.jserv.docs.syn.singleton import PortfolioException,\
    AppSettings, implISettingsLoaded, web_port, webroot, \
    sys_db, syn_db, syntity_json, getJservUrl
from src.io.oz.syn import AnRegistry, SynodeConfig


def ping(peerid: str, peerserv: str):
    pass # raise PortfolioException('TODO')


def decode(warns: bytes):
    lines = []
    if warns is not None:
        for b in warns.split(b'\n'):
            # lines.append(b.decode('utf-8') if isinstance(b, bytes) else b)
            if isinstance(b, bytes):
                try:
                    s = b.decode('utf-8')
                except UnicodeDecodeError:
                    try:
                        s = b.decode('gbk')
                    except UnicodeDecodeError:
                        s = ''.join(chr(int(b)))
                if s is not None:
                    lines.extend(s.split('\n'))
            else:
                lines.append(str(b))
    return lines


def valid_registry(reg: AnRegistry):
    """
    config.synid must presents in peers, which should be more or equals 2 nodes.
    :param reg:
    :return:
    """
    if LangExt.len(reg.config.peers) < 2:
        raise PortfolioException("Shouldn't works at leas 2 peers? (while checking dictionary.json)")
    if AnRegistry.find_synode(reg.config.peers, reg.config.synid) is None:
        raise PortfolioException(
            f'Cannot find peer registry of my id: {reg.config.synid}. (while checking dictionary.json.peers)')


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

host_private = 'private'
web_host_json = f'{host_private}/host.json'

album_web_dist = 'web-dist'

dictionary_json = 'dictionary.json'
settings_json = 'settings.json'
web_inf = 'WEB-INF'
index_html = 'index.html'
# syn_db = 'doc-jserv.db'
# sys_db = 'jserv-main.db'
jserv_07_jar = 'jserv-album-0.7.1.jar'
# doc_jserv_db = 'doc-jserv.db'
# jserv_main_db = 'jserv-main.db'
# syntity_json = 'syntity.json'
exiftool_zip = 'exiftool.zip'
exiftool_v_exe = 'exiftool*.exe'
exiftool_exe = 'exiftool.exe'
exiftool_testver = 'exiftool -ver'


class InstallerCli:
    settings: AppSettings
    registry: AnRegistry

    @staticmethod
    def parsejservstr(jservstr: str):
        """
        :param jservstr: "x:\\turl-1\\ny:..."
        :return: [[x, url-1], ...]
        """
        return [[kv.strip().removesuffix(':') for kv in line.split('\t')] for line in jservstr.split('\n')]

    def fromat_jservstr(jservstr: str):
        return {kv[0]: kv[1] for kv in InstallerCli.parsejservstr(jservstr)}

    def __init__(self):
        self.registry = cast(AnRegistry, None)
        self.settings = cast(AppSettings, None)

        Anson.java_src('src')

    def list_synodes(self):
        return ['list', 'load', 'save', 'print', 'showip', f'vol: {self.settings.volume}']

    def Jservs(self, jsrvs: dict) :
        self.settings.Jservs(jsrvs)
        return self

    def loadInitial(self, res_path: str = None):
        """
        If this is called at the first time (WEB-INF/settings.json[root-key] == null),
        load from res_path/setings.json,
        else load from WEB-INF/settings.json.

        :param res_path: if WEB-INF/settings.json is missing, must provide initial resource's path
        :return: loaded json object
        """

        web_settings = os.path.join(web_inf, settings_json)
        if os.path.exists(web_settings):
            try:
                data: AppSettings = cast(AppSettings, Anson.from_file(web_settings))
                self.settings = data
            except json.JSONDecodeError as e:
                raise PortfolioException(f'Loading Anson data from {web_settings} failed.', e)

            try:
                self.registry = self.loadRegistry(data.Volume())
            except FileNotFoundError or PortfolioException as e:
                Utils.warn(f"Can't find registry configure in {data.Volume()}, replacing with {res_path}")
                self.registry = self.loadRegistry(res_path)

        else:
            if res_path is None:
                raise PortfolioException("WEB-INF/settings.json doesn't exist and the resource path is not specified.")

            res_settings = os.path.join(res_path, settings_json)
            self.registry = self.loadRegistry(res_path)
            self.settings = cast(AppSettings, Anson.from_file(res_settings))

        return self.settings

    @staticmethod
    def loadRegistry(res_path: str):
        """
        :param res_path: if none, load from volume path
        :return: AnRegistry
        """
        diction_json = os.path.join(res_path, dictionary_json)
        registry = AnRegistry.load(diction_json)
        valid_registry(registry)

        return registry

    def isinstalled(self, volpath_ui: str = None):
        return (self.settings is not None and
                (volpath_ui is None and self.validateVol(self.settings.Volume()) is None or
                 volpath_ui is not None and self.validateVol(volpath_ui) is None))

    def hasrun(self, volpath_ui = None):
        volpath = LangExt.ifnull(volpath_ui, self.settings.Volume())
        data = Anson.from_file(volpath_ui) if volpath_ui is not None else self.settings

        psys_db = os.path.join(volpath, sys_db)
        return len(data.rootkey) > 0 and os.path.exists(psys_db) and os.path.getsize(psys_db) > 0

    @staticmethod
    def reportIp() -> str:
        import socket
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            print(ip)
            return ip

    def validateVol(self, volpath: str):
        if volpath is None or len(volpath) == 0:
            return {"volume", "Volume path is empty."}

        if not Path.is_dir(Path(volpath)):
            try:
                os.mkdir(volpath)
                return None
            except FileExistsError:
                return {"volume", f'Directory already exists: {volpath}'}
            except FileNotFoundError:
                return {"volume", f'Parent directory does not exist: {volpath}'}

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
                f'Some initial database or configure files cannot be found in {volume}: {sys_db}, {syn_db}, {syntity_json}')
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
        if (not os.path.isfile(os.path.join('volume', sys_db))
                or not os.path.isfile(os.path.join('volume', syn_db))):
            raise FileNotFoundError(
                f'Some initial database or configure files cannot be found in volume: {sys_db}, {syn_db}')
        return True

    def peers_find(self, id):
        return AnRegistry.find_synode(self.registry.config.peers, id)

    def validate(self, synid: str, volpath: str, peerjservs: str):
        """
        Check synodepy3, volume, jservs.
        :param synid:
        :param volpath:
        :param peerjservs:
        :return: error {name: input-data} if there are errors. Error names: synodepy3 | volume | jserv
        """

        v = self.validateVol(volpath)
        if v is not None:
            return v

        peer_jservss = InstallerCli.parsejservstr(peerjservs)
        for peer in peer_jservss:
            if self.peers_find(peer[0]) is None:
                return {"peers": {peer[0]: LangExt.str(self.registry.config.peers)}}

            if synid != peer[0] and not ping(peer[0], peer[1]):
                return {"jserv": {peer[0]: peer[1]}}

        # check synodepy3 id is domain wide unique
        if synid is None or len(synid) == 0 or synid not in [ln[0] for ln in peer_jservss]:
            return {"synodepy3", synid}

        if not checkinstall_exiftool():
            return {"exiftool": "Check and install exiftool failed!"
                    if Utils.get_os() == 'Windows'
                    else "Please install exiftool and test it's working with command 'exiftool -ver'"}
        return None

    def updateWithUi(self, jservss: str = None, synid: str = None, port: str = None, volume: str = None,
                     syncins: float = None, envars={}):
        if jservss is not None and len(jservss) > 8:
            self.settings.Jservs(InstallerCli.fromat_jservstr(jservss) if isinstance(jservss, str) else jservss)

        if not LangExt.isblank(synid):
            self.registry.config.synid = synid

        if not LangExt.isblank(port):
            self.settings.port = int(port)

        if not LangExt.isblank(volume):
            if not os.path.exists(volume):
                os.mkdir(volume)
            elif not os.path.samefile(volume, self.settings.volume):
                try: self.registry = self.loadRegistry(volume)
                except Exception: pass
            self.settings.Volume(os.path.abspath(volume))

        if syncins is not None:
            self.registry.config.syncIns = float(syncins)

        if port is not None and len(port) > 1:
            InstallerCli.update_private(self.registry.config, self.settings)

        for k in envars:
            self.settings.envars[k] = envars[k]

        web_settings = os.path.join(web_inf, settings_json)
        if os.path.exists(web_settings):
            try:
                data: AppSettings = cast(AppSettings, Anson.from_file(web_settings))
                # self.settings = data
                self.settings.rootkey, self.settings.installkey = data.rootkey, data.installkey
            except Exception as e:
                Utils.warn("Checking existing runtime settings, settings.json, failed.")
                print(e)

        if not os.path.isdir(web_inf):
            raise PortfolioException(f'Folder {web_inf} dose not exist.')

        self.settings.toFile(os.path.join(web_inf, settings_json))

    def install(self, respth: str, volpath: str = None):
        """
        Install / setup synodepy3, by moving /update dictionary to vol-path, settings.json
        to WEB-INF, unzip exiftool.zip, and check bin/jar first.
        Note: this is not installing Windows service.
        :param respth:
        :param volpath:
        :return:
        """

        self.check_src_jar_db()

        ########## settings
        # Update config.WEBROOT_HUB with local IP and port by ui.
        self.settings.envars[webroot] = f'{InstallerCli.reportIp()}:{web_port}'

        # ["io.oz.syntier.serv.WebsrvLocalExposer", "web-dist/private/host.json", "WEBROOT_HUB", "8900"]
        self.settings.startHandler = [implISettingsLoaded,
                                      f'{album_web_dist}/{web_host_json}',
                                      webroot, web_port]
        print(self.settings.startHandler)

        self.settings.toFile(os.path.join(web_inf, settings_json))

        ########## volume
        path_v = Path(LangExt.ifnull(volpath, self.settings.Volume()))

        if not Path.exists(path_v):
            Path.mkdir(path_v)
        elif not Path.is_dir(path_v):
            raise IOError(f'Volume path is not a folder: {path_v}')

        if self.isinstalled(volpath) and self.hasrun(volpath):
            raise PortfolioException(f'Deployed synodepy3 in {path_v} is, or has, already running.')

        self.registry.toFile(os.path.join(path_v, dictionary_json))

        v_jservdb_pth = os.path.join(path_v, syn_db)
        v_main_db_pth = os.path.join(path_v, sys_db)
        v_syntity_pth = os.path.join(path_v, syntity_json)

        if not Path.exists(Path(v_jservdb_pth)) and not Path.exists(Path(v_main_db_pth)):
            shutil.copy2(os.path.join(respth if LangExt.isblank(respth) is not None else '.', syntity_json),
                         v_syntity_pth)
            shutil.copy2(os.path.join("volume", syn_db), v_jservdb_pth)
            shutil.copy2(os.path.join("volume", sys_db), v_main_db_pth)
        else:
            # Prevent deleting tables by JettypApp's checking installation.
            if not LangExt.isblank(self.settings.installkey) and LangExt.isblank(self.settings.rootkey):
                self.settings.rootkey, self.settings.installkey = self.settings.installkey, None
            Utils.warn(f'volume is set to {path_v}.\nignoring existing database:\n{v_main_db_pth}\n{v_jservdb_pth}')
            self.settings.toFile(os.path.join(web_inf, settings_json))

        # web/host.json
        InstallerCli.update_private(self.registry.config, self.settings)

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
        self.updateWithUi(syncins=0, envars={webroot: f'{InstallerCli.reportIp()}:{web_port}'})

        system = Utils.get_os()
        jar = os.path.join('bin', jserv_07_jar)
        command = f'java -jar -Dfile.encoding=UTF-8 {jar}'
        if system == "Windows":
            return subprocess.Popen(['cmd', '/k', f'chcp 65001 && {command}'],
                                    creationflags=subprocess.CREATE_NEW_CONSOLE)
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
        :param jservport:
        :param config:
        :return:
        """
        prv_path = os.path.join(album_web_dist, host_private)
        if not os.path.exists(prv_path):
            os.mkdir(prv_path)

        webhost_pth: str = os.path.join(album_web_dist, web_host_json)

        if webhost_pth is not None:
            # ip = InstallerCli.reportIp()
            jsrvhost: str = getJservUrl(config.https, f'%s:{settings.port}')

            hosts: ExternalHosts
            try: hosts = cast(ExternalHosts, Anson.from_file(webhost_pth))
            except: hosts = ExternalHosts()

            hosts.host = config.synid
            # hosts.localip = ip
            hosts.syndomx.update({'domain': config.domain})

            for sid, jurl in settings.jservs.items():
                if sid == config.synid:
                    hosts.syndomx.update({sid: jsrvhost})
                else:
                    hosts.syndomx.update({sid: jurl})

            hosts.toFile(webhost_pth)

    @staticmethod
    def stop_web(httpd: TCPServer):
        if httpd is not None:
            try:
                httpd.shutdown()
            except OSError as e:
                print(e)

    def start_web(self, webport=8900, jservport=8964):
        import http.server
        import socketserver
        import threading
    
        # PORT = 8900
        httpdeamon: [socketserver.TCPServer] = [None]
    
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
                # self.send_header('Server', self.version_string())
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
                        httpdeamon[0] = httpd
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
        while count < 5 and httpdeamon[0] is None:
            count += 1
            time.sleep(0.2)
    
        print(httpdeamon[0])
        return httpdeamon[0], thr
