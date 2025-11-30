"""
Thanks to Grok
"""
import os
import shutil
import threading
import time
from pathlib import Path
from typing import Callable

from PySide6.QtWidgets import (
    QApplication, QLabel
)
from anson.io.odysz.common import LangExt, Utils
from jre_mirror.temurin17 import TemurinMirror
from semanticshare.io.oz.edge import JRERelease


# ------------------------------------------------------------------
# Worker that runs in a QThread
# ------------------------------------------------------------------
_jre_ = 'jre17'
_jre_path_ = Path(_jre_)
_event_loop_interval_ = 0.1


class DownloadWorker():

    def __init__(self, temurin_release):
        super().__init__()
        self.temurin_release = temurin_release
        self._cancelled = False
        self._finished  = False

    def cancel(self):
        self._cancelled = True

    def run(self, on_progress: Callable[[int, int, int], None]):
        mirror = TemurinMirror(self.temurin_release)

        try:
            jre_temp = f'{_jre_}-temp'
            on_progress(0, 100, 100)
            extract, ext_path = mirror.resolve_to(
                                jre_temp, extract_check=True, prog_hook=on_progress)

            if extract and ext_path:
                print(f'{ext_path}/* -> {_jre_}')
                if os.path.exists(_jre_):
                    shutil.rmtree(_jre_, ignore_errors=True)
                shutil.move(ext_path, _jre_)
                shutil.rmtree(jre_temp)
            else:
                Utils.warn("Download & install JRE failed: " + self.temurin_release)

            self._finished = True
            print("JRE-WORKER finished")
        except Exception as e:
            print(e)


class JreDownloader:
    jrelease: JRERelease
    ui_lable: QLabel

    def __init__(self, progress_label: QLabel=None):
        super().__init__()
        self._cancelled = False
        self.ui_lable = progress_label

    def progress_text(self, percent: int):
        return f'Downloading JRE: {percent}%{"" if LangExt.isblank(self.jrelease.proxy) else " proxy: " + self.jrelease.proxy}'

    def label_progress(self, blocknum, blocksize, totalsize):
        if self._cancelled:
            return True  # tell the downloader to abort

        if self.ui_lable and totalsize > 0:
            read = blocknum * blocksize
            percent = min(100, read * 100 // totalsize)
            self.ui_lable.setText(self.progress_text(percent))

    def start_download_cli(self, jre_release: JRERelease, on_prgess):
        def download():
            self.worker.run(on_prgess)

        self.jrelease = jre_release
        self.worker = DownloadWorker(self.jrelease)
        self.thread = threading.Thread(target=download)

        on_prgess(0, 10, 100)
        self.thread.start()

        while not self.worker._finished and not self.worker._cancelled:
            time.sleep(_event_loop_interval_)

        return self.worker._finished, self.worker._cancelled

    def start_download_gui(self, jre_release: JRERelease):
        def download():
            self.worker.run(self.label_progress)

        self.jrelease = jre_release
        self.worker = DownloadWorker(self.jrelease)
        self.thread = threading.Thread(target=download)

        if self.ui_lable:
            self.ui_lable.setText(f'Start to download JRE...')

        self.thread.start()

        while not self.worker._finished and not self.worker._cancelled:
            time.sleep(_event_loop_interval_)
            QApplication.processEvents()

        if self.ui_lable:
            if self.worker._finished:
                self.ui_lable.setText('Download completed.')
            elif self.worker._cancelled:
                self.ui_lable.setText('Download cancelled.')
            else:
                self.ui_lable.setText('Download aborted.')

    def cancel_download(self):
        if self.worker:
            self.worker.cancel()

    def cleanup(self, message: str):
        print(message)
        if self.thread:
            self.thread.join()

    def isrunning(self):
        return self.thread and self.thread.is_alive()
