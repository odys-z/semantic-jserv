'''
Thanks to Grok!
'''
from typing import cast
import platform
import urllib.request
import zipfile
import os
from pathlib import Path

from anson.io.odysz.anson import Anson, AnsonException
from jre_mirror.temurin17 import TemurinMirror, Temurin17Release

_jre_ = 'jre17'
_jre_path_ = Path('jre17')

# def get_adoptium_jre17_url(download_url: str = None):
#     system = platform.system()
#     machine = platform.machine()
# 
#     if system == "Windows":
#         os_name = "windows"
#         ext = "zip"
#     elif system == "Darwin":
#         os_name = "mac"
#         ext = "tar.gz"
#     elif system == "Linux":
#         os_name = "linux"
#         ext = "tar.gz"
#     else:
#         raise RuntimeError("Unsupported OS")
# 
#     if machine in ("AMD64", "x86_64"):
#         arch = "x64"
#     elif machine in ("aarch64", "arm64"):
#         arch = "aarch64"
#     else:
#         raise RuntimeError(f"Unsupported arch: {machine}")
# 
#     # Latest Temurin 17 as of Nov 2025
#     # version = "17.0.13+11"   # ← change this when updating
#     if download_url is None:
#         #               https://github.com/adoptium/temurin17-binaries/releases/download/
#         #                       jdk-17.0.9%2B9.1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip
#         #
#         #               https://github.com/adoptium/temurin17-binaries/releases/download
#         #                      /jdk-17.0.9%2B9/OpenJDK17U-jre_x86-32_windows_hotspot_17.0.9_9.zip
#         #               https://github.com/adoptium/temurin17-binaries/releases/download
#         #                      /jdk-17.0.9%2B9/OpenJDK17U-jre_arm_linux_hotspot_17.0.9_9.tar.gz
#         download_url = 'https://github.com/adoptium/temurin17-binaries/releases/download'
# 
#     build, plus = "17.0.9", "9"
#     zip_gz = f"OpenJDK17U-jre_{arch}_{os_name}_hotspot_{build}_{plus}.{ext}"
#     return f"{download_url}/jdk-{build}%2B{plus}/{zip_gz}"

# def download_and_extract(url, target_dir="jre-download"):
#     target_dir = Path(target_dir)
#     target_dir.mkdir(exist_ok=True)
# 
#     filename = url.split("/")[-1]
#     zip_path = target_dir / filename
# 
#     if not zip_path.exists():
#         print(f"Downloading JRE for {platform.system()} {platform.machine()}...")
#         urllib.request.urlretrieve(url, zip_path, reporthook=progress_hook)
# 
#     print("Extracting...")
#     if filename.endswith(".zip"):
#         with zipfile.ZipFile(zip_path, 'r') as z:
#             z.extractall(target_dir)
#     else:
#         import tarfile
#         with tarfile.open(zip_path, 'r:gz') as t:
#             t.extractall(target_dir)
# 
#     # Find the actual jre folder (Adoptium extracts to jdk-xxx-jre)
#     for root, dirs, _ in os.walk(target_dir):
#         if "bin/java" in [os.path.join(root, d, "bin/java") for d in dirs]:
#             return Path(root)
#     raise RuntimeError("JRE extraction failed")

