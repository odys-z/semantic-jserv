"""
The "find ports?" dialog of setup-gui: lists the available / used TCP ports on this host, and checks
the web and synode ports being configured.

The dialog knows neither the caller's widgets nor its data module: it takes two port numbers and
returns the picked ones. The caller updates its data (e.g. InstallerCli.settings) on OK, then binds
the data to its UI, e.g. in setup-gui:

    ok, webport, port = find_ports(self, settings.webport, settings.port)
    if ok:
        settings.webport, settings.port = webport, port
        self.bind_ports(settings)
"""
import io
from typing import Optional, Tuple

from PySide6.QtCore import QRegularExpression, QTimer, Qt
from PySide6.QtGui import QFontDatabase, QRegularExpressionValidator
from PySide6.QtWidgets import (QApplication, QDialog, QDialogButtonBox, QGridLayout, QLabel, QLineEdit,
                               QPlainTextEdit, QVBoxLayout, QWidget)
from semanticshare.io.oz.jserv.docs.syn.singleton import valid_url_port

from synodepy3.get_avail_ports import (check_port, describe_process, is_listening, report_port_ranges,
                                       synode_label, tcp_port_owners, unowned_note, IS_MAC, IS_WIN)

port_min, port_max = 1024, 65535


def port_status(port: int, host: str = "127.0.0.1") -> Tuple[bool, str]:
    """
    Check one port, as get_avail_ports does for the report.
    :return: (free, text), e.g. (False, "❌ in use: pid 1234 Synode.web-0.8.0-pm-3-hub (web)")
    """
    free, err = check_port(port, host)
    try:
        owners = tcp_port_owners()
    except Exception:  # psutil missing, or denied
        owners = None

    # Windows / macOS: a bind on 127.0.0.1 can succeed while another program listens on 0.0.0.0 or [::].
    if free and (IS_WIN or IS_MAC) and owners is not None and is_listening(owners, port):
        free = False
    if free:
        return True, "✅ available"

    pids = (owners or {}).get(port)
    if not pids:
        return False, f"❌ in use {unowned_note(err)}"
    holders = []
    for pid, status in pids.items():
        label = synode_label(pid, port, status) if pid is not None else None
        holders.append(f"pid {pid if pid is not None else '?'} {label or describe_process(pid)}")
    return False, "❌ in use: " + "; ".join(holders)


