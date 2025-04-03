import platform
from socketserver import TCPServer
from threading import Thread

import pywintypes
import win32serviceutil
import win32service
import win32event
import servicemanager
import logging
import logging.handlers
import os
import sys
import time

album_web_dir = 'web-dist'
web_srvname = 'AlbumWeb'
index_html = 'index.html'


# Configuration (embedded for simplicity)
class Config:
    LOG_FILE = os.path.join(os.path.dirname(__file__), "logs", "service.log")
    LOG_LEVEL = logging.INFO
    MAX_LOG_BYTES = 1024 * 1024  # 1 MB
    BACKUP_COUNT = 3  # Keep 3 backup files

# Setup logging with rotation
def setup_logging():
    logger = logging.getLogger(__name__)
    logger.setLevel(Config.LOG_LEVEL)

    # Ensure log directory exists
    log_dir = os.path.dirname(Config.LOG_FILE)
    if log_dir and not os.path.exists(log_dir):
        os.makedirs(log_dir)

    # RotatingFileHandler for size control
    handler = logging.handlers.RotatingFileHandler(
        filename = Config.LOG_FILE,
        maxBytes = Config.MAX_LOG_BYTES,
        backupCount = Config.BACKUP_COUNT
    )
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    handler.setFormatter(formatter)
    logger.addHandler(handler)

    # Console handler for debugging (remove in production if desired)
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    logger.info("Logger is running...")
    return logger


