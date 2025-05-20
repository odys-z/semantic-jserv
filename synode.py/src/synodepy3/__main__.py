import os
import sys

from anclient.io.odysz.jclient import Clients

import io as std_io
from typing import Optional, cast

import PySide6
import qrcode
from PySide6.QtCore import QEvent
from PySide6.QtGui import QPixmap, Qt
from PySide6.QtWidgets import QApplication, QMainWindow, QFileDialog, QMessageBox, QLabel  #, QSpacerItem, QSizePolicy
from anson.io.odysz.common import Utils, LangExt

from src.io.oz.jserv.docs.syn.singleton import PortfolioException, AppSettings, getJservOption
from src.io.oz.syn import AnRegistry, SyncUser
from src.synodepy3.commands import install_htmlsrv, install_wsrv_byname, winsrv_synode, winsrv_websrv
from src.synodepy3.installer_api import InstallerCli, install_uri, web_inf, settings_json, serv_port0, web_port0

# Important:
# Run the following command to generate the ui_form.py file
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
    # if Utils.get_os() != 'Windows':
    #     msg.setStyleSheet("QMessageBox{min-width:6em;}")
    # else:
    #     msg.setStyleSheet("QLabel{min-width:2em}")

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

    # horizontal_spacer = QSpacerItem(800, 0, QSizePolicy.Minimum, QSizePolicy.Expanding)
    # layout = msg.layout()
    # layout.addItem(horizontal_spacer, layout.rowCount(), 0, 1, layout.columnCount())

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

        # self.httpd = None
        # self.webth = None

        self.ui = Ui_InstallForm()
        self.ui.setupUi(self)

        self.root_path = 'registry'
        self.cli = InstallerCli()

    @staticmethod
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

    def gen_qr(self) -> Optional[dict]:
        """
        Generate ip, port and QR.
        :return:
        """

        # settings don't have current IP
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

        # try:
        #     port = int(self.ui.txtPort.text(), 10)
        # except Exception as e:
        #     port = serv_port0
        #     self.ui.txtPort.setText(str(port))
        port = self.cli.settings.port

        iport = f'{ip}:{port}'
        synode = self.ui.txtSynode.text()
        data = getJservOption(synode, iport, False)

        InstallerForm.set_qr_label(self.ui.lbQr, data)
        return {"ip": ip, "port": port, "synodepy3": synode}

    def default_ui_values(self):
        # if self.ui.txtSyncIns.text() is None or self.ui.txtSyncIns.text().strip() == '':
        #     self.ui.txtSyncIns.setText('120.0')
        if LangExt.len(self.ui.txtSyncIns.text()) == 0:
            self.ui.txtSyncIns.setText('120.0')
        if LangExt.len(self.ui.txtPort.text()) == 0:
            self.ui.txtPort.setText(serv_port0)
        if LangExt.len(self.ui.txtWebport.text()) == 0:
            self.ui.txtWebport.setText(web_port0)

    def validate(self) -> bool:
        """
        Validate user's input
        :return: succeed or not
        """
        self.ui.bWinserv.setEnabled(False)

        err = self.cli.validate(
                volpath=self.ui.txtVolpath.text(),
                synid=self.ui.txtSynode.text(),
                peerjservs=self.ui.jservLines.toPlainText(),
                warn=warn_msg)

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

    def pings(self):
        details = []
        errs = False
        def err_ctx(c, e: str, *args: str) -> None:
            print(c, e.format(args), file=sys.stderr)
            details.append('\n' + e)
            errs = True

        jservss = self.ui.jservLines.toPlainText()
        if jservss is not None and len(jservss) > 8:
            jservss = InstallerCli.parsejservstr(jservss)

            for jsrv in jservss:
                if jsrv[0] != self.cli.registry.config.synid:
                    Clients.servRt = jsrv[1]
                    resp = Clients.pingLess(install_uri, err_ctx)
                    if resp is None:
                        details.append(f'\n{jsrv[0]}: {jsrv[1]}\n' + 'Error: not responds')
                    else:
                        details.append(f'\n{jsrv[0]}: {jsrv[1]}\n'  + resp.toBlock(beautify = True))

        if errs:
            warn_msg('Ping synodes has errors. Check details for errors.', details)
        else:
            msg_box('Ping synodes completed. Check the echo messages in details.', details)
        return details

    def save(self):
        self.default_ui_values()

        try:
            self.cli.updateWithUi(
                jservss=self.ui.jservLines.toPlainText(),
                synid=self.ui.txtSynode.text(),
                syncins=self.ui.txtSyncIns.text(),
                port=self.ui.txtPort.text(),
                webport=self.ui.txtWebport.text(),
                volume=self.ui.txtVolpath.text())

            if self.validate():
                try:
                    self.cli.install(self.ui.txtResroot.text())

                    post_err = self.cli.postFix()
                    if not post_err:
                        msg_box("Setup successfully.")
                    else:
                        warn_msg("Install successfully, with errors automatically fixe. See details...", post_err)

                except FileNotFoundError or IOError as e:
                    # Changing vol path can reach here
                    err_msg('Setting up synodepy3 is failed.', e)
                except PortfolioException as e:
                    warn_msg('Configuration is updated with errors. Check the details.\n'
                             'If this is not switching volume, that is not correct' , e)

                self.enableServInstall()

        except PortfolioException as e:
            err_msg('Setting up synodepy3 is failed.', e)

    def test_run(self):
        if self.validate():
            msg_box('The settings is valid. You can close the opening terminal once you need to stop it.\n'
                    'A stand alone running is recommended. Install the service on Windows or start:\n'
                    'java -jar bin/jserv-album-#.#.#.jar\n'
                    'java -jar bin/html-web-#.#.#.jar')
            try:
                if self.cli.httpd is None:
                    self.cli.httpd, self.cli.webth = InstallerCli.start_web(int(self.ui.txtWebport.text()))
                self.cli.test_in_term()
                qr_data = self.gen_qr()
                print(qr_data)
                self.enableServInstall()
            except PortfolioException as e:
                self.ui.lbQr.clear()
                err_msg('Start Portfolio service failed', e.msg)
                return

    def installWinsrv(self):
        self.cli.stop_web()
        msg_box('Please close any service Window if any been started\n'
                'Click Ok to continue, and confirm all permission requests.\n'
                '4 Times Confirmation Required (Dialog can be hidden)!')

        srvname = install_wsrv_byname(self.cli.gen_wsrv_name())
        if srvname is not None:
            self.cli.settings.envars.update({winsrv_synode: srvname})

        srvname = install_htmlsrv(self.cli.gen_html_srvname())
        if srvname is not None:
            self.cli.settings.envars.update({winsrv_websrv: srvname})

        self.gen_qr()
        self.cli.settings.toFile(cast(str, os.path.join(web_inf, settings_json)))

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

            self.enableServInstall()

            if self.cli.isinstalled() and self.cli.hasrun():
                # self.gen_qr()
                ip = InstallerCli.reportIp()
                port = self.cli.settings.port

                InstallerForm.set_qr_label(self.ui.lbQr,
                                           getJservOption(self.cli.registry.config.synid,
                                                          f'{ip}:{port}',
                                                          self.cli.registry.config.https))

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
            self.ui.bPing.clicked.connect(self.pings)
            self.ui.bSetup.clicked.connect(self.save)
            self.ui.bValidate.clicked.connect(self.test_run)

            if Utils.get_os() == 'Windows':
                self.ui.bWinserv.clicked.connect(self.installWinsrv)
            else:
                self.ui.bWinserv.setEnabled(False)

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
        self.ui.txtSyncIns.setText('0' if cfg.syncIns is None else str(cfg.syncIns))

        credits_link = 'https://odys-z.github.io/products/album/credits.html'
        help_install_link = 'https://odys-z.github.io/products/portfolio/synode/setup.html#install-steps'
        self.ui.lblink.setText(f'Portfolio is based on <a href="{credits_link}">open source projects</a>.')
        self.ui.lbHelplink.setText(f'<a href="{help_install_link}">Help</a>.')

    def bindSettings(self, settings: AppSettings):
        self.ui.txtPort.setText(str(settings.port))
        self.ui.txtWebport.setText(str(settings.webport))
        self.ui.txtVolpath.setText(settings.Volume())

        if settings is not None:
            lines = "\n".join(settings.jservLines())
            print(lines)
            self.ui.jservLines.setText(lines)
        else:
            self.ui.jservLines.setText(
                '# Error: No configuration has been loaded. Check resource root path setting.')

    def enableServInstall(self):
        installed = self.cli.isinstalled()
        if Utils.iswindows():
            self.ui.bWinserv.setEnabled(installed)

    def closeEvent(self, event: PySide6.QtGui.QCloseEvent):
        super().closeEvent(event)
        if self.cli.httpd is not None or self.cli.webth is not None:
            try:
                InstallerCli.closeWeb(self.cli.httpd, self.cli.webth)
                self.cli.httpd, self.cli.webth = None, None
            finally:
                event.accept()
        else:
            event.accept()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    widget = InstallerForm()
    widget.show()
    sys.exit(app.exec())
