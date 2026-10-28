import sys
import time

# Before anything imports PySide6: on Linux, Qt needs system libraries pip can't install (libEGL.so.1, ...).
from synodepy3.prerequisites import check_prerequisites
_missing = check_prerequisites(gui=True)
if _missing and 'Qt runtime' in _missing:
    print(_missing['Qt runtime'], file=sys.stderr)
    sys.exit(1)
# The other items, e.g. exiftool, are checked again by InstallerCli.validate() before installing.

from semanticshare.io.odysz.semantic.jprotocol import JServUrl

from synodepy3.jre_downloader import JreDownloader
from synodepy3.ports_dialog import find_ports

sys.stdout.reconfigure(encoding="utf-8")

import os

import io
from typing import Optional, cast

import PySide6
import qrcode
from PySide6.QtCore import QEvent, QSignalBlocker
from PySide6.QtGui import QPixmap, Qt, QKeyEvent
from PySide6.QtWidgets import QApplication, QMainWindow, QFileDialog, QMessageBox, QLabel  #, QSpacerItem, QSizePolicy

from anson.io.odysz.common import Utils, LangExt
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException, getJservOption, AppSettings
from semanticshare.io.oz.syn.registry import AnRegistry, RegistResp, CynodeStats, SynodeConfig
from semanticshare.io.oz.syn import SynodeMode, Synode

from synodepy3.winsrv import install_winsrvs, winsrvs_installed
from synodepy3.installer_api import InstallerCli, web_inf, settings_json, serv_port0, web_port0, err_uihandlers, \
    synode_ui, cfgpaths, posix_path, desktop_exe, start_desktop, readable_state, state_mark
from synodepy3.install_jre import validate_jre
from synodepy3.jre_downloader import _event_loop_interval_

# Important:
# Run the following command to generate the ui_form.py file
#     pyside6-uic form.ui -o ui_form.py
from synodepy3.ui_form import Ui_InstallForm


# DEBUG-WINSRV begin: temporary tracing of bWinserv enabling, remove later
def _trace_winsrv(form, where: str):
    def _try(f):
        try: return f()
        except Exception as e: return f'<{type(e).__name__}: {e}>'

    def _dbs():
        sysdb, syndb, _ = InstallerCli.sys_syn_db_syntity(form.cli.settings.Volume())
        return {p: os.path.getsize(p) if os.path.exists(p) else 'missing' for p in (sysdb, syndb)}

    def _peer():
        pid = form.cbb_synid()
        n = form.cli.registry.find_peer(pid)
        return f'{pid}: ' + ('not found' if n is None else f'stat={n.stat!r}')

    dl = form.jredownloader
    print(f'[DEBUG-WINSRV] @{where}\n'
          f'  iswindows        = {_try(Utils.iswindows)}\n'
          f'  validate_jre()   = {_try(validate_jre)}\n'
          f'  isjre_ready()    = {_try(form.cli.isjre_ready)}\n'
          f'  jredownloader    = {dl!r}\n'
          f'  volume           = {_try(form.cli.settings.Volume)}\n'
          f'  validateVol()    = {_try(form.cli.validateVol)}\n'
          f'  installkey       = {_try(lambda: form.cli.settings.installkey)!r}\n'
          f'  rootkey          = {_try(lambda: form.cli.settings.rootkey)!r}\n'
          f'  db sizes         = {_try(_dbs)}\n'
          f'  hasrun()         = {_try(form.cli.hasrun)}\n'
          f'  peer             = {_try(_peer)}\n'
          f'  iscreating_state = {_try(form.iscreating_state)}\n'
          f'  chk_synid        = {_try(lambda: form.cli.chk_synid(form.cbb_synid()))}\n'
          f'  chk_srv_install  = {_try(lambda: form.cli.chk_srv_install(form.cbb_synid(), lambda: winsrvs_installed(form.cli)))}\n'
          f'  bSetup.enabled   = {form.ui.bSetup.isEnabled()}\n'
          f'  bWinserv.enabled = {form.ui.bWinserv.isEnabled()}',
          flush=True)
