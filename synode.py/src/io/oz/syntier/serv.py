from dataclasses import dataclass

from anson.io.odysz.ansons import Anson


@dataclass
class ExternalHosts(Anson):
    host: str
    localip: str
    syndomx: dict
    resources: dict

    def __init__(self):
        super().__init__()
        self.syndomx = dict()
        self.resources = dict()


