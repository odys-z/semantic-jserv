from dataclasses import dataclass

from anson.io.odysz.anson import Anson


@dataclass
class ExternalHosts(Anson):
    host: str
    localip: str
    syndomx: dict
    resources: dict

    def __init__(self):
        super().__init__()
        self.localip = None
        self.syndomx = dict()
        self.resources = dict()