# Service class
class AlbumWeb(win32serviceutil.ServiceFramework):
    @staticmethod
    def start_web(webport: int = 8900) -> tuple[TCPServer, Thread]:
        import http.server
        import socketserver
        import threading

        # PORT = 8900
        if webport == None:
            webport = 8900

        httpdeamon: [TCPServer] = [None]

        # To serve gzip, see
        # https://github.com/ksmith97/GzipSimpleHTTPServer/blob/master/GzipSimpleHTTPServer.py#L244
        class WebHandler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, directory=album_web_dir, **kwargs)
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
            print("Starting web at port", webport)
            with socketserver.TCPServer(("", webport), WebHandler) as httpd:
                httpdeamon[0] = httpd
                httpd.serve_forever()

        if not os.path.isdir(album_web_dir):
            raise FileNotFoundError(f'Cannot find web root folder: {album_web_dir}')
        elif not os.path.isfile(os.path.join(album_web_dir, index_html)):
            raise FileNotFoundError(f'Cannot find {index_html} in {album_web_dir}')

        daemon = threading.Thread(target=create_server, daemon=True)
        daemon.start()

        count = 0
        while count < 20 and httpdeamon[0] is None:
            # TODO: lights
            count += 1
            time.sleep(0.2)

        print(httpdeamon[0])
        return httpdeamon[0], daemon

    def __init__(self, args):
        win32serviceutil.ServiceFramework.__init__(self, args)
        self.hWaitStop = win32event.CreateEvent(None, 0, 0, None)
        self.logger = setup_logging()
        # self.logic = BusinessLogic()
        # self.is_running = False
        self.logger.debug(args)
        self.port = args.port
        self.svc_name_ = args.srvName
        self.svc_display_name_ = args.displayName
        self.svc_description_ = args.describe

    def SvcStop(self):
        """Stop the service gracefully."""
        # self.logger.info("Album-web stopping...")
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        # self.is_running = False
        win32event.SetEvent(self.hWaitStop)
        self.ReportServiceStatus(win32service.SERVICE_STOPPED)

    def SvcDoRun(self):
        """Execute the service logic."""
        # self.logger = setup_logging()
        # self.logger.info("Album-web starting...")
        # self.ReportServiceStatus(win32service.SERVICE_START_PENDING)
        try:
            self.ReportServiceStatus(win32service.SERVICE_RUNNING)
            # httpd, daemon = AlbumWeb.start_web(self.port)
            while True:
                if win32event.WAIT_TIMEOUT != win32event.WaitForSingleObject(self.hWaitStop, 1800):
                    print("Stopping Album-web ...")
                    # if httpd is not None:
                    #     httpd.shutdown()
                    self.ReportServiceStatus(win32service.SERVICE_STOPPED)
                    break

        except Exception as e:
            self.ReportServiceStatus(win32service.SERVICE_STOPPED)
            self.logger.error(f"Album-web error: {str(e)}", exc_info=True)
            self.SvcStop()

    @classmethod
    def install(cls):
        logger = setup_logging()
        logger.info('install ...')

        if platform.system() != "Windows":
            print("This script is designed to run on Windows only.")
            sys.exit(1)

        # pythonClassStr = "src.synodepy3.cli"
        # pythonClassStr = '"C:\\Users\\Alice\\github\\semantic-jserv\\synode.py\\src\\synodepy3\\cli.py start-web" run'
        pythonClassStr = f"{__name__}.{cls.__name__}"
        # exeArg = f'"{os.path.abspath(__file__)}" run'
        # '"C:\\Users\\Alice\\github\\semantic-jserv\\synode.py\\src\\synodepy3\\websrv_delete.py" run'

        # exeArg = f'"py -m src.synodepy3.cli start-web" run'
        exeArg = f'"{os.path.abspath(__file__)}" run'

        print('pythonClassStr', pythonClassStr)
        print('exeArgs', exeArg, 'len =', len(exeArg))

        try:
            log_dir = os.path.dirname(Config.LOG_FILE)
            if log_dir and not os.path.exists(log_dir):
                os.makedirs(log_dir)
            win32serviceutil.InstallService(
                pythonClassString = pythonClassStr,  # Just the Windows Registry class name
                serviceName       = web_srvname,
                displayName       = 'Album-web Service',
                startType         = win32service.SERVICE_AUTO_START,  # Auto-start at boot
                errorControl      = win32service.SERVICE_ERROR_NORMAL,  # Default error control
                bRunInteractive   = 0,  # Do not run interactively
                serviceDeps       = [],  # No dependencies
                userName          = None,  # Run as SYSTEM (default)
                password          = None,  # No password for SYSTEM
                exeName           = f'{sys.executable} -m',  # Path to Python interpreter
                exeArgs           = exeArg,  # Script and run argument
                description       = 'Album-web Windows Service',
                delayedstart      = False  # No delayed start
            )
            print(f"Album-web service installed successfully.")
        except Exception as e:
            print(f"Failed to install service: {e}")

    @staticmethod
    def uninstall():
        """Uninstall the service with runtime checks."""
        if platform.system() != "Windows":
            print("This script is designed to run on Windows only.")
            sys.exit(1)

        try:
            try:
                win32serviceutil.QueryServiceStatus(web_srvname)
            except pywintypes.error as e:
                if e.winerror == 1060:  # ERROR_SERVICE_DOES_NOT_EXIST
                    print(f"Service '{web_srvname}' does not exist.")
                    return

            win32serviceutil.RemoveService(web_srvname)
            print(f"Service '{web_srvname}' uninstalled successfully.")

        except Exception as e:
            print(f"Failed to uninstall service: {e}")
            sys.exit(1)

if __name__ == "__main__":
    print('websrv main', sys.argv)
    if len(sys.argv) == 1:
        servicemanager.Initialize()
        servicemanager.PrepareToHostSingle(AlbumWeb)
        servicemanager.StartServiceCtrlDispatcher()
    else:
        if sys.argv[1] == "install":
            AlbumWeb.install()
        elif sys.argv[1] == "remove":
            AlbumWeb.uninstall()
        elif sys.argv[1] == "run":
            print("This is only for run the service by SC. See https://grok.com/chat/5eaf268d-0137-4ac3-89c9-6648d1c95bcf, can't wrap my head.")
            print('Only for debug!')

        else:
            print("Unknown command. Use 'install', 'remove', or run implicitly.")
