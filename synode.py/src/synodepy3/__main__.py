import os
import sys

from anclient.io.odysz.jclient import Clients
from typing_extensions import deprecated

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

from anson.io.odysz.anson import Anson

from synodepy3 import Synode

Anson.java_src('src')
path = os.path.dirname(__file__)
synode_ui = cast(Synode, Anson.from_file(os.path.join(path,"synode.json")))

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
        self.ui = Ui_InstallForm()
        self.ui.setupUi(self)
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

        ip, port = self.getProxiedIp(self.cli.settings)

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
                ping_timeout=int(self.ui.txtimeout.text()),
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

    # def signup(self):
    #     details = []
    #     errs = False
    #     def err_ctx(c, e: str, *args: str) -> None:
    #         nonlocal errs, details
    #         print(c, e.format(args), file=sys.stderr)
    #         details.append('\n' + e)
    #         errs = True
    #
    #     jsrv = synode_ui.centralserv
    #     Clients.init(jserv=jsrv, timeout=20)
    #     resp = Clients.signLess(install_uri, err_ctx)
    #     if resp is None:
    #         details.append(f'\n{jsrv}: {jsrv}\n' + 'Error: no responds')
    #     else:
    #         details.append(f'\n{jsrv}: {jsrv}\n' + resp.toBlock(beautify=True))
    #
    #     if errs:
    #         warn_msg('Cannot sing up. Check details for errors.', details)
    #     else:
    #         msg_box('Sing up successfully. Register the or create a domain.', details)
    #     return details

    def signup_demo(self):
        msg_box(synode_ui.signup_prompt('This is a demo version. You can login with the provided admin Id and the password.'))

    def login(self) -> bool:
        # ui = self.ui
        # ui.txtDomain.setText('zsu')
        msg_box(synode_ui.signup_prompt('This is a demo version. You can login with the provided admin Id and the password.'))
        return False

    def pings(self):
        details = []
        errs = False

        def err_ctx(c, e: str, *args: str) -> None:
            nonlocal errs, details
            print(c, e.format(args), file=sys.stderr)
            details.append('\n' + e)
            errs = True

        jservss = self.ui.jservLines.toPlainText()
        if jservss is not None and len(jservss) > 8:
            jservss = InstallerCli.parsejservstr(jservss)

            for jsrv in jservss:
                if jsrv[0] != self.cli.registry.config.synid:
                    Clients.init(jserv=jsrv[1], timeout=int(self.ui.txtimeout.text()))
                    resp = Clients.pingLess(install_uri, err_ctx)
                    if resp is None:
                        details.append(f'\n{jsrv[0]}: {jsrv[1]}\n' + 'Error: no responds')
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
                webProxyPort=self.ui.txtWebport_proxy.text(),
                reverseProxy=self.ui.chkReverseProxy.checkState() == Qt.CheckState.Checked,
                proxyIp=self.ui.txtIP_proxy.text(),
                proxyPort=self.ui.txtPort_proxy.text(),
                volume=self.ui.txtVolpath.text())

            if self.validate():
                try:
                    self.cli.install(self.ui.txtResroot.text())

                    post_err = self.cli.postFix()
                    if not post_err:
                        msg_box("Setup successfully.\n"\
                                "If Windows services have been already installed, new settings can only take effects after restart the services. Restarting Windows won't work."\
                                if os.name == 'nt' else \
                                "Reload service settings and restart it to take effects.")
                    else:
                        warn_msg("Install successfully, with errors automatically fixe. See details...", post_err)

                except FileNotFoundError or IOError as e:
                    # Changing vol path can reach here
                    err_msg('Setting up synodepy3 is failed.', e)
                except PortfolioException as e:
                    warn_msg('Configuration is updated with errors. Check the details.\n'
                             'If this is not switching volume, that is not correct' , e)

                if self.ui.lbQr.pixmap is not None:
                    self.gen_qr()

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

    def updateChkReverse(self, check: bool):
        if check == None:
            check = self.ui.chkReverseProxy.checkState() == Qt.CheckState.Checked
        else:
            self.ui.chkReverseProxy.setChecked(check)

        self.ui.txtIP_proxy.setEnabled(check)
        self.ui.txtWebport_proxy.setEnabled(check)
        self.ui.txtPort_proxy.setEnabled(check)

    def updateRegistry(self):
        '''
        Update cli.registry, re-load, if volume/dictionary.json is found, or load from ./registry-i/dictinary.json.
        :return: path of loaded json file, without filename
        '''

    def showEvent(self, event: PySide6.QtGui.QShowEvent):
        def bindUi():
            self.ui.gboxRegistry.setTitle(
                # synode_ui.langs[synode_ui.lang]['gboxRegistry'].format(market=synode_ui.market))
                # synode_ui.langstr('gboxRegistry').format(market=synode_ui.market))
                synode_ui.langstrf('gboxRegistry', market=synode_ui.market))

            # lb_help = synode_ui.langs[synode_ui.lang]['lbHelplink']
            lb_help = synode_ui.langstr('lbHelplink')
            self.ui.lbHelplink.setText(f'<a href="{synode_ui.langs[synode_ui.lang]['help_link']}">{lb_help}</a>.')
            self.ui.lblink.setText(f'Portfolio is based on <a href="{synode_ui.credits}">open source projects</a>.')

        def bindRegistry(root: str):
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
                self.gen_qr()

            self.updateChkReverse(self.cli.settings.reverseProxy)

        super().showEvent(event)

        if event.type() == QEvent.Type.Show:
            bindUi()
            self.reloadRegistry()

            def setVolumePath():
                volpath = QFileDialog.getExistingDirectory(self, 'ResourcesPath')
                if volpath == self.registry_i:
                    return err_msg("Volume path cannot be the same as registry resource's root path.")
                self.ui.txtVolpath.setText(volpath)

            def reloadRespath():
                self.registry_i = QFileDialog.getExistingDirectory(self, 'ResourcesPath')
                self.ui.txtResroot.setText(self.registry_i)
                bindRegistry(self.registry_i)
                self.cli.settings.registpath = self.registry_i

            self.ui.bSignup.clicked.connect(self.signup_demo)

            self.ui.txtSynode.setEnabled(False)
            self.ui.bVolpath.clicked.connect(setVolumePath)
            self.ui.bRegfolder.clicked.connect(reloadRespath) # disabled 0.7.6
            # self.ui.bCreateDomain.clicked.connect(self.updatePeers)   # disabled 0.7.6

            self.ui.bLogin.clicked.connect(self.login)
            self.ui.bPing.clicked.connect(self.pings)
            self.ui.bSetup.clicked.connect(self.save)
            self.ui.bValidate.clicked.connect(self.test_run)

            self.ui.chkReverseProxy.clicked.connect(self.updateChkReverse)

            if Utils.get_os() == 'Windows':
                self.ui.bWinserv.clicked.connect(self.installWinsrv)
            else:
                self.ui.bWinserv.setEnabled(False)

    def reloadRegistry(self):
        '''
        Reload local dictionary.json.
        Try load volume/dictionary.json first.
        If volume/dictionary doesn't exists, then load registyr-i/dictonary.json.
        :return: loaded path
        '''
        self.registry_i = './registry-i'
        self.cli.registry = InstallerCli.loadRegistry(synode_ui.registry_i)
        Utils.logi("[reloadRegistry] {}", self.cli.registry)
        self.bindIdentity(self.cli.registry)
        self.bind_peerJservs()

    def bind_peerJservs(self):
        '''
        If volume/dictionary.json exists, update with config.peers;
        else if WEB-INF/settings.json.non-ici exists, update with settings.jservs;
        else update with registry-i/dictionary.json/config.peers
        :return:
        '''
        pass

    def bindIdentity(self, registry: AnRegistry):
        def findUser(usrs: [SyncUser], usrid):
            for u in usrs:
                if u.userId == usrid:
                    return u
        print(registry.config.toBlock())
        cfg = registry.config
        self.ui.txtAdminId.setText(cfg.admin)

        # 0.7.5
        # pswd = findUser(registry.synusers, cfg.admin).pswd
        # self.ui.txtPswd.setText(pswd)
        # self.ui.txtPswd2.setText(pswd)
        # self.ui.txtSynode.setText(cfg.synid)
        # self.ui.txtSyncIns.setText('0' if cfg.syncIns is None else str(cfg.syncIns))

        # 0.7.6
        self.ui.txtOrgid.setText(cfg.org.orgId)
        self.ui.txtDomain.setText(cfg.domain)
        pswd = findUser(registry.synusers, cfg.admin).pswd
        self.ui.txtDompswd.setText(pswd)
        self.ui.txtSynode.setText(cfg.synid)
        self.ui.txtSyncIns.setText('0' if cfg.syncIns is None else str(cfg.syncIns))

    def bindSettings(self, settings: AppSettings):
        self.ui.txtPort.setText(str(settings.port))
        self.ui.txtWebport.setText(str(settings.webport))

        self.ui.chkReverseProxy.setChecked(settings.reverseProxy)
        self.ui.txtIP_proxy.setText(settings.proxyIp)
        self.ui.txtPort_proxy.setText(str(settings.proxyPort))
        self.ui.txtWebport_proxy.setText(str(settings.webProxyPort))

        self.ui.txtResroot.setText(settings.Registpath())
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

    def getProxiedIp(self, settings):
        ip, port = self.cli.reportIp(), settings.port
        if settings.reverseProxy:
            ip, port = settings.proxyIp, settings.proxyPort
        return ip, port


if __name__ == "__main__":
    app = QApplication(sys.argv)
    widget = InstallerForm()
    widget.show()
    sys.exit(app.exec())
