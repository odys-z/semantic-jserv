'''
JRE Installer (GUI)
'''
from typing import cast
import platform
import urllib.request
import zipfile
import os
from pathlib import Path

from anson.io.odysz.anson import Anson, AnsonException
from semanticshare.io.oz.edge import Temurin17Release
from jre_mirror.temurin17 import TemurinMirror

_jre_ = 'jre17'
_jre_path_ = Path('jre17')

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
    def cancel_download():
        print('Cancelling download.')
    
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


# Create the main application window for showing a progress bar.
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

    # FIXME Not working without in a background thread.
    if len(sys.argv) > 1 and sys.argv[1] == 'test-download':
        from src.synodepy3.installer_api import synode_ui
        from src.synodepy3 import jre_mirror_key
        
        temurin = Temurin17Release()
        temurin.path = synode_ui.langstr(jre_mirror_key)
        # temurin.mirroring.append('OpenJDK17U-jre_x64_linux_hotspot_17.0.17_10.tar.gz')
        temurin.proxy = 'proxy.json'
        jreimg = temurin.set_jre()
        print('JRE:', jreimg)
        dowload_jre(window, temurin)
        
    sys.exit(app.exec_())

