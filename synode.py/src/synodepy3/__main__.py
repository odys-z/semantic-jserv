import sys
import time

from synodepy3 import SynodeUi

sys.stdout.reconfigure(encoding="utf-8")

import os

import io
from typing import Optional, cast

import PySide6
import qrcode
from PySide6.QtCore import QEvent
from PySide6.QtGui import QPixmap, Qt
from PySide6.QtWidgets import QApplication, QMainWindow, QFileDialog, QMessageBox, QLabel  #, QSpacerItem, QSizePolicy

from anson.io.odysz.common import Utils, LangExt
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException, getJservOption, jserv_url_path
from semanticshare.io.oz.syn.registry import AnRegistry, RegistResp, CynodeStats
from semanticshare.io.oz.syn import SynodeMode, Synode

from synodepy3.commands import install_htmlsrv, install_wsrv_byname, winsrv_synode, winsrv_websrv
from synodepy3.installer_api import InstallerCli, web_inf, settings_json, serv_port0, web_port0, err_uihandlers, synode_ui

# Important:
# Run the following command to generate the ui_form.py file
#
from synodepy3.ui_form import Ui_InstallForm
from synodepy3.installer_api import mode_hub

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

details = ['']
errs = False
def err_ctx(c, e: str, *args: str) -> None:
    global errs, details
    details[0] = e
    errs = True

def err_ready():
    global errs, details
    errs = False
    details.clear()
    details.append('')

