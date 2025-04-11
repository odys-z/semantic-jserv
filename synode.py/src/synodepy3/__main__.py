import sys

import io as std_io
from typing import Optional

import PySide6
import qrcode
from PySide6.QtCore import QEvent, QSize
from PySide6.QtGui import QPixmap, Qt
from PySide6.QtWidgets import QApplication, QMainWindow, QFileDialog, QMessageBox, QLabel, QSpacerItem, QSizePolicy
from anson.io.odysz.common import Utils

from src.io.oz.jserv.docs.syn.singleton import PortfolioException, AppSettings
from src.io.oz.syn import AnRegistry, SyncUser
from src.synodepy3.commands import install_htmlsrv, install_jserv
from src.synodepy3.installer_api import InstallerCli, jserv_url_path


# Important:
# You need to run the following command to generate the ui_form.py file
#     pyside6-uic form.ui -o ui_form.py
from src.synodepy3.ui_form import Ui_InstallForm


def msg_box(info: str, details: object = None):
    msg = QMessageBox()
    msg.setWindowTitle("Message")
    msg.setText(info)
    msg.setDetailedText(str(details))
    msg.setIcon(QMessageBox.Icon.Information)
    result = msg.exec()
    return result


def err_msg(err: str, details: object = None):
    msg = QMessageBox()
    if Utils.get_os() != 'Windows':
        msg.setStyleSheet("QMessageBox{min-width:6em;}")
    else:
        msg.setStyleSheet("QLabel{min-width:10em}")

    qt_msgbox_label = msg.findChild(QLabel, "qt_msgbox_label")
    msg.layout().children()
    qt_msgbox_label.setAlignment(Qt.AlignmentFlag.AlignLeft)

    msg.setWindowTitle("Error")
    msg.setText(err)
    try:
        msg.setDetailedText(str(details))
    except:
        Utils.warn(details)
    msg.setIcon(QMessageBox.Icon.Critical)
    result = msg.exec()
    return result


def warn_msg(warn: str, details: object = None):
    msg = QMessageBox()
    # if get_os() != 'Windows':
    #     msg.setStyleSheet("QLabel{min-width:200px;}")
    # else:
    #     msg.setStyleSheet("QLabel{min-width:20px;}")

    # msg.setMinimumWidth(400)
    horizontal_spacer = QSpacerItem(800, 0, QSizePolicy.Minimum, QSizePolicy.Expanding)
    layout = msg.layout()
    layout.addItem(horizontal_spacer, layout.rowCount(), 0, 1, layout.columnCount())

    qt_msgbox_label = msg.findChild(QLabel, "qt_msgbox_label")
    msg.layout().children()
    qt_msgbox_label.setAlignment(Qt.AlignmentFlag.AlignLeft)

    msg.setWindowTitle("Warning")
    msg.setText(warn + ' ' * 20)
    msg.setDetailedText(str(details))
    msg.setIcon(QMessageBox.Icon.Warning)
    result = msg.exec()
    return result


