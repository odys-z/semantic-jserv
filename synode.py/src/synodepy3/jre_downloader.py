"""
Thanks to Grok
"""

import sys
from pathlib import Path

from PySide6.QtWidgets import (
    QApplication, QWidget, QVBoxLayout, QPushButton, QProgressDialog
)
from PySide6.QtCore import QObject, QThread, Signal, Slot
from jre_mirror.temurin17 import TemurinMirror
from semanticshare.io.oz.edge import Temurin17Release, JRERelease

# ------------------------------------------------------------------
# Worker that runs in a QThread
# ------------------------------------------------------------------
_jre_ = 'jre17'
_jre_path_ = Path('jre17')

class DownloadWorker(QObject):
    progress = Signal(int)      # emits 0-100
    finished = Signal()
    failed = Signal(str)

    def __init__(self, temurin_release):
        super().__init__()
        self.temurin_release = temurin_release
        self._cancelled = False

    def cancel(self):
        self._cancelled = True

    @Slot()
    def run(self):
        mirror = TemurinMirror(self.temurin_release)

        def prog_hook(blocknum: int, blocksize: int, totalsize: int):
            if self._cancelled:
                return True  # tell the downloader to abort
            read = blocknum * blocksize
            if totalsize > 0:
                percent = min(100, read * 100 // totalsize)
                self.progress.emit(percent)

        try:
            mirror.resolve_to(_jre_, extract_check=True, prog_hook=prog_hook)
            if not self._cancelled:
                self.progress.emit(100)
                self.finished.emit()
            print("JRE-WORKER finished")
        except Exception as e:
            self.failed.emit(str(e))


# ------------------------------------------------------------------
# Main GUI
# ------------------------------------------------------------------
class JreWorker:
    jrelease: JRERelease
    def __init__(self, jre_release: JRERelease):
        super().__init__()
        self.jrelease = jre_release

        # Will be created later
        self.progress_dialog: QProgressDialog | None = None
        self.worker: DownloadWorker | None = None
        self.thread: QThread | None = None

    def start_download(self, parentui: QWidget):
        # ------------------------------------------------------------------
        # 1. Create the progress dialog in the MAIN thread (very important!)
        # ------------------------------------------------------------------
        proxy_text = self.jrelease.proxy or ""
        label_text = "Downloading ..."
        if proxy_text:
            label_text += f" (proxy: {proxy_text})"

        self.progress_dialog = QProgressDialog(
            label_text, "Cancel", 0, 100, parentui
        )
        self.progress_dialog.setWindowTitle("Installing JRE 17")
        self.progress_dialog.setModal(True)
        self.progress_dialog.setMinimumDuration(0)   # show immediately
        self.progress_dialog.canceled.connect(self.cancel_download)

        # ------------------------------------------------------------------
        # 2. Set up QThread + Worker
        # ------------------------------------------------------------------
        self.thread = QThread(parent=parentui)
        self.worker = DownloadWorker(self.jrelease)
        self.worker.moveToThread(self.thread)

        # Connections
        self.worker.progress.connect(self.progress_dialog.setValue)
        self.worker.finished.connect(self.download_finished)
        # self.worker.failed.connect(self.download_failed)

        self.thread.started.connect(self.worker.run)
        self.thread.finished.connect(self.thread.deleteLater)

        # Start everything
        self.thread.start()
        self.progress_dialog.show()
        QApplication.processEvents()

    def cancel_download(self):
        if self.worker:
            self.worker.cancel()

    def download_finished(self):
        self.progress_dialog.setValue(100)
        self.cleanup("Download completed successfully!")

    # def download_failed(self, msg: str):
    #     self.cleanup(f"Download failed: {msg}")

    def cleanup(self, message: str):
        print(message)
        if self.progress_dialog:
            self.progress_dialog.close()
        if self.thread and self.thread.isRunning():
            self.thread.quit()
            self.thread.wait(3000)
