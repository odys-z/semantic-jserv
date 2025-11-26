import sys
import time

from semanticshare.io.odysz.semantic.jprotocol import JServUrl, JProtocol

sys.stdout.reconfigure(encoding="utf-8")

import os

import io
from typing import Optional, cast

import PySide6
import qrcode
from PySide6.QtCore import QEvent
from PySide6.QtGui import QPixmap, Qt, QKeyEvent
from PySide6.QtWidgets import QApplication, QMainWindow, QFileDialog, QMessageBox, QLabel  #, QSpacerItem, QSizePolicy

from anson.io.odysz.common import Utils, LangExt
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException, getJservOption, AppSettings
from semanticshare.io.oz.syn.registry import AnRegistry, RegistResp, CynodeStats, SynodeConfig
from semanticshare.io.oz.syn import SynodeMode, Synode

from .commands import install_htmlsrv, install_wsrv_byname, winsrv_synode, winsrv_websrv
from .installer_api import InstallerCli, web_inf, settings_json, serv_port0, web_port0, err_uihandlers, \
    synode_ui, pths
from .install_jre import validate_jre, dowload_jre_gui
from . import SynodeUi, jre_mirror_key

# Important:
# Run the following command to generate the ui_form.py file
#     pyside6-uic form.ui -o ui_form.py
from .ui_form import Ui_InstallForm

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
    details[0] = e.format(args) if e is not None else e
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
        self.domains_arr = [] # query results for the org / community
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
        iport = self.cli.get_iport()

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
        if LangExt.len(self.ui.txtVolpath.text()) == 0:
            self.ui.txtVolpath.setText('vol')

    def show_validation(self, err: dict):
        if err is None or err == True:
            return True

        if 'exiftool' in err:
            if Utils.get_os() == 'Windows':
                Utils.warn('Installing exiftool failed.')
            else:
                err_msg('Install Exiftool from <a href="https://exiftool.org/">here</a> first.', err)
        else:
            err_msg('Validation failed.', err)

        return err

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
        self.cli.update_domain(
                orgtype=synode_ui.market_id,
                reg_jserv=self.ui.txtCentral.text().strip(),
                orgid=self.ui.cbbOrgs.currentText().strip(),
                domain=domainid)
        v = self.cli.validate_domain()
        if v is None:
            resp = self.cli.register()

            if resp is None:
                details.append(self.cli.settings.regiserv + '\n' + 'Error while rigstering.')
                errs = True
            else:
                if resp.r == RegistResp.R.domexists:
                    self.update_bind_domconf(resp)
                    errs = False # and bind
                    warn_msg("Domain already exists")
                elif resp.r == RegistResp.R.ok:
                    self.update_bind_domconf(resp)
                    errs = False
                    msg_box("Domain created: " + domainid)
                else:
                    warn_msg("Creating domain failed.")
        else:
            errs = True
            details.append(str(v))

        if has_err():
            warn_msg('Central service cannot be reached.', details)

        self.enable_widgets()

    def submit_jserv(self):
        """
        Call this after saving and validations are completed
        :return:
        """
        err_ready()
        resp = self.cli.submit_mysettings()
        global errs, details
        if resp == None or errs:
            warn_msg("Failed to submit registration.", details)
        else:
            # update jservs
            self.cli.after_submit(resp)
            self.bind_hubjserv(self.cli.registry.config, self.cli.settings)

    def pings(self):
        err_ready()
        jservss = self.ui.jservLines.toPlainText()
        if jservss is not None and len(jservss) > 8:
            jservss = InstallerCli.parsejservstr(jservss)

            for jsrv in jservss:
                mon_id = self.ui.cbbPeers.currentText()
                # if mon_id != self.cli.registry.config.synid:
                if not self.cli.is_hub(mon_id):
                    resp = self.cli.ping(jsrv[0])
                    if resp is None:
                        details.append(f'\n{mon_id}: {jsrv[0]}\n' + 'Error while pinging.')
                    else:
                        details.append(f'\n{mon_id}: {jsrv[0]}\n'  + resp.msg())

        if has_err():
            warn_msg('Ping synodes has errors. Check details for errors.', details)
        else:
            msg_box('Ping synodes completed. Check the echo messages in details.', details)
        return details

    def update_valid(self):

        self.default_ui_values()
        self.update()

        self.cli.updateWithUi(
            market=synode_ui.market_id,
            org=self.ui.cbbOrgs.currentText(),
            domain=self.ui.cbbDomains.currentText().strip(),
            reg_jserv=self.ui.txtCentral.text().strip(),
            admin=self.ui.txtAdminId.text(),
            # 0.7.7 central pswd is build by tasks.py
            # centralPswd=self.cli.matchPswds(self.ui.txtPswd.text(), self.ui.txtPswd2.text()),
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

        v = self.cli.validate()
        if v is not None:
            global errs, details
            details.append(v)
            errs = True
            self.show_validation(v)
        return v

    def check_install_jre(self):
        from semanticshare.io.oz.edge import Temurin17Release
        temurin = Temurin17Release()
        temurin.path = synode_ui.langstr(jre_mirror_key)
        if os.path.exists('proxy.json'):
            temurin.proxy = 'proxy.json'
        jreimg = temurin.set_jre()
        print('JRE:', jreimg)
        dowload_jre_gui(self, temurin)

    def save(self):
        err_ready()
        if self.update_valid() is not None:
            return

        if self.check_install_jre() is not None:
            return

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

            self.submit_jserv()

        except FileNotFoundError or IOError as e:
            # Changing vol path can reach here
            err_msg('Setting up synodepy3 is failed.', e)
        except PortfolioException as e:
            warn_msg('Configuration is updated with errors. Check the details.\n'
                     'If this is not switching volume, that is not correct' , e)
            self.gen_qr()

        self.enableWinsrvInstall()

    def test_run(self):
        syncins = self.cli.registry.config.syncIns
        self.cli.registry.config.syncIns = 0
        try:
            self.update_valid()
            self.cli.settings.save(pths.web_settings)

            msg_box('The settings is valid. You can close the opening terminal once you need to stop it.\n'
                'To stat the services, a stand alone running is recommended. Install the service on Windows or start:\n'
                'java -jar bin/jserv-album-#.#.#.jar\n'
                'java -jar bin/html-web-#.#.#.jar')

            if self.cli.httpd is None:
                self.cli.httpd, self.cli.webth = InstallerCli.start_web(int(self.ui.txtWebport.text()))
            self.cli.test_in_term()
            qr_data = self.gen_qr()
            print(qr_data)
            self.enableWinsrvInstall()

        except FileNotFoundError or IOError as e:
            # Changing vol path can reach here
            err_msg('Setting up synodepy3 is failed.', e)
        except PortfolioException as e:
            self.ui.lbQr.clear()
            err_msg('Start Portfolio service failed', e.msg)
        finally:
            self.cli.registry.config.syncIns = syncins
            self.cli.settings.save(pths.web_settings)

        time.sleep(0.2)
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
        self.enable_widgets()

    def update_bind_domconf(self, resp: RegistResp):
        myid = self.cli.registry.config.synid
        if not LangExt.isblank(myid) and myid != resp.diction.synid:
            Utils.warn('Server replied with a different synode id {} other than {}',
                       resp.diction.synid, myid)

        self.cli.registry.config.overlay(resp.diction)

        binding_synode = resp.next_installing() if self.cli.registry.find_peer(myid) is None else myid
        self.bind_cbbpeers(peers=self.cli.registry.config.peers, select_id=binding_synode)

        self.cli.settings.acceptj_butme(binding_synode, self.cli.registry.config.peers)
        self.bind_hubjserv(self.cli.registry.config, self.cli.settings)

    def select_community(self, commuix):
        domx = self.query_domx(self.ui.cbbOrgs.currentText())
        self.ui.cbbDomains.clear()
        if domx is not None:
            self.domains_arr = domx.domains()
            my_domid = self.cli.registry.config.domain
            self.ui.cbbDomains.addItems(self.domains_arr)

            # avoid change my domain id by select_domx()
            if not LangExt.isblank(my_domid):
                self.ui.cbbDomains.setCurrentText(my_domid)
                self.select_domx(my_domid)
            # self.cli.registry.config.domain = my_domid

    def select_domx(self, dix):
        domid = self.ui.cbbDomains.currentText()
        if domid not in self.domains_arr: return

        orgid = self.ui.cbbOrgs.currentText()
        resp  = self.cli.query_domconf(commuid=orgid, domid=domid)
        if resp is not None:
            self.update_bind_domconf(resp)

    def select_peer_byid(self, synid):
        try:
            pr = self.cli.registry.find_peer(synid)
            self.bind_synode(pr)
        except: pass

    def select_peer(self, idx):
        """
        Actually doing nothing as there is nothing from config.peers[x] to be bound to ui.
        :param idx:
        :return: None
        """
        try: self.bind_synode(self.cli.registry.config.peers[idx])
        except Exception as _:
            pass

    def bind_synode(self, n: Synode):
        peer = self.cli.find_peer(n.synid)
        chk = peer.remarks == SynodeMode.hub.name
        self.ui.chkHub.setChecked(chk)
        self.enable_widgets()

        hub_srv = self.cli.registry.find_hubpeer().jserv
        if JServUrl.valid(hub_srv):
            self.ui.jservLines.setText(hub_srv)

    def update_chkreverse(self, check: bool):
        if check is None:
            check = self.ui.chkReverseProxy.checkState() == Qt.CheckState.Checked
        else:
            self.ui.chkReverseProxy.setChecked(check)

        self.ui.txtIP_proxy.setEnabled(check)
        self.ui.txtIP_proxy.setEnabled(check)

        self.ui.txtWebport_proxy.setEnabled(check)
        if check and LangExt.isblank(self.ui.txtWebport_proxy.text(), '0'):
            self.ui.txtWebport_proxy.setText(str(self.cli.settings.webport))

        self.ui.txtPort_proxy.setEnabled(check)
        if check and LangExt.isblank(self.ui.txtPort_proxy.text(), '0'):
            self.ui.txtPort_proxy.setText(str(self.cli.settings.port))

    def bind_config(self):
        self.cli.registry = self.cli.load_settings()
        self.cli.registry = InstallerCli.loadRegistry(self.cli.settings.volume, 'registry')
        self.bindIdentity(self.cli.registry, synodeui=synode_ui)
        self.bindSettings()
        self.enable_widgets()

    def bind_cbborg(self, orgs: list[str], elect: str):
        self.ui.cbbOrgs.clear()
        self.ui.cbbOrgs.addItems(orgs)
        self.ui.cbbOrgs.setCurrentText(elect)

    def bind_cbbpeers(self, peers: list[Synode], select_id):
        self.ui.cbbPeers.clear()
        if peers is not None:
            self.ui.cbbPeers.addItems([s.synid for s in peers if s is not None])

        # if select_id is not None:
        if not LangExt.isblank(select_id):
            self.ui.cbbPeers.setCurrentText(select_id)
            self.select_peer_byid(select_id)

    def bindIdentity(self, registry: AnRegistry, synodeui: SynodeUi):
        cfg = registry.config
        cfg.org.orgType = synodeui.market_id
        print(cfg.toBlock())

        self.ui.txtAdminId.setText(cfg.admin)

        # self.ui.txtPswd.setText(registry.synusers[0].pswd)
        # self.ui.txtPswd2.setText(registry.synusers[0].pswd)
        self.ui.txtDompswd.setText(registry.synusers[0].pswd)

        self.bind_cbborg([cfg.org.orgId], cfg.org.orgId)
        self.ui.cbbDomains.setCurrentText(cfg.domain)

        u = self.cli.find_synuser(cfg.admin)
        if u is not None:
            self.ui.txtDompswd.setText(u.pswd)

        self.ui.chkHub.setChecked(SynodeMode.hub.name == cfg.mode)
        self.bind_cbbpeers(cfg.peers, cfg.synid)

        sync_insnds = cfg.syncIns
        try: sync_insnds = str(int(float(sync_insnds)))
        except: sync_insnds = '0'
        self.ui.txtSyncIns.setText('0' if sync_insnds is None else sync_insnds)

    def bind_hubjserv(self, cfg: SynodeConfig, settings: AppSettings):
        '''
        This noly bind settings.jservs[hub] to ui.jservLines. Call settings.accept(cfg.peers) first.
        :param cfg:
        :param settings:
        :return:
        '''
        if cfg is not None and LangExt.len(cfg.peers) > 0:
            for p in cfg.peers:
                if p.remarks == SynodeMode.hub.name:
                    jsrv = settings.jservs[p.synid] if p.synid in settings.jservs else None
                    self.ui.jservLines.setText(f'http://127.0.0.1:{serv_port0}/{JProtocol.urlroot}' \
                        if LangExt.isblank(jsrv) else jsrv)

    def bindSettings(self):
        peers, settings = self.cli.registry.config.peers, self.cli.settings

        self.ui.txtCentral.setText(settings.regiserv)
        self.ui.txtPswd.setText(settings.centralPswd)
        self.ui.txtPswd2.setText(settings.centralPswd)

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

            settings.acceptj_butme(cfg.synid, cfg.peers)
            self.bind_hubjserv(cfg, settings)

        else:
            self.ui.jservLines.setText(
                '# Error: No configuration has been loaded. Check resource root path setting.')

    def iscreating_state(self):
        """
        The synode id is correct and is not installed
        :return:
        """
        next_id = self.ui.cbbPeers.currentText()
        le_node = self.cli.registry.find_peer(next_id)
        # if le_node is not None and (le_node.stat is None or le_node.stat == CynodeStats.create):
        if le_node is None or le_node.stat is None or le_node.stat == CynodeStats.create:
            return self.cli.validateVol() is None
        else:
            return False

    def enableWinsrvInstall(self):
        """
        replaced by enable_widgets()
        :return:
        """
        jre_ready = validate_jre()
        installed = self.cli.vol_valid()
        neverun = not self.cli.hasrun()
        iscreate = self.iscreating_state()
        if Utils.iswindows():
            self.ui.bWinserv.setEnabled(jre_ready and installed and neverun and iscreate)

    def seal_has_run(self):
        """
        replaced by enable_widgets()
        :return:
        """

        def can_save():
            """
            The synode id is correct and is not installed
            :return:
            """
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

        def update_chkhub(check: bool):
            self.cli.registry.config.mode = SynodeMode.hub.name if check else SynodeMode.peer.name

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
        # self.ui.txtDompswd.setEnabled(neverun) # can change in the future
        self.ui.bCreateDomain.setEnabled(neverun)

        self.cli.update_domain(orgtype=synode_ui.market_id,
                               domain=self.ui.cbbDomains.currentText(),
                               orgid=self.ui.cbbOrgs.currentText())
        # valid_peers = self.cli.is_peers_valid()
        # self.ui.cbbPeers.setEnabled(valid_peers)
        self.ui.cbbPeers.setEnabled(neverun)
        self.ui.bVolpath.setEnabled(neverun)
        self.ui.txtVolpath.setEnabled(neverun)

        update_chkhub(self.ui.chkHub.checkState() == Qt.CheckState.Checked)

        # cansave = valid_peers and (neverun or self.cli.registry.config.synid == self.ui.cbbPeers.currentText())
        cansave = self.iscreating_state()
        self.ui.bSetup.setEnabled(cansave)

        # test_run() is now actually saved syncIns == 0, but will not reinitialize dbs.
        # cantest = neverun and iscreating_state() and self.cli.vol_valid() and cansave
        cantest = neverun and self.iscreating_state() and self.cli.vol_valid()
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

        if event.type() == QEvent.Type.Show and self.cli.registry is None:
            translateUI()

            def setVolumePath():
                volpath = QFileDialog.getExistingDirectory(self, caption='Volume Path')
                self.ui.txtVolpath.setText(volpath)

            if err_uihandlers[0] is None:
                err_uihandlers[0] = err_ctx

            self.ui.txtCentral.installEventFilter(self)
            self.ui.bSignup.clicked.connect(self.signup_demo)

            self.ui.cbbOrgs.currentIndexChanged.connect(self.select_community)
            self.ui.cbbDomains.currentIndexChanged.connect(self.select_domx)
            self.ui.bCreateDomain.clicked.connect(self.create_find_dom)

            self.ui.chkHub.clicked.connect(self.enable_widgets)
            self.ui.cbbPeers.currentIndexChanged.connect(self.select_peer)
            self.ui.bVolpath.clicked.connect(setVolumePath)

            self.ui.bLogin.clicked.connect(self.login)
            self.ui.bPing.clicked.connect(self.pings)

            # self.ui.bSetup.clicked.connect(self.save)
            self.ui.bSetup.clicked.connect(self.check_install_jre)

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

    def eventFilter(self, obj, event):
        if event.type() == QEvent.Type.KeyPress and obj is self.ui.txtCentral:
            key = cast(QKeyEvent, event).key()
            if self.ui.txtCentral.hasFocus() and \
               (key == Qt.Key.Key_Return or key == Qt.Key.Key_Enter):
                txt = self.ui.txtCentral.text()
                if JServUrl.valid(jserv=txt, rootpath=synode_ui.central_path):
                    self.cli.settings.regiserv = txt
                    communs, communid = self.cli.query_orgs()
                    self.bind_cbborg(communs, communid)
        return super().eventFilter(obj, event)


if __name__ == "__main__":
    app = QApplication(sys.argv)
    widget = InstallerForm()
    widget.show()
    sys.exit(app.exec())