def has_err():
    global errs
    return errs


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
        buf = io.BytesIO()
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
            err_msg(f'Sorry! There are errors while generating QR Code. Try again or copy the text:\n{text}', e)

    def gen_qr(self) -> Optional[dict]:
        """
        Generate ip, port and QR.
        :return:
        """

        ip, port = self.cli.getProxiedIp()
        # iport = f'{ip}:{port}'
        iport = self.cli.get_iport()

        # synode = self.ui.txtSynode.text()
        synode = self.cli.registry.config.synid

        data = getJservOption(synode, iport, False)
        InstallerForm.set_qr_label(self.ui.lbQr, data)

        self.ui.txtIP.setText(InstallerCli.reportIp())

        return {"ip": ip, "port": port, "synodepy3": synode}

    def default_ui_values(self):
        if LangExt.len(self.ui.txtSyncIns.text()) == 0:
            self.ui.txtSyncIns.setText('120.0')
        if LangExt.len(self.ui.txtPort.text()) == 0:
            self.ui.txtPort.setText(serv_port0)
        if LangExt.len(self.ui.txtWebport.text()) == 0:
            self.ui.txtWebport.setText(web_port0)

    def validate(self) -> bool:
        """
        Validate data models (updated with ui).
        :return: succeed or not
        """
        err = self.cli.validate()
        if err is not None:
            self.updateValidation(err)
            self.ui.lbQr.clear()
            return False

        self.ui.bWinserv.setEnabled(False)
        self.ui.bTestRun.setEnabled(False)
        return True

    def updateValidation(self, err: dict):
        if 'exiftool' in err:
            if Utils.get_os() == 'Windows':
                Utils.warn('Installing exiftool failed.')
            else:
                err_msg('Install Exiftool from <a href="https://exiftool.org/">here</a> first.', err)
        else:
            err_msg('Validation failed.', err)

    def signup_demo(self):
        msg_box(synode_ui.signup_prompt('This is a demo version. TODO'))

    def login(self) -> bool:
        msg_box(synode_ui.signup_prompt('This is a demo version. TODO'))
        return False

    def query_domx(self, commuid) -> Optional[RegistResp]:
        global errs
        resp = self.cli.query_domx(synode_ui.market_id, commuid)
        if resp is None:
            details.append(self.cli.settings.regiserv + '\n' + 'Error while loading domains.')
            errs = True
        else:
            return cast(RegistResp, resp)

        if has_err():
            warn_msg('Central service cannot be reached.', details)
        return None

    def create_find_dom(self):
        global errs, details
        print("request create/join domain to", self.ui.txtCentral.text())

        err_ready()

        domainid = self.ui.cbbDomains.currentText().strip()
        if self.cli.update_domain(
                reg_jserv=self.ui.txtCentral.text().strip(),
                orgid=self.ui.cbbOrgs.currentText().strip(),
                domain=domainid
            ) and self.cli.validate_domain() is None:
            resp = self.cli.register()

            if resp is None:
                details.append(self.cli.settings.regiserv + '\n' + 'Error while rigstering.')
                errs = True
            else:
                if resp.r == RegistResp.R.domexists:
                    self.update_bind_domconf(resp)
                    errs = False
                    warn_msg("Domain already exists") # and still bind to the list
                elif resp.r == RegistResp.R.ok:
                    self.update_bind_domconf(resp)
                    # self.cli.registry.config = resp.diction
                    # self.bind_cbbpeers(peers=resp.peer_ids(), synid=resp.next_installing())
                    # self.bind_hubnode(resp)
                    errs = False
                    msg_box("Domain created: " + domainid)
                else:
                    warn_msg("Creating domain failed.")

        if has_err():
            warn_msg('Central service cannot be reached.', details)

        self.seal_has_run()

    def submit_jserv(self):
        """
        Call this after saving and validations are completed
        :return:
        """
        resp = self.cli.submit_mysettings()
        global errs, details
        if resp == None or errs:
            warn_msg("Failed to submit registration.", details)
        else:
            # update jservs
            self.cli.registry.config.peers = resp.diction.peers

    def pings(self):
        err_ready()
        jservss = self.ui.jservLines.toPlainText()
        if jservss is not None and len(jservss) > 8:
            jservss = InstallerCli.parsejservstr(jservss)

            for jsrv in jservss:
                sid = self.ui.cbbPeers.currentText()
                if sid != self.cli.registry.config.synid:
                    resp = self.cli.ping(jsrv[0])
                    if resp is None:
                        details.append(f'\n{sid}: {jsrv[0]}\n' + 'Error while pinging.')
                    else:
                        details.append(f'\n{sid}: {jsrv[0]}\n'  + resp.msg())

        if has_err():
            warn_msg('Ping synodes has errors. Check details for errors.', details)
        else:
            msg_box('Ping synodes completed. Check the echo messages in details.', details)
        return details

    def update_valid(self):
        self.cli.updateWithUi(
            reg_jserv=self.ui.txtCentral.text().strip(),
            admin=self.ui.txtAdminId.text(),
            pswd=self.cli.matchPswds(self.ui.txtPswd.text(), self.ui.txtPswd2.text()),
            org=self.ui.cbbOrgs.currentText(),
            domain=self.ui.cbbDomains.currentText().strip(),
            domphrase=self.ui.txtDompswd.text(),
            hubmode=self.ui.chkHub.checkState() == Qt.CheckState.Checked,
            jservss=self.ui.jservLines.toPlainText(),
            synid=self.ui.cbbPeers.currentText().strip(),
            syncins=self.ui.txtSyncIns.text(),
            port=self.ui.txtPort.text(),
            webport=self.ui.txtWebport.text(),
            webProxyPort=self.ui.txtWebport_proxy.text(),
            reverseProxy=self.ui.chkReverseProxy.checkState() == Qt.CheckState.Checked,
            proxyIp=self.ui.txtIP_proxy.text(),
            proxyPort=self.ui.txtPort_proxy.text(),
            volume=self.ui.txtVolpath.text())

        return self.validate()

    def save(self):
        self.default_ui_values()
        self.update()
        try:
            if self.update_valid():
                try:
                    self.cli.install()

                    post_err = self.cli.postFix()
                    if not post_err:
                        msg_box("Setup successfully.\n"\
                                "If Windows services have been already installed, new settings can only take effects after restart the services. Restarting Windows won't work."\
                                if os.name == 'nt' else \
                                "Reload service settings and restart it to take effects.")
                    else:
                        warn_msg("Install successfully, with errors automatically fixe. See details...", post_err)

                    if self.ui.lbQr.pixmap is not None:
                        self.gen_qr()

                    self.bind_config()
                    self.submit_jserv()

                except FileNotFoundError or IOError as e:
                    # Changing vol path can reach here
                    err_msg('Setting up synodepy3 is failed.', e)
                except PortfolioException as e:
                    warn_msg('Configuration is updated with errors. Check the details.\n'
                             'If this is not switching volume, that is not correct' , e)

                self.enableWinsrvInstall()

        except PortfolioException as e:
            err_msg('Setting up synodepy3 is failed.', e)

    def test_run(self):
        syncins = self.cli.registry.config.syncIns
        self.cli.registry.config.syncIns = 0
        self.save()

        msg_box('The settings is valid. You can close the opening terminal once you need to stop it.\n'
                'To stat the services, a stand alone running is recommended. Install the service on Windows or start:\n'
                'java -jar bin/jserv-album-#.#.#.jar\n'
                'java -jar bin/html-web-#.#.#.jar')
        try:
            if self.cli.httpd is None:
                self.cli.httpd, self.cli.webth = InstallerCli.start_web(int(self.ui.txtWebport.text()))
            self.cli.test_in_term()
            qr_data = self.gen_qr()
            print(qr_data)
            self.enableWinsrvInstall()
        except PortfolioException as e:
            self.ui.lbQr.clear()
            err_msg('Start Portfolio service failed', e.msg)

        time.sleep(0.2)
        self.cli.registry.config.syncIns = syncins
        self.save()
        self.bind_config()

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

        self.seal_has_run()

    def update_bind_domconf(self, resp: RegistResp):
        self.cli.registry.config = resp.diction
        self.bind_cbbpeers(peers=resp.peer_ids(), synid=resp.next_installing())
        self.bind_peer(resp)

    # def update_chkhub(self, check: bool):
    #     self.cli.registry.config.mode = mode_hub if check else None
    #
    #     self.ui.txtSyncIns.setDisabled(check)
    #     self.ui.jservLines.setDisabled(check)
    #     self.ui.bPing.setDisabled(check)
    #     self.ui.txtimeout.setDisabled(check)
    #     self.ui.bCreateDomain.setText(synode_ui.langs[synode_ui.lang]["txt_create_dom"]
    #                           if check else synode_ui.langs[synode_ui.lang]["txt_join_dom"])
    #
    #     if not check and LangExt.isblank(self.ui.txtSyncIns.text(), r'0+'):
    #         self.ui.txtSyncIns.setText("40")

    def select_community(self, commuix):
        domx = self.query_domx(self.ui.cbbOrgs.currentText())
        self.ui.cbbDomains.clear()
        if domx is not None:
            self.ui.cbbDomains.addItems(domx.domains())

    def select_domx(self, dix):
        domid = self.ui.cbbDomains.currentText()
        resp  = self.cli.query_domconf(domid)
        self.update_bind_domconf(resp)

    def select_peer_byid(self, synid):
        try:
            pr = self.cli.registry.find_peer(synid)
            self.bind_synode(pr)
        except: pass

    def select_peer(self, idx):
        '''
        Actually doing nothing as there is nothing from config.peers[x] to be bound to ui.
        :param idx:
        :return: None
        '''
        try: self.bind_synode(self.cli.registry.config.peers[idx])
        except Exception as _:
            pass

    def bind_synode(self, n: Synode):
        peer = self.cli.find_peer(n.synid)
        chk = peer.remarks == SynodeMode.hub.name
        self.ui.chkHub.setChecked(chk)

        self.enable_widgets()
        # self.update_chkhub(chk)
        #
        # can_install = LangExt.isblank(peer.stat)
        # self.ui.bSetup.setEnabled(can_install)
        # self.ui.bSetup.setEnabled(can_install)

    def update_chkreverse(self, check: bool):
        if check == None:
            check = self.ui.chkReverseProxy.checkState() == Qt.CheckState.Checked
        else:
            self.ui.chkReverseProxy.setChecked(check)

        self.ui.txtIP_proxy.setEnabled(check)
        # if check and LangExt.isblank(self.ui.txtIP_proxy.text(), '(^0*$)|(^0\.0\.0\.0$)'):
        #     self.ui.txtIP_proxy.setText(self.cli.settings.proxyIp)
        self.ui.txtIP_proxy.setEnabled(check)

        self.ui.txtWebport_proxy.setEnabled(check)
        if check and LangExt.isblank(self.ui.txtWebport_proxy.text(), '0'):
            self.ui.txtWebport_proxy.setText(str(self.cli.settings.webProxyPort))

        self.ui.txtPort_proxy.setEnabled(check)
        if check and LangExt.isblank(self.ui.txtPort_proxy.text(), '0'):
            self.ui.txtPort_proxy.setText(str(self.cli.settings.proxyPort))

    def bind_config(self):
        self.cli.registry = self.cli.load_settings()
        self.cli.registry = InstallerCli.loadRegistry(self.cli.settings.volume, 'registry')
        self.bindIdentity(self.cli.registry, synodeui=synode_ui)
        self.bindSettings()
        self.seal_has_run()

    def bind_cbborg(self, orgs: list[str], elect: str):
        self.ui.cbbOrgs.clear()
        self.ui.cbbOrgs.addItems(orgs)
        self.ui.cbbOrgs.setCurrentText(elect)

    def bind_cbbpeers(self, peers: list[Synode], synid):
        self.ui.cbbPeers.clear()
        if peers is not None:
            self.ui.cbbPeers.addItems([s.synid for s in peers if s is not None])
        if synid is not None:
            self.ui.cbbPeers.setCurrentText(synid)
            self.select_peer_byid(synid)

    def bind_peer(self, resp: RegistResp):
        if resp.diction.peers is not None and len(resp.diction.peers) > 0:
            peerno = self.cli.registry.find_peer(self.cli.registry.config.synid) or resp.diction.peers[0]

            if peerno.stat == CynodeStats.create:
                # bind this as hub
                self.ui.jservLines.setText(
                    peerno.jserv if peerno is not None else f'http://127.0.0.1:8964/{jserv_url_path}')
                # self.update_chkhub(True)

    def bindIdentity(self, registry: AnRegistry, synodeui: SynodeUi):
        cfg = registry.config
        cfg.org.orgType = synodeui.market_id
        print(cfg.toBlock())

        self.ui.txtAdminId.setText(cfg.admin)

        # users[0]
        self.ui.txtPswd.setText(registry.synusers[0].pswd)
        self.ui.txtPswd2.setText(registry.synusers[0].pswd)
        self.ui.txtDompswd.setText(registry.synusers[0].pswd)

        # self.ui.txtOrgid.setText(cfg.org.orgId)
        self.bind_cbborg([cfg.org.orgId], cfg.org.orgId)

        # self.cli.domoptions.add(cfg.domain)
        self.ui.cbbDomains.setCurrentText(cfg.domain)

        u = self.cli.find_synuser(cfg.admin)
        if u is not None:
            self.ui.txtDompswd.setText(u.pswd)

        self.ui.chkHub.setChecked(SynodeMode.hub.name == cfg.mode)

        # self.ui.txtSynode.setText(cfg.synid)
        self.bind_cbbpeers(cfg.peers, cfg.synid)

        self.ui.txtSyncIns.setText('0' if cfg.syncIns is None else str(int(cfg.syncIns)))

        # self.update_chkhub(SynodeMode.hub.name == cfg.mode)

    def bindSettings(self):
        peers, settings = self.cli.registry.config.peers, self.cli.settings

        self.ui.txtCentral.setText(settings.regiserv)

        self.ui.txtPort.setText(str(settings.port))
        self.ui.txtWebport.setText(str(settings.webport))

        self.ui.chkReverseProxy.setChecked(settings.reverseProxy)
        self.ui.txtIP_proxy.setText(settings.proxyIp)
        self.ui.txtPort_proxy.setText(str(settings.proxyPort))
        self.ui.txtWebport_proxy.setText(str(settings.webProxyPort))

        self.update_chkreverse(self.cli.settings.reverseProxy)

        self.ui.txtVolpath.setText(settings.Volume())

        if settings is not None:
            lines = "\n".join(settings.jservLines(peers))

            cfg = self.cli.registry.config
            self.bind_cbbpeers(peers, cfg.synid)
            print(lines)
            if cfg is not None and LangExt.len(cfg.peers) > 0:
                hub_id = cfg.peers[0].synid
                if hub_id in settings.jservs:
                    self.ui.jservLines.setText(f'{hub_id}:\t{settings.jservs[hub_id]}')
                else:
                    self.ui.jservLines.setText(f'{hub_id}:\thttp://127.0.0.1:{serv_port0}/{jserv_url_path}')

        else:
            self.ui.jservLines.setText(
                '# Error: No configuration has been loaded. Check resource root path setting.')

    def enableWinsrvInstall(self):
        '''
        replaced by enable_widgets()
        :return:
        '''
        installed = self.cli.isinstalled()
        if Utils.iswindows():
            self.ui.bWinserv.setEnabled(installed)

    def seal_has_run(self):
        '''
        replaced by enable_widgets()
        :return:
        '''

        def can_save():
            '''
            The synode id is correct and is not installed
            :return:
            '''
            next_id = self.ui.cbbPeers.currentText()
            le_node = self.cli.registry.find_peer(next_id)
            if le_node is not None and (le_node.stat is None or le_node.stat == CynodeStats.create):
                return self.cli.validateVol() is None
            else:
                return False

        neverun = not self.cli.hasrun()

        self.ui.bTestRun.setEnabled(neverun and can_save())
        self.ui.chkHub.setEnabled(neverun)
        self.ui.cbbOrgs.setEnabled(neverun)
        self.ui.cbbDomains.setEnabled(neverun)
        self.ui.cbbPeers.setEnabled(neverun)

        # TODO FIXME should still can change password
        # Or possibly write back to setting.json by jar?
        # self.ui.txtPswd.setEnabled(neverun)
        # self.ui.txtPswd2.setEnabled(neverun)
        self.ui.txtDompswd.setEnabled(neverun)

    def enable_widgets(self):
        def iscreating():
            '''
            The synode id is correct and is not installed
            :return:
            '''
            next_id = self.ui.cbbPeers.currentText()
            le_node = self.cli.registry.find_peer(next_id)
            if le_node is not None and (le_node.stat is None or le_node.stat == CynodeStats.create):
                return self.cli.validateVol() is None
            else:
                return False

        def update_chkhub(check: bool):
            self.cli.registry.config.mode = mode_hub if check else None

            self.ui.txtSyncIns.setDisabled(check)
            self.ui.jservLines.setDisabled(check)
            self.ui.bPing.setDisabled(check)
            self.ui.txtimeout.setDisabled(check)
            self.ui.bCreateDomain.setText(synode_ui.langs[synode_ui.lang]["txt_create_dom"]
                                          if check else synode_ui.langs[synode_ui.lang]["txt_join_dom"])

            if not check and LangExt.isblank(self.ui.txtSyncIns.text(), r'0+'):
                self.ui.txtSyncIns.setText("40")

        neverun = not self.cli.hasrun()

        self.ui.chkHub.setEnabled(neverun)
        self.ui.cbbOrgs.setEnabled(neverun)
        self.ui.cbbDomains.setEnabled(neverun)
        self.ui.txtDompswd.setEnabled(neverun) # can change in the future

        valid_peers = self.cli.is_peers_valid()
        self.ui.cbbPeers.setEnabled(valid_peers)

        update_chkhub(self.ui.chkHub.checkState() == Qt.CheckState.Checked)

        cansave = valid_peers and (not neverun or self.cli.registry.config.synid == self.ui.cbbPeers.currentText())
        self.ui.bSetup.setEnabled(cansave)

        # test_run() is now actually saved syncIns == 0, but will not reinitialize dbs.
        cantest = neverun and iscreating() and self.cli.isinstalled() and cansave
        self.ui.bTestRun.setEnabled(cantest)

        can_winsrv = cantest and Utils.iswindows()
        self.ui.bWinserv.setEnabled(can_winsrv)

    def showEvent(self, event: PySide6.QtGui.QShowEvent):
        def translateUI():
            self.ui.gboxRegistry.setTitle(
                synode_ui.langstrf('gboxRegistry', market=synode_ui.market))

            lb_help = synode_ui.langstr('lbHelplink')
            self.ui.lbHelplink.setText(f'<a href="{synode_ui.langs[synode_ui.lang]["help_link"]}">{lb_help}</a>.')
            self.ui.lblink.setText(f'Portfolio is based on <a href="{synode_ui.credits}">open source projects</a>.')

        super().showEvent(event)

        if event.type() == QEvent.Type.Show:
            translateUI()

            def setVolumePath():
                volpath = QFileDialog.getExistingDirectory(self, 'ResourcesPath')
                self.ui.txtVolpath.setText(volpath)

            if err_uihandlers[0] is None:
                err_uihandlers[0] = err_ctx

            self.ui.bSignup.clicked.connect(self.signup_demo)

            self.ui.cbbOrgs.currentIndexChanged.connect(self.select_community)
            self.ui.cbbDomains.currentIndexChanged.connect(self.select_domx)

            self.ui.bCreateDomain.clicked.connect(self.create_find_dom)

            # self.ui.chkHub.clicked.connect(self.update_chkhub)
            self.ui.chkHub.clicked.connect(self.enable_widgets)

            self.ui.cbbPeers.currentIndexChanged.connect(self.select_peer)
            self.ui.bVolpath.clicked.connect(setVolumePath)

            self.ui.bLogin.clicked.connect(self.login)
            self.ui.bPing.clicked.connect(self.pings)
            self.ui.bSetup.clicked.connect(self.save)

            self.ui.bTestRun.setEnabled(False)
            self.ui.bTestRun.clicked.connect(self.test_run)

            self.ui.chkReverseProxy.clicked.connect(self.update_chkreverse)

            if Utils.get_os() == 'Windows':
                self.ui.bWinserv.clicked.connect(self.installWinsrv)
            else:
                self.ui.bWinserv.setEnabled(False)

            self.bind_config()
            self.enable_widgets()

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
