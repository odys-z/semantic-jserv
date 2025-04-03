# This Python file uses the following encoding: utf-8
import json
import os
import re
import shutil
import subprocess
import threading
import time
import zipfile
from glob import glob
from pathlib import Path

import psutil
from anson.io.odysz.ansons import Anson
from anson.io.odysz.common import Utils, LangExt

import sys


from src.io.oz.jserv.docs.syn.singleton import PortfolioException, AppSettings
from src.io.oz.syn import AnRegistry


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
                        s = ''.join(chr, b)
                if s is not None:
                    lines.extend(s.split('\n'))
            else:
                lines.append(str(b))
    return lines


def get_os():
    """
    :return: Windows | Linux | macOS
    """
    if os.name == 'nt':
        return 'Windows'
    elif os.name == 'posix':
        if sys.platform.startswith('linux') or sys.platform.startswith('freebsd'):
          return 'Linux'
        elif sys.platform.startswith('darwin'):
            return 'macOS'
    return 'Unknown'


def iswindows():
    return get_os() == 'Windows'


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
    if iswindows():
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

    if iswindows() and not check:
        install_exiftool_win()
        return True
    else:
        return check


web_port = 8900
webroot = 'WEBROOT_HUB'

"""
    Suppose there are both github/Anclient & github/semantic-jserv,
    then in semantic-jserv/synode.py3:
    ln -s ../../Anclient/examples/example.js/album/web-0.4 web-dist
"""

host_private = 'private'
host_json = f'{host_private}/host.json'

album_web_dist = 'web-dist'