def progress_hook_cli(blocknum, blocksize, totalsize):
    read = blocknum * blocksize
    if totalsize > 0:
        percent = min(100, read * 100 // totalsize)
        print(f"\rDownloading... {percent}%", end="")


def validate_jre():
    if not Path.is_dir(Path(_jre_)) or \
        os.name == 'posix' and not Path.exists(_jre_path_ / 'bin' / 'java') or \
        os.name == 'nt' and not Path.exists(_jre_path_ / 'bin' / 'java.dll'):
        return {'jre': 'JRE 17 is not available.'}
    else:
        return None

# if __name__ == "__main__":
#     if not os.path.exists("jre"):
#         url = get_adoptium_jre17_url()
#         print(url)
#         jre_path = download_and_extract(url, target_dir="jre-17-download")
#         # On macOS/Adoptium the real folder is inside
#         real_jre = next((jre_path / p for p in os.listdir(jre_path) if p.startswith("jdk")), jre_path)
#         os.rename(real_jre, "jre")
#     # Now launch your app
#     os.execv(".", ["jre/bin/java", "-version"])

import sys
import time
from PySide6.QtWidgets import (
    QApplication,
    QMainWindow,
    QPushButton,
    QVBoxLayout,
    QWidget,
    QProgressDialog,
)
from PySide6.QtCore import QObject, QThread
from PySide6.QtGui import Qt


def dowload_jre(parentui: QWidget, temurin17: Temurin17Release):
    def cancel_download(a, b, c):
        print(a, b, c)
    
    progress_dialog = QProgressDialog(
        "Downloading ...", "Cancel", 0, 100, parentui
    )
    progress_dialog.setWindowTitle("Please Wait")
    progress_dialog.setWindowModality(Qt.WindowModal)
    progress_dialog.setMinimumDuration(0)

    progress_dialog.canceled.connect(cancel_download)
    
    def on_progress(blocknum, blocksize, totalsize):
        read = blocknum * blocksize
        if totalsize > 0:
            percent = min(100, read * 100 // totalsize)
            progress_dialog.setValue(percent)

    mirror = TemurinMirror(temurin17)
    mirror.resolve_to(_jre_, extract_check=True, prog_hook=on_progress)
    # mirror.release.save(list_json)


# 1. Create a QObject to run the long task
# This is a good practice as it separates the task logic from the thread itself.
# class Worker(QObject):
#     finished = pyqtSignal()
#     progress = pyqtSignal(int)
# 
#     def run_long_task(self):
#         """A dummy function for a long-running process."""
#         num_steps = 100
#         for i in range(num_steps):
#             time.sleep(0.05)  # Simulate a time-consuming operation
#             self.progress.emit(i + 1)
#             # You can add a check for cancellation here if the dialog has a cancel button
#         self.finished.emit()


# 2. Create the main application window
class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Progress Dialog Example")
        self.setGeometry(100, 100, 300, 100)

        layout = QVBoxLayout()
        self.start_button = QPushButton("Start Task")
        self.start_button.clicked.connect(self.start_progress)

        layout.addWidget(self.start_button)
        container = QWidget()
        container.setLayout(layout)
        self.setCentralWidget(container)

    def start_progress(self):
        self.start_button.setEnabled(False)

        # 3. Initialize QThread and Worker
        self.thread = QThread()
        # self.worker = Worker()
        # self.worker.moveToThread(self.thread)

        # 4. Create and configure QProgressDialog
        self.progress_dialog = QProgressDialog(
            "Working...", "Cancel", 0, 100, self
        )
        self.progress_dialog.setWindowTitle("Please Wait")
        self.progress_dialog.setWindowModality(Qt.WindowModal)
        self.progress_dialog.setMinimumDuration(0) # Show dialog immediately

        # 5. Connect signals and slots
        # self.thread.started.connect(self.worker.run_long_task)
        # self.worker.progress.connect(self.progress_dialog.setValue)
        # self.worker.finished.connect(self.progress_dialog.close)
        # self.worker.finished.connect(self.thread.quit)
        # self.worker.finished.connect(self.worker.deleteLater)
        self.thread.finished.connect(self.thread.deleteLater)

        # Connect the cancel button
        self.progress_dialog.canceled.connect(self.thread.quit)

        # 6. Start the thread
        self.thread.start()

        # Handle UI after the thread is done
        self.thread.finished.connect(lambda: self.start_button.setEnabled(True))
        self.thread.finished.connect(
            lambda: print("Task finished or was canceled.")
        )
        
        for i in range(101):
            self.progress_dialog.setValue(i)
            time.sleep(0.1)
        
        print('end of start_progress()')

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    if len(sys.argv) > 1 and sys.argv[1] == 'test-download':
        from src.synodepy3.installer_api import synode_ui
        from src.synodepy3 import jre_mirror_key
        
        temurin = Temurin17Release()
        temurin.path = synode_ui.langstr(jre_mirror_key)
        # temurin.mirroring.append('OpenJDK17U-jre_x64_linux_hotspot_17.0.17_10.tar.gz')
        jreimg = temurin.set_jre()
        print('JRE:', jreimg)
        dowload_jre(window, temurin)
        
    sys.exit(app.exec_())