# DEBUG-WINSRV end

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
    ui: Ui_InstallForm
    cli: InstallerCli
    jredownloader: Optional[JreDownloader]

    def __init__(self, parent=None):
        super().__init__(parent)
        self.domains_arr = [] # query results for the org / community
        self.ui = Ui_InstallForm()
        self.ui.setupUi(self)
        self.cli = InstallerCli()
        self.jredownloader = None

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

    def on_find_ports(self):
        """
        The "find ports?" button. The dialog only returns the picked ports; on OK they are
        updated into cli.settings, then the settings are bound to the UI.
        settings.json is still only written by save().
        """
        settings = self.cli.settings
        ok, webport, port = find_ports(self, settings.webport, settings.port)
        if not ok:
            return

        settings.webport, settings.port = webport, port

        self.bind_ports(settings)
        # self.gen_qr()  # the QR / jserv option uses get_iport() -> settings.port

    def bind_ports(self, settings: AppSettings):
        self.ui.txtPort.setText(str(settings.port))
        self.ui.txtWebport.setText(str(settings.webport))

    def default_ui_values(self):
        if LangExt.len(self.ui.txtSyncIns.text()) == 0:
            self.ui.txtSyncIns.setText('120.0')
        if LangExt.len(self.ui.txtPort.text()) == 0:
            self.ui.txtPort.setText(serv_port0)
        if LangExt.len(self.ui.txtWebport.text()) == 0:
            self.ui.txtWebport.setText(web_port0)
        if LangExt.len(self.ui.txtVolpath.text()) == 0:
            # MEMO: a relative path is only a UI default; it is saved as an absolute path by
            #   InstallerCli.updateWithUi(volume=...), which the synode and the upgrade require.
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
        resp = self.cli.query_domx(market=self.cli.settings.market_id, commu=commuid)
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
                orgtype=self.cli.settings.market_id,
                reg_jserv=self.ui.txtCentral.text().strip(),
                orgid=self.ui.cbbOrgs.currentText().strip(),
                domain=domainid)
        # a new domain's id is shorter: the central names its synodes <domain>-hub, ...
        v = self.cli.validate_domain(creating=domainid not in self.domains_arr)
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
                mon_id = self.cbb_synid()
                if not self.cli.is_hub(mon_id):
                    print(f'Ping with ui timeout:', int(self.ui.txtimeout.text()))
                    resp = self.cli.ping(jsrv[0], timeout=int(self.ui.txtimeout.text()))
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
            market=self.cli.registry.config.org.orgType,
            org=self.ui.cbbOrgs.currentText(),
            domain=self.ui.cbbDomains.currentText().strip(),
            reg_jserv=self.ui.txtCentral.text().strip(),
            admin=self.ui.txtAdminId.text(),
            # 0.7.7 central pswd is build by tasks.py
            # centralPswd=self.cli.matchPswds(self.ui.txtPswd.text(), self.ui.txtPswd2.text()),
            domphrase=self.ui.txtDompswd.text(),
            hubmode=self.ui.chkHub.checkState() == Qt.CheckState.Checked,
            jservss=self.ui.jservLines.toPlainText(),
            synid=self.cbb_synid(),
            syncins=self.ui.txtSyncIns.text(),
            port=self.ui.txtPort.text(),
            webport=self.ui.txtWebport.text(),
            webProxyPort=self.ui.txtWebport_proxy.text(),
            reverseProxy=self.ui.chkReverseProxy.checkState() == Qt.CheckState.Checked,
            proxyIp=self.ui.txtIP_proxy.text(),
            proxyPort=self.ui.txtPort_proxy.text(),
            volume=posix_path(self.ui.txtVolpath.text()))

        v = self.cli.validate(ping_timeout=int(self.ui.txtimeout.text()))
        if v is not None:
            global errs, details
            details.append(v)
            errs = True
            self.show_validation(v)
        return v

    def check_install_jre(self):
        '''
        :return:
        '''
        self.jredownloader = self.cli.check_install_jre(self.jredownloader, self.ui.lbQr)

    def save(self):
        err_ready()

        # A running synode id ('p' or 'h' at the central) can't be selected, saved or installed.
        # Once this volume has run, the id is locked (see enable_widgets()) and other settings can be saved.
        if not self.cli.hasrun():
            v = self.cli.chk_synid(self.cbb_synid())
            if v is not None:
                warn_msg('The synode id is already running somewhere. Nothing is saved.', v)
                _trace_winsrv(self, 'save: chk_synid(), returned') # DEBUG-WINSRV
                return

        try:
            if self.update_valid() is not None:
                _trace_winsrv(self, 'save: update_valid() failed, returned') # DEBUG-WINSRV
                return
        except Exception as e:
            # A slot's exception is silent in the windowed setup-gui.exe.
            err_msg('Checking the settings failed.', e)
            _trace_winsrv(self, 'save: update_valid() raised, returned') # DEBUG-WINSRV
            return

        if self.check_install_jre() is not None:
            _trace_winsrv(self, 'save: check_install_jre() not None, returned') # DEBUG-WINSRV
            return
        _trace_winsrv(self, 'save: after check_install_jre()') # DEBUG-WINSRV

        try:
            self.cli.install()
            _trace_winsrv(self, 'save: after cli.install()') # DEBUG-WINSRV

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
            _trace_winsrv(self, 'save: after submit_jserv()') # DEBUG-WINSRV

            h = self.cli.chk_hub()
            if h is not None:
                warn_msg('Hub synode is missing.', h)

        except FileNotFoundError or IOError as e:
            # Changing vol path can reach here
            err_msg('Setting up synodepy3 is failed.', e)
            _trace_winsrv(self, f'save: except {type(e).__name__}: {e}') # DEBUG-WINSRV
        except PortfolioException as e:
            warn_msg('Configuration is updated with errors. Check the details.\n'
                     'If this is not switching volume, that is not correct' , e)
            self.gen_qr()
            _trace_winsrv(self, f'save: except PortfolioException: {e}') # DEBUG-WINSRV

        self.enable_widgets()

    def test_run(self):
        syncins = self.cli.registry.config.syncIns
        self.cli.registry.config.syncIns = 0
        try:
            self.update_valid()
            self.cli.settings.save(cfgpaths.web_settings)

            msg_box('The settings is valid. You can close the opening terminal once you need to stop it.\n'
                'To stat the services, a stand alone running is recommended. Install the service on Windows or start:\n'
                'java -jar bin/jserv-album-#.#.#.jar\n'
                'java -jar bin/html-web-#.#.#.jar')

            if self.cli.httpd is None:
                self.cli.httpd, self.cli.webth = InstallerCli.start_web(int(self.ui.txtWebport.text()))
            self.cli.test_in_term()
            qr_data = self.gen_qr()
            print(qr_data)
            self.enable_widgets()

        except FileNotFoundError or IOError as e:
            # Changing vol path can reach here
            err_msg('Setting up synodepy3 is failed.', e)
        except PortfolioException as e:
            self.ui.lbQr.clear()
            err_msg('Start Portfolio service failed', e.msg)
        finally:
            self.cli.registry.config.syncIns = syncins
            self.cli.settings.save(cfgpaths.web_settings)

        time.sleep(0.2)
        self.bind_config()

    def installWinsrv(self):
        v = self.cli.chk_srv_install(self.cbb_synid(), lambda: winsrvs_installed(self.cli))
        if v is not None:
            warn_msg('The services cannot be installed.', v)
            self.enable_widgets()
            return

        h = self.cli.chk_hub()
        if h is not None:
            warn_msg('Hub synode is incorrect, need to reset again when the hub node is running.', h)

        self.cli.stop_web()
        msg_box('Installing Windows Services...\n'
                'Click Ok to continue, and confirm the administrator permission request\n'
                '(asked only once, the dialog can be hidden).')

        try:
            states = install_winsrvs(self.cli)
        except (PermissionError, OSError) as e:
            err_msg('Installing Windows services failed.', e)
            self.enable_widgets()
            return

        failed = [s for s, ok in states.items() if not ok]
        if failed:
            err_msg(f'Service(s) not installed: {", ".join(failed)}\n'
                    'Check winsrv-install.log in current folder.')
            self.enable_widgets()
            return

        self.gen_qr()

        msg_box('Services installed. You can check in Windows Service Control, or logs in current folder.\n'
                'Restart the computer if the service starting failed due to binding ports, by which you started tests early.')

        self.enable_widgets()
        self.ask_start_desktop()

    def ask_start_desktop(self):
        '''
        Services are installed: ask for starting the desktop app (desktop/, if packaged), which runs on its own.
        '''
        if desktop_exe() is None:
            return
        yes = QMessageBox.StandardButton.Yes
        if QMessageBox.question(self, 'Portfolio Desktop', 'Start Portfolio Desktop now?',
                                yes | QMessageBox.StandardButton.No, yes) == yes:
            try:
                start_desktop()
            except OSError as e:
                err_msg('Starting Portfolio Desktop failed. Start it later with desktop/album_gui.exe.', e)

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

    def select_community(self, commuix: Optional[str] = None):
        # domx = self.query_domx(self.ui.cbbOrgs.currentText())
        if commuix is None:
            commuix = self.ui.cbbOrgs.currentText()

        domx = self.query_domx(commuix)

        self.domains_arr = []
        prev_domid = self.cli.registry.config.domain
        with QSignalBlocker(self.ui.cbbDomains):
            self.ui.cbbDomains.clear()
            if domx is not None and LangExt.len(domx.domains()) > 0:
                self.domains_arr = domx.domains()
                self.ui.cbbDomains.addItems(self.domains_arr)

                # avoid change my domain id by select_domx()
                if not LangExt.isblank(prev_domid) and prev_domid in self.domains_arr:
                    self.ui.cbbDomains.setCurrentText(prev_domid)
            else:
                self.ui.cbbDomains.setCurrentText('')

        if LangExt.len(self.domains_arr) > 0 and \
                (LangExt.isblank(prev_domid) or prev_domid not in self.domains_arr):
            # A blank or stale domain id, e.g. the package's registry template's, is not one of the community's:
            # take the first one and load its synodes from the central, so that peers are never left empty.
            with QSignalBlocker(self.ui.cbbDomains):
                self.ui.cbbDomains.setCurrentText(self.domains_arr[0])
            self.select_domid(0)

    def select_domid(self, dix):
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

    def select_peer(self, idx: int):
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
        if self.cli.reg_jserv.valid(hub_srv):
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
        self.bindIdentity(self.cli.registry, settings=self.cli.settings)
        self.bindSettings()
        self.enable_widgets()

    def bind_cbborg(self, orgs: list[str], elect: str):
        with QSignalBlocker(self.ui.cbbOrgs):
            self.ui.cbbOrgs.clear()
            self.ui.cbbOrgs.addItems(orgs)
            self.ui.cbbOrgs.setCurrentText(elect)
        self.select_community(self.ui.cbbOrgs.currentText())

    def bind_cbbpeers(self, peers: list[Synode], select_id):
        """
        Items are shown as "<state mark> synid", with the synode id as the item's data,
        and readable_state() as the tooltip, like the synode id choices in prompt.py.
        Use cbb_synid() for the selected id.
        """
        self.ui.cbbPeers.clear()
        if peers is not None:
            for s in peers:
                if s is None: continue
                mark = state_mark(s.stat)
                self.ui.cbbPeers.addItem(f'{mark} {s.synid}' if mark else s.synid, s.synid)
                self.ui.cbbPeers.setItemData(self.ui.cbbPeers.count() - 1,
                                             readable_state(s.stat), Qt.ItemDataRole.ToolTipRole)

        # if select_id is not None:
        if not LangExt.isblank(select_id):
            ix = self.ui.cbbPeers.findData(select_id)
            if ix >= 0:
                self.ui.cbbPeers.setCurrentIndex(ix)
            self.select_peer_byid(select_id)

    def cbb_synid(self) -> str:
        """
        The selected synode id of cbbPeers, without the state mark.
        """
        synid = self.ui.cbbPeers.currentData()
        return synid if synid is not None else self.ui.cbbPeers.currentText().strip()

    def bindIdentity(self, registry: AnRegistry, settings: AppSettings):
        cfg = registry.config
        # in 0.8.0, orgType is configured by tasks.py
        # cfg.org.orgType = settings.market_id
        print(cfg.toBlock())

        self.ui.txtAdminId.setText(cfg.admin)
        self.ui.txtDompswd.setText(registry.synusers[0].pswd)

        self.bind_cbborg([cfg.org.orgId], cfg.org.orgId)
        # select_community() has selected the configured domain, or the community's first one if the
        # configured is not in the central. Only show the configured one if the central can't be reached.
        if LangExt.len(self.domains_arr) == 0:
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
                    # self.ui.jservLines.setText(self.cli.syn_jserv if LangExt.isblank(jsrv) else jsrv)
                    self.ui.jservLines.setText(jsrv)

    def bindSettings(self):
        peers, settings = self.cli.registry.config.peers, self.cli.settings

        self.ui.txtCentral.setText(settings.regiserv)
        self.ui.txtPswd.setText(settings.centralPswd)
        self.ui.txtPswd2.setText(settings.centralPswd)

        self.bind_ports(settings)

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
        next_id = self.cbb_synid()
        le_node = self.cli.registry.find_peer(next_id)
        # if le_node is not None and (le_node.stat is None or le_node.stat == CynodeStats.create):
        if le_node is None or le_node.stat is None or le_node.stat == CynodeStats.create:
            return self.cli.validateVol() is None
        else:
            return False

    def enableWinsrvInstall(self):
        """
        Deprecated, not called: replaced by enable_widgets()
        :return:
        """
        jre_ready = validate_jre() == None
        installed = self.cli.vol_valid()
        neverun = not self.cli.hasrun()
        iscreate = self.iscreating_state()
        if Utils.iswindows():
            self.ui.bWinserv.setEnabled(jre_ready and installed and neverun and iscreate)
        print(f'[DEBUG-WINSRV] enableWinsrvInstall(): jre_ready={jre_ready}, installed={installed}, '
              f'neverun={neverun}, iscreate={iscreate}', flush=True) # DEBUG-WINSRV
        _trace_winsrv(self, 'enableWinsrvInstall() end') # DEBUG-WINSRV

    def seal_has_run(self):
        """
        Deprecated, not called: replaced by enable_widgets()
        :return:
        """

        def can_save():
            """
            The synode id is correct and is not installed
            :return:
            """
            next_id = self.cbb_synid()
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
        """
        The only place updating the widgets' states (until the validator pattern applies to all widgets).
        Call this after any data / state change, instead of setting the widgets elsewhere.

        - Save is always enabled: save() refuses a running synode id (InstallerCli.chk_synid()), and
          once this volume has run, the synode id, domain & volume are locked, other settings can be saved.
        - Installing services follows InstallerCli.chk_srv_install(), the same as setup-cli.
        """

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

        self.cli.update_domain(orgtype=self.cli.settings.market_id,
                               domain=self.ui.cbbDomains.currentText(),
                               orgid=self.ui.cbbOrgs.currentText())
        # valid_peers = self.cli.is_peers_valid()
        # self.ui.cbbPeers.setEnabled(valid_peers)
        self.ui.cbbPeers.setEnabled(neverun)
        self.ui.bVolpath.setEnabled(neverun)
        self.ui.txtVolpath.setEnabled(neverun)

        update_chkhub(self.ui.chkHub.checkState() == Qt.CheckState.Checked)

        # cansave = valid_peers and (neverun or self.cli.registry.config.synid == self.ui.cbbPeers.currentText())

        self.ui.bSetup.setEnabled(True)

        # test_run() is now actually saved syncIns == 0, but will not reinitialize dbs.
        # cantest = neverun and iscreating_state() and self.cli.vol_valid() and cansave

        # cantest = neverun and self.iscreating_state() and self.cli.vol_valid() and jre_ready
        # self.ui.bTestRun.setEnabled(cantest)
        self.ui.bTestRun.setEnabled(False)

        if Utils.iswindows():
            srv_err = self.cli.chk_srv_install(self.cbb_synid(), lambda: winsrvs_installed(self.cli))
        else:
            srv_err = {'os': 'Windows services only.'}
        self.ui.bWinserv.setEnabled(srv_err is None)
        self.ui.bWinserv.setToolTip('' if srv_err is None else '\n'.join(str(v) for v in srv_err.values()))

        # ISSUE 2026-03-02
        # The service is registered by Procrun.exe as \SOFTWARE\WOW6432Node\Apache Software Foundation\Procrun 2.0\...,
        # rather than HKLM\SYSTEM\CurrentControlSet\Services\Synode-7.10-service-id.
        # That makes the services lost after Windows updated.
        # Brutally re-install and start the service can solve the problem, and files are synchronized.
        # 2026-10-17: services can't be re-installed by setup-gui / setup-cli, only by upgrading.
        _trace_winsrv(self, 'enable_widgets() end') # DEBUG-WINSRV

    def showEvent(self, event: PySide6.QtGui.QShowEvent):
        def translateUI():
            from synodepy3.__version__ import synode_ver
            self.setWindowTitle(f'Portfolio Synode {synode_ver}')

            self.ui.gboxRegistry.setTitle(synode_ui.langstrf(
                    'gboxRegistry', market=self.cli.settings.market_id))

            lb_help = synode_ui.langstr('lbHelplink')
            self.ui.lbHelplink.setText(f'<a href="{synode_ui.langs[synode_ui.lang]["help_link"]}">{lb_help}</a>.')
            self.ui.lblink.setText(f'Portfolio is based on <a href="{synode_ui.credits}">open source projects</a>.')

        super().showEvent(event)

        if event.type() == QEvent.Type.Show: # FIXME suspicious check: and self.cli.registry is None:

            def setVolumePath():
                volpath = QFileDialog.getExistingDirectory(self, caption='Volume Path')
                if not LangExt.isblank(volpath):
                    self.ui.txtVolpath.setText(posix_path(volpath))

            if err_uihandlers[0] is None:
                err_uihandlers[0] = err_ctx

            self.ui.txtCentral.installEventFilter(self)
            self.ui.bSignup.clicked.connect(self.signup_demo)

            self.ui.cbbOrgs.currentIndexChanged.connect(self.select_community)
            self.ui.cbbDomains.currentIndexChanged.connect(self.select_domid)
            self.ui.bCreateDomain.clicked.connect(self.create_find_dom)

            self.ui.chkHub.clicked.connect(self.enable_widgets)
            self.ui.cbbPeers.currentIndexChanged.connect(self.select_peer)
            self.ui.bVolpath.clicked.connect(setVolumePath)
            self.ui.bFindPorts.clicked.connect(self.on_find_ports)

            self.ui.bLogin.clicked.connect(self.login)
            self.ui.bPing.clicked.connect(self.pings)

            self.ui.bSetup.clicked.connect(self.save)
            self.ui.chkReverseProxy.clicked.connect(self.update_chkreverse)

            if Utils.get_os() == 'Windows':
                self.ui.bWinserv.clicked.connect(self.installWinsrv)

            self.bind_config()
            translateUI()
            self.enable_widgets()

    def closeEvent(self, event: PySide6.QtGui.QCloseEvent):
        super().closeEvent(event)

        if self.jredownloader:
            self.jredownloader.cancel_download()
            # QApplication.processEvents()
            time.sleep(_event_loop_interval_)
            self.jredownloader.cleanup('Close event: stop jre downloader...')

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
                if self.cli.reg_jserv.is_valid(txt): # check protocol path
                    self.cli.settings.regiserv = txt
                    self.cli.reg_jserv = JServUrl(txt)
                    communs, communid = self.cli.query_orgs()
                    self.bind_cbborg(communs, communid)
                    self.ui.statusbar.showMessage("Ready")
                else:
                    # synode_ui.status.text = "Invalid Registry url"
                    self.ui.statusbar.showMessage("Invalid Registry url")
        return super().eventFilter(obj, event)


def main():
    """
    Entry-point: synode-gui (pyproject.toml [project.scripts])
    """
    app = QApplication(sys.argv)
    widget = InstallerForm()
    widget.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
