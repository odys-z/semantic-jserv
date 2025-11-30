'''
JRE Installer (GUI)
'''
import os
import threading
from pathlib import Path
from typing import cast

from anson.io.odysz.common import LangExt
from jre_mirror.temurin17 import TemurinMirror, guess_jretree
from semanticshare.io.oz.edge import Temurin17Release

_jre_ = 'jre17'
_jre_path_ = Path('jre17')

def progress_hook_cli(blocknum, blocksize, totalsize):
    read = blocknum * blocksize
    if totalsize > 0:
        percent = min(100, read * 100 // totalsize)
        print(f"\rDownloading... {percent}%", end="")


def validate_jre():
    if not Path.is_dir(Path(_jre_)) or \
        guess_jretree(_jre_) != Path(_jre_) or \
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
from PySide6.QtWidgets import (
    QApplication,
    QMainWindow,
    QPushButton,
    QVBoxLayout,
    QWidget,
    QProgressDialog,
)
from PySide6.QtCore import QThread
# from PySide6.QtGui import Qt

progress_dialog = cast(QProgressDialog, None)
jre17release = cast(Temurin17Release, None)
thjre = cast(threading.Thread, None)
progress_parent = cast(QWidget, None)

def on_progress(blocknum, blocksize, totalsize):
    global  progress_dialog
    read = blocknum * blocksize
    if totalsize > 0:
        percent = min(100, read * 100 // totalsize)
        progress_dialog.setValue(percent)


def downloading():
    global  progress_dialog, jre17release, progress_parent

    # QObject::startTimer: Timers can only be used with threads started with QThread
    progress_dialog = QProgressDialog(
        "Downloading ..." if LangExt.isblank(jre17release.proxy) \
            else f"Downloading with settings {jre17release.proxy} ...",
        "Cancel", 0, 100, None)

    progress_dialog.setWindowTitle("Installing JRE 17")
    # progress_dialog.setWindowModality(Qt.WindowModal)
    progress_dialog.setModal(True)
    progress_dialog.setMinimumDuration(0)

    progress_dialog.canceled.connect(cancel_download)
    progress_dialog.show()
    mirror = TemurinMirror(jre17release)
    mirror.resolve_to(_jre_, extract_check=True, prog_hook=on_progress)
    # mirror.release.save(list_json)

    # TODO verify this on Windows
    progress_dialog.setValue(100)
    # progress_dialog.close()
    print("quit downloading thread")

def cancel_download():
    print('Cancelling download.')

def dowload_jre_gui(parentui: QWidget, temurin17: Temurin17Release):
    global  progress_dialog, jre17release, thjre, progress_parent
    jre17release = temurin17
    progress_parent = parentui

    # Qt timers cannot be stopped from another thread 
    #
    # progress_dialog = QProgressDialog(
    #     "Downloading ..." if LangExt.isblank(temurin17.proxy) \
    #                       else f"Downloading with settings {temurin17.proxy} ...",
    #     "Cancel", 0, 100, parentui)
    #
    # progress_dialog.setWindowTitle("Installing JRE 17")
    # # progress_dialog.setWindowModality(Qt.WindowModal)
    # progress_dialog.setModal(True)
    # progress_dialog.setMinimumDuration(0)
    #
    # progress_dialog.canceled.connect(cancel_download)
    # progress_dialog.show()
    
    thjre = threading.Thread(target=downloading)
    thjre.start()
    # thjre.join() not to block Qt event loop
    print('Quit Envent Handler')
    # progress_dialog.close()

################### Test Section ###################
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
        # self.progress_dialog.setWindowModality(Qt.WindowModal)
        self.progress_dialog.setModal(True)
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

        import time
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
        dowload_jre_gui(window, temurin)
        
    sys.exit(app.exec_())

