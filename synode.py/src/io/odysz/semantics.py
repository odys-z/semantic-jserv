from dataclasses import dataclass

from anson.io.odysz.anson import Anson


@dataclass
class SessionInf(Anson):
    uid: str
    pswd: str
    ssid: str
    ssToken: str

    def __init__(self, uid: str = None, pswd: str = None):
        super().__init__()
        self.uid = uid
        self.pswd = pswd
        self.ssid = None
        self.ssToken = None