class InstallerForm(QMainWindow):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.httpd = None
        self.ui = Ui_InstallForm()
        self.ui.setupUi(self)

        self.root_path = 'registry'
        self.cli = InstallerCli()

    def gen_qr(self) -> Optional[str]:
        """
        Generate ip, port and QR.
        :return:
        """
        def set_qr_label(label, text):
            """
            set qrcode image on QLabel

            @param label: QLabel
            @param text: text for the QR code
            """
            buf = std_io.BytesIO()
            img = qrcode.make(text, border=1)
            try:
                img.save(buf, 'PNG')
                qt_pixmap = QPixmap()
                qt_pixmap.loadFromData(buf.getvalue(), 'PNG')
                label.setPixmap(qt_pixmap)
                label.setScaledContents(True)
            except Exception as e:
                # Weired exception raised from Image.save(), when it's importing GifImagePlugin.
                # Similar issue: https://github.com/python-pillow/Pillow/issues/2803
                # This error can occur and disappear without any clear conditions.
                label.setText(text)
                err_msg(f'Generating QR Code Error. Please generate QR Code for:\n{text}', e)

        if len(self.ui.txtIP.text()) < 7:
            try:
                ip = self.cli.reportIp()
            except OSError as e:
                err_msg('Network IP can not be found. IP address must be manually set.\n{1}.', e)
                return None

            self.ui.txtIP.setText(ip)
        elif self.ui.txtIP.text() == '0.0.0.0':
            ip = InstallerCli.reportIp()
        else:
            ip = self.ui.txtIP.text()

        try:
            port = int(self.ui.txtPort.text(), 10)
        except Exception as e:
            port = 8964
            self.ui.txtPort.setText(str(port))

        iport = f'{ip}:{port}'
        synode = self.ui.txtSynode.text()
        data = f'{synode}-{iport}\nhttp://{iport}/{jserv_url_path}'

        set_qr_label(self.ui.lbQr, data)
        return {"ip": ip, "port": port, "synodepy3": synode}

    def validate(self) -> bool:
        """
        Validate user's input
        :return: succeed or not
        """
        self.ui.bWinserv.setEnabled(False)

        err = self.cli.validate(
                volpath=self.ui.txtVolpath.text(),
                synid=self.ui.txtSynode.text(),
                peerjservs=self.ui.jservLines.toPlainText())

        if err is not None:
            self.updateValidation(err)
            self.ui.lbQr.clear()
            return False
        return True

    def updateValidation(self, err: dict):
        if 'exiftool' in err:
            if Utils.get_os() == 'Windows':
                Utils.warn('Installing exiftool failed.')
            else:
                err_msg('Install Exiftool from <a href="https://exiftool.org/">here</a> first.', err)
        else:
            err_msg('Validation failed.', err)

    def login(self) -> bool:
        ui = self.ui
        ui.txtDomain.setText('zsu')
        return False

    def setup(self):
        try:
            self.cli.updateWithUi(
                jservss=self.ui.jservLines.toPlainText(),
                synid=self.ui.txtSynode.text(),
                port=self.ui.txtPort.text(),
                volume=self.ui.txtVolpath.text())

            if self.validate():
                try:
                    self.cli.install(self.ui.txtResroot.text())
                    msg_box("Setup successfully.")
                except FileNotFoundError or IOError as e:
                    # Changing vol path can reach here
                    err_msg('Setting up synodepy3 has failed.', e)
                except PortfolioException as e:
                    warn_msg('Configuration is updated with errors. Check the details.\nIf this is not switching volume, that is not correct' , e)

                self.updateUiByInstalled()

        except PortfolioException as e:
            err_msg('Setting up synodepy3 has failed.', e)

    def test_run(self):
        if self.validate():
            msg_box('The settings is valid. You can close the opening terminal once you need to stop it.\n'
                    'A stand alone running is recommended. Install the service on Windows or start:\n'
                    'java -jar bin/jserv-album-#.#.#.jar\n'
                    'java -jar bin/html-web-#.#.#.jar')
            try:
                self.httpd = InstallerCli.start_web()
                self.cli.test_in_term()
                qr_data = self.gen_qr()
                print(qr_data)
                self.updateUiByInstalled()
            except PortfolioException as e:
                self.ui.lbQr.clear()
                err_msg('Start Portfolio service failed', e.msg)
                return

    def installWinsrv(self):
        self.cli.stop_web(self.httpd)
        msg_box('Please close any service Window if any been started\n'
                'Click Ok to continue, and confirm all permission requests (window can be hidden).')

        install_jserv()
        install_htmlsrv()

        msg_box('Services installed. You can check in Windows Service Control, or logs in current folder.\n'
                'Restart the computer if the service starting failed due to binding ports, by which you started tests early.')

    def showEvent(self, event: PySide6.QtGui.QShowEvent):
        super().showEvent(event)

        def bindInitial(root: str):
            print(f'loading {root}')
            self.cli = InstallerCli()

            try: self.cli.loadInitial(root)
            except FileNotFoundError as e:
                return err_msg('Cannot find registry.', e)
            except PortfolioException as e:
                return err_msg(e.msg, e.cause)

            self.bindIdentity(self.cli.registry)

            json = self.cli.settings
            self.bindSettings(json)

            self.updateUiByInstalled()

        if event.type() == QEvent.Type.Show:
            bindInitial(self.root_path)

            def setVolumePath():
                volpath = QFileDialog.getExistingDirectory(self, 'ResourcesPath')
                if volpath == self.root_path:
                    return err_msg("Volume path cannot be the same as registry resource's root path.")
                self.ui.txtVolpath.setText(volpath)

            def reloadRespath():
                self.root_path = QFileDialog.getExistingDirectory(self, 'ResourcesPath')
                self.ui.txtResroot.setText(self.root_path)
                bindInitial(self.root_path)

            self.ui.bVolpath.clicked.connect(setVolumePath)
            self.ui.bRoot.clicked.connect(reloadRespath)

            self.ui.bLogin.clicked.connect(self.login)
            self.ui.bSetup.clicked.connect(self.setup)
            self.ui.bValidate.clicked.connect(self.test_run)

            if Utils.get_os() == 'Windows':
                self.ui.bWinserv.clicked.connect(self.installWinsrv)
            else:
                self.ui.bWinserv.setEnabled(False)

            # self.httpd = InstallerCli.start_web()

    def bindIdentity(self, registry: AnRegistry):
        def findUser(usrs: [SyncUser], usrid):
            for u in usrs:
                if u.userId == usrid:
                    return u
        print(registry.config.toBlock())
        cfg = registry.config
        self.ui.txtUserId.setText(cfg.admin)
        self.ui.txtUserId.setText(cfg.admin)

        pswd = findUser(registry.synusers, cfg.admin).pswd
        self.ui.txtPswd.setText(pswd)
        self.ui.txtPswd2.setText(pswd)
        self.ui.txtSynode.setText(cfg.synid)

        credits_link = 'https://odys-z.github.io/products/album/credits.html'
        help_install_link = 'https://odys-z.github.io/products/portfolio/synode/setup.html#install-steps'
        self.ui.lblink.setText(f'Portfolio is based on <a href="{credits_link}">open source projects</a>.')
        self.ui.lbHelplink.setText(f'<a href="{help_install_link}">Help</a>.')

    def bindSettings(self, settings: AppSettings):
        self.ui.txtPort.setText(str(settings.port))
        self.ui.txtVolpath.setText(settings.Volume())

        if settings is not None:
            lines = "\n".join(settings.jservLines())
            print(lines)
            self.ui.jservLines.setText(lines)
        else:
            self.ui.jservLines.setText(
                '# Error: No configuration has been loaded. Check resource root path setting.')

    def updateUiByInstalled(self):
        installed = self.cli.isinstalled()
        if Utils.iswindows():
            self.ui.bWinserv.setEnabled(installed)

    def closeEvent(self, event: PySide6.QtGui.QCloseEvent):
        super().closeEvent(event)
        if self.httpd is not None:
            try:
                InstallerCli.closeWeb(self.httpd)
            finally:
                event.accept()
        else:
            event.accept()


        # try:
        #     if self.httpd is not None:
        #         self.httpd.shutdown()
        #         self.httpd = None
        #     else:
        #         print("No???")
        # finally:
        #     event.accept()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    widget = InstallerForm()
    widget.show()
    sys.exit(app.exec())