class PortsDialog(QDialog):
    """
    Lists the available ports, and lets the user check & pick the web port and the synode (jserv) port.
    A port is checked when its box loses focus (or on Enter). Read the picked ports with webport()
    and port() after exec() returns Accepted.
    """

    def __init__(self, parent: Optional[QWidget] = None, webport: str = '', port: str = ''):
        super().__init__(parent)
        self.setWindowTitle("Find available ports")
        self.resize(640, 520)

        self.txtWebport = self._number_box(webport)
        self.txtPort = self._number_box(port)
        self.lbWebport = self._status_label()
        self.lbPort = self._status_label()

        grid = QGridLayout()
        grid.addWidget(QLabel("Web port"), 0, 0)
        grid.addWidget(self.txtWebport, 0, 1)
        grid.addWidget(self.lbWebport, 0, 2)
        grid.addWidget(QLabel("Synode port"), 1, 0)
        grid.addWidget(self.txtPort, 1, 1)
        grid.addWidget(self.lbPort, 1, 2)
        grid.setColumnStretch(2, 1)

        self.txtReport = QPlainTextEdit()
        self.txtReport.setReadOnly(True)
        self.txtReport.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self.txtReport.setFont(QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont))

        self.lbNote = QLabel("A used port may be held by this synode's own service, if it is running.")
        self.lbNote.setWordWrap(True)

        self.buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        self.btnOk = self.buttons.button(QDialogButtonBox.StandardButton.Ok)
        self.btnOk.setText("Use these ports")
        # Enter in a port box checks the port, it doesn't close the dialog.
        self.btnOk.setAutoDefault(False)
        self.btnOk.setDefault(False)
        self.buttons.accepted.connect(self.accept)
        self.buttons.rejected.connect(self.reject)

        lay = QVBoxLayout(self)
        lay.addLayout(grid)
        lay.addWidget(self.txtReport, 1)
        lay.addWidget(self.lbNote)
        lay.addWidget(self.buttons)

        self._checked = None
        '''The (webport, port) texts last checked, to skip re-scanning when a box loses focus unchanged.'''

        # editingFinished: focus lost, or Enter
        self.txtWebport.editingFinished.connect(self.check)
        self.txtPort.editingFinished.connect(self.check)

        # Scan once the dialog is shown, so the user can see "Scanning ..." while waiting.
        QTimer.singleShot(0, self.check)

    @staticmethod
    def _number_box(text: str) -> QLineEdit:
        box = QLineEdit(text)
        box.setValidator(QRegularExpressionValidator(QRegularExpression(r"\d{0,5}")))
        box.setMaxLength(5)
        box.setFixedWidth(80)
        return box

    @staticmethod
    def _status_label() -> QLabel:
        lb = QLabel()
        lb.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        lb.setWordWrap(True)
        return lb

    def webport(self) -> str:
        return self.txtWebport.text().strip()

    def port(self) -> str:
        return self.txtPort.text().strip()

    def check(self):
        """
        Validate the two ports, show whether they are free, refresh the available port list,
        and allow OK only if both ports are valid and different.
        """
        texts = (self.webport(), self.port())
        if texts == self._checked:
            return
        self._checked = texts

        self.txtReport.setPlainText("Scanning available ports ...")
        self.lbWebport.setText("")
        self.lbPort.setText("")
        QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        QApplication.processEvents()  # paint the message before the scan blocks the GUI
        try:
            ok = True
            for txt, lb in ((texts[0], self.lbWebport), (texts[1], self.lbPort)):
                p = int(txt) if txt else 0
                if not valid_url_port(p):
                    lb.setText(f"⛔ Port must be in [{port_min} - {port_max}]")
                    ok = False
                else:
                    lb.setText(port_status(p)[1])

            if ok and texts[0] == texts[1]:
                self.lbPort.setText("⛔ The web port and the synode port must be different.")
                ok = False
            self.btnOk.setEnabled(ok)

            marks = {int(texts[0]): "new web port", int(texts[1]): "new synode service port"} if ok else None
            buf = io.StringIO()
            try:
                report_port_ranges(port_min, port_max, out=buf, marks=marks)
            except Exception as e:
                buf.write(f"\nScan failed: {e}\n")
            self.txtReport.setPlainText(buf.getvalue())
        finally:
            QApplication.restoreOverrideCursor()

    def accept(self):
        # The boxes may have been edited without losing focus, e.g. OK by a shortcut.
        self.check()
        if self.btnOk.isEnabled():
            super().accept()


def find_ports(parent: Optional[QWidget], webport: int, port: int) -> Tuple[bool, int, int]:
    """
    Pop up the ports dialog, initialized with the given ports.

    :param parent: the parent window
    :param webport: the current web port, 0 / None for none
    :param port: the current synode (jserv) port, 0 / None for none
    :return: (accepted, webport, port). If not accepted, the given ports are returned unchanged.
    """
    def text(p) -> str:
        return str(p) if p else ''

    dlg = PortsDialog(parent, text(webport), text(port))
    if dlg.exec() != QDialog.DialogCode.Accepted:
        return False, webport, port
    # The dialog only accepts valid, different ports.
    return True, int(dlg.webport()), int(dlg.port())


if __name__ == "__main__":
    # Manual test: python -m synodepy3.ports_dialog
    import sys

    app = QApplication(sys.argv)
    print("(accepted, webport, port):", find_ports(None, 8900, 8964))