dictionary_json = 'dictionary.json'
settings_json = 'settings.json'
web_inf = 'WEB-INF'
index_html = 'index.html'
syn_db = 'doc-jserv.db'
sys_db = 'jserv-main.db'
jserv_07_jar = 'jserv-album-0.7.1.jar'
doc_jserv_db = 'doc-jserv.db'
jserv_main_db = 'jserv-main.db'
syntity_json = 'syntity.json'
exiftool_zip = 'exiftool-*.zip'
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
        self.registry = None
        self.settings = None

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
                data: AppSettings = Anson.from_file(web_settings)
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
            self.settings = Anson.from_file(res_settings)

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
                # and LangExt.len(self.settings.rootkey) > 0)

    def hasrun(self, volpath_ui = None):
        volpath = LangExt.ifnull(volpath_ui, self.settings.Volume())
        # data = self.loadSettings(volpath_ui) if volpath_ui is not None else self.settings
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
        if iswindows() and not os.path.isfile(exiftool_exe):
            raise FileNotFoundError(f'Depending tool, exiftool is missing: {exiftool_exe}')
        else:
            Utils.warn('TODO: check exiftool on {0}', get_os())

        volume = self.settings.Volume()
        if (not os.path.isfile(os.path.join(volume, jserv_main_db))
                or not os.path.isfile(os.path.join(volume, doc_jserv_db))
                or not os.path.isfile(os.path.join(volume, syntity_json))):
            raise FileNotFoundError(
                f'Some initial database or configure files cannot be found in {volume}: {jserv_main_db}, {doc_jserv_db}, {syntity_json}')
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
        if (not os.path.isfile(os.path.join('volume', jserv_main_db))
                or not os.path.isfile(os.path.join('volume', doc_jserv_db))):
            raise FileNotFoundError(
                f'Some initial database or configure files cannot be found in volume: {jserv_main_db}, {doc_jserv_db}')
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
                    if get_os() == 'Windows'
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
            InstallerCli.update_private(int(port))

        for k in envars:
            self.settings.envars[k] = envars[k]

        web_settings = os.path.join(web_inf, settings_json)
        if os.path.exists(web_settings):
            try:
                data: AppSettings = Anson.from_file(web_settings)
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
        Install synodepy3, by moving /update dictionary to vol-path, settings.json
        to WEB-INF, and check bin/jar first.
        Note: this is not installing Windows service.
        :param respth:
        :param volpath:
        :return:
        """
        self.check_src_jar_db()

        # volume
        path_v = Path(LangExt.ifnull(volpath, self.settings.Volume()))

        if not Path.exists(path_v):
            Path.mkdir(path_v)
        elif not Path.is_dir(path_v):
            raise IOError(f'Volume path is not a folder: {path_v}')

        if self.isinstalled(volpath) and self.hasrun(volpath):
            raise PortfolioException(f'Deployed synodepy3 in {path_v} is, or has, already running.')

        self.registry.toFile(os.path.join(path_v, dictionary_json))

        v_jservdb_pth = os.path.join(path_v, doc_jserv_db)
        v_main_db_pth = os.path.join(path_v, jserv_main_db)
        v_syntity_pth = os.path.join(path_v, syntity_json)

        if not Path.exists(Path(v_jservdb_pth)) and not Path.exists(Path(v_main_db_pth)):
            shutil.copy2(os.path.join(respth if LangExt.isblank(respth) is not None else '.', syntity_json),
                         v_syntity_pth)
            shutil.copy2(os.path.join("volume", doc_jserv_db), v_jservdb_pth)
            shutil.copy2(os.path.join("volume", jserv_main_db), v_main_db_pth)
        else:
            # Prevent deleting tables by JettypApp's checking installation.
            if not LangExt.isblank(self.settings.installkey) and LangExt.isblank(self.settings.rootkey):
                self.settings.rootkey, self.settings.installkey = self.settings.installkey, None
            Utils.warn(f'volume is set to {path_v}.\nignoring existing database:\n{v_main_db_pth}\n{v_jservdb_pth}')

        # Update config.WEBROOT_HUB with local IP and port by ui.
        self.settings.envars[webroot] = f'{InstallerCli.reportIp()}:{web_port}'
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

    def test_run(self) -> None:
        """
        @deprecated
        """
        sins = self.registry.config.syncIns
        volume = self.settings.Volume()
        self.registry.toFile(os.path.join(volume, dictionary_json))
        try:
            self.test_in_term()
        except Exception as e:
            print(e)
            raise PortfolioException("Starting local synodepy3 failed.", e)
        finally:
            self.registry.config.syncIns = sins
            self.registry.toFile(os.path.join(volume, dictionary_json))

    def test_in_term(self):
        self.updateWithUi(syncins=0, envars={webroot: f'{InstallerCli.reportIp()}:{web_port}'})

        system = get_os()
        jar = os.path.join('bin', jserv_07_jar)
        command = f'java -jar {jar}'
        if system == "Windows":
            return subprocess.Popen(['cmd', '/k', command], creationflags=subprocess.CREATE_NEW_CONSOLE)
        elif system == "Linux" or system == "macOS":
            return subprocess.Popen(['gnome-terminal', '--', 'bash', '-c', command], start_new_session=True)
        else:
            raise PortfolioException(f"Unsupported operating system: {system}")

    @staticmethod
    def closeWeb(httpd):
        try:
            httpd.shutdown()
        except Exception as e:
            print(e)

    @staticmethod
    def update_private(jservport: int):
        """
        Update private/host.json/{host: url}
        :param jservport:
        :return:
        """
        prv_path = os.path.join(album_web_dist, host_private)
        if not os.path.exists(prv_path):
            os.mkdir(prv_path)

        host_path = os.path.join(album_web_dist, host_json)
        try: os.remove(host_path)
        except FileNotFoundError: pass

        with open(host_path, "w") as file:
            file.write(f'{{"host": "http://{InstallerCli.reportIp()}:{jservport}/jserv-album"}}')

    @staticmethod
    def start_web(webport=8900, jservport=8964):
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
            with socketserver.TCPServer(("", webport), WebHandler) as httpd:
                httpdeamon[0] = httpd
                print("Starting web at port", webport)
                httpd.serve_forever()
    
        if not os.path.isdir(album_web_dist):
            raise PortfolioException(f'Cannot find web root folder: {album_web_dist}')
        if not os.path.isfile(os.path.join(album_web_dist, index_html)):
            raise FileNotFoundError(f'Cannot find {index_html} in {album_web_dist}')
    
        InstallerCli.update_private(jservport)
    
        thr = threading.Thread(target=create_server)
        thr.start()
    
        count = 0
        while count < 5 and httpdeamon[0] is None:
            count += 1
            time.sleep(0.2)
    
        print(httpdeamon[0])
        return httpdeamon[0]

    def runjserv_deprecated(self) -> subprocess.Popen:
        """
        @deprecated
        This method is used for testing running jserv without in a terminal.
        The problem is that the service process is difficult to manage, and
        anti user's intuition.
        :return: the process
        """

        self.check_installed_jar_db()
        self.isinstalled()

        # The Google SearchLab AI says:
        # The java -version command, by design, outputs its version information to
        # the standard error stream (stderr), not to standard output (stdout).
        # And ['java', '-version'] is not working.
        proc = subprocess.Popen(['java', '-version'], stderr=subprocess.PIPE)
        warns = decode(proc.communicate()[0])
        if (warns is not None):
            print(warns)

        jar = os.path.join('bin', jserv_07_jar)
        Utils.logi('Jar path: {}', jar)

        if not os.path.isfile(jar):
            raise FileNotFoundError(f'Java file is missing: {jar}',)

        proc = subprocess.Popen(
            # jserv_07_jar='jserv-album-0.7.0.jar'
            f'java -jar bin/{jserv_07_jar}',
            # debug: deadlock randomly? 'java -Dfile.encoding=UTF-8 -jar bin/jserv-album-0.7.0.jar',
            # also work: f'java -Dfile.encoding=UTF-8 -jar {jar}',
            # doesn't work:['java', '-Dfile.encoding=UTF-8', f'-jar {jar}'],
            # doesn't work: ['java', '-Dfile.encoding=UTF-8', '-jar', jar],
            shell=True, stdin=subprocess.PIPE, stderr=subprocess.PIPE)

        return proc

    def stop_test(self, proc: subprocess.Popen) -> [str]:
        """
        @deprecated
        Stop jetty server.

        NOTE
        ====

        Popen.communicat() won't work, which will read and terminate the process, leading halt as the java process is
        running endlessly and no ETX char can be returned while reading stderr.

        P = Popen()
        p.communicate() # waiting the service to quit, which will not happen

        :param proc:
        :return:
        """
        def kill(pid):
            process = psutil.Process(pid)
            for p in process.children(recursive=True):
                p.kill()
            process.kill()

        errlines = []

        def reader(proc):
            with proc.stderr as stdout:
                for line in stdout:
                    print(line.decode(), file=sys.stderr)
                    errlines.append(line)

        threading.Thread(target=reader, args=(proc,)).start()

        time.sleep(0.1)
        kill(proc.pid)

        return errlines

    def kill_bashport(self, port):
        """
        deprecated('Not correct')

        Kill port listener, with bash command
        :param port:
        :return:
        """
        cmd = f"netstat -anp | grep :{port} | awk '{{print $7}}' | grep -Eo '[0-9]{1,10}' | xargs kill -9"
        print(cmd)
        p = subprocess.Popen(cmd)
        p.communicate()

    # def install_winsrv(v):
    #     if get_os() == 'Windows':
    #         pass
    #     else:
    #         raise PortfolioException('This is only for Windows. To install service on Linux, add this to system service:\n'
    #                                  'java -jar bin/jserv-album-0.#.#.jar\n'
    #                                  'Modify WEB-INF/settings.json/{port} for binding to different port.')

