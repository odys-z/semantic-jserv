import win32serviceutil
import win32service
import win32event
import servicemanager
import logging
import logging.handlers
import os
import sys
import time

from io.oz.jserv.docs.syn.singleton import PortfolioException

album_web = 'web-dist'
index_html = 'index.html'


# Configuration (embedded for simplicity)
class Config:
#     SERVICE_NAME = "LazyPythonService"
#     SERVICE_DISPLAY_NAME = "Lazy Python Service"
#     SERVICE_DESCRIPTION = "A lazy Python-based Windows service."
    LOG_FILE = os.path.join(os.path.dirname(__file__), "logs", "service.log")
    LOG_LEVEL = logging.INFO
    MAX_LOG_BYTES = 1024 * 1024  # 1 MB
    BACKUP_COUNT = 5  # Keep 5 backup files

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

    return logger

# Business logic (simple example)
class BusinessLogic:
    def __init__(self):
        self.logger = logging.getLogger(__name__)

    def run(self):
        self.logger.info("Business logic running...")
        time.sleep(1)  # Simulate work
        # Add your service logic here (e.g., file monitoring, API calls)

# Service class
class AlbumWeb(win32serviceutil.ServiceFramework):
    # _svc_name_ = Config.SERVICE_NAME
    # _svc_display_name_ = Config.SERVICE_DISPLAY_NAME
    # _svc_description_ = Config.SERVICE_DESCRIPTION

    @staticmethod
    def start_web(webport=8900):
        import http.server
        import socketserver
        import threading

        # PORT = 8900
        httpdeamon: [socketserver.TCPServer] = [None]

        # To serve gzip, see
        # https://github.com/ksmith97/GzipSimpleHTTPServer/blob/master/GzipSimpleHTTPServer.py#L244
        class WebHandler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, directory=album_web, **kwargs)
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
                print("Starting web at port", webport)
                httpd.serve_forever()
                httpdeamon[0] = httpd

        if not os.path.isdir(album_web):
            raise PortfolioException(f'Cannot find web root folder: {album_web}')
        if not os.path.isfile(os.path.join(album_web, index_html)):
            raise FileNotFoundError(f'Cannot find {index_html} in {album_web}')

        # InstallerCli.update_private(jservport)

        thr = threading.Thread(target=create_server)
        thr.start()

        count = 0
        while count < 20 and httpdeamon[0] is None:
            count += 1
            time.sleep(0.2)

        print(httpdeamon[0])
        return httpdeamon[0]

    def __init__(self, args):
        win32serviceutil.ServiceFramework.__init__(self, args)
        self.hWaitStop = win32event.CreateEvent(None, 0, 0, None)
        self.logger = setup_logging()
        # self.logic = BusinessLogic()
        # self.is_running = False
        self.port = args.port
        self.svc_name_ = args.srvName
        self.svc_display_name_ = args.displayName
        self.svc_description_ = args.describe

    def SvcStop(self):
        """Stop the service gracefully."""
        self.logger.info("Album-web stopping...")
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        self.is_running = False
        win32event.SetEvent(self.hWaitStop)
        self.ReportServiceStatus(win32service.SERVICE_STOPPED)

    def SvcDoRun(self):
        """Execute the service logic."""
        self.logger.info("Album-web starting...")
        self.ReportServiceStatus(win32service.SERVICE_START_PENDING)
        try:
            # self.is_running = True
            self.ReportServiceStatus(win32service.SERVICE_RUNNING)
            # while self.is_running:
            #     self.logic.run()

            AlbumWeb.start_web(self.port)
            if win32event.WAIT_TIMEOUT != win32event.WaitForSingleObject(self.hWaitStop, 2400):
                print("Stoping Album-web ...")

        except Exception as e:
            self.logger.error(f"Album-web error: {str(e)}", exc_info=True)
            self.SvcStop()

if __name__ == "__main__":
    if len(sys.argv) == 1:
        servicemanager.Initialize()
        servicemanager.PrepareToHostSingle(AlbumWeb)
        servicemanager.StartServiceCtrlDispatcher()
    else:
        if sys.argv[1] == "install":
            pythonClassStr = f"{__name__}.AlbumWeb"
            exeArg = f'"{os.path.abspath(__file__)}" run'
            print('pythonClassStr', pythonClassStr, 'exeArgs', exeArg)

            try:
                log_dir = os.path.dirname(Config.LOG_FILE)
                if log_dir and not os.path.exists(log_dir):
                    os.makedirs(log_dir)
                win32serviceutil.InstallService(
                    pythonClassString = pythonClassStr,  # Fully qualified class name
                    serviceName = Config.SERVICE_NAME,
                    displayName = Config.SERVICE_DISPLAY_NAME,
                    startType = win32service.SERVICE_AUTO_START,  # Auto-start at boot
                    errorControl = win32service.SERVICE_ERROR_NORMAL,  # Default error control
                    bRunInteractive = 0,  # Do not run interactively
                    serviceDeps = [],  # No dependencies
                    userName = None,  # Run as SYSTEM (default)
                    password = None,  # No password for SYSTEM
                    exeName = sys.executable,  # Path to Python interpreter
                    exeArgs = exeArg,  # Script and run argument
                    description = Config.SERVICE_DESCRIPTION,
                    delayedstart = False  # No delayed start
                )
                print(f"Service '{Config.SERVICE_NAME}' installed successfully.")
            except Exception as e:
                print(f"Failed to install service: {e}")
        elif sys.argv[1] == "remove":
            try:
                win32serviceutil.RemoveService(Config.SERVICE_NAME)
                print(f"Service '{Config.SERVICE_NAME}' removed successfully.")
            except Exception as e:
                print(f"Failed to remove service: {e}")
        elif sys.argv[1] == "run":
            pass
        else:
            print("Unknown command. Use 'install', 'remove', or run implicitly.")