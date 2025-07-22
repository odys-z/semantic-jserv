from dataclasses import dataclass

from anson.io.odysz.anson import Anson


@dataclass
class Synode(Anson):
    org: str
    synid: str
    mac: str
    domain: str
    nyq: int
    syn_uid: str

    def __init__(self):
        super().__init__()


@dataclass()
class SyncUser(Anson):
    userId: str
    userName: str
    pswd: str
    iv: str
    domain: str
    org: str

    def __init__(self, userId=None, userName=None, pswd=None):
        super().__init__()
        self.userId = userId
        self.userName = userName
        self.pswd = pswd

