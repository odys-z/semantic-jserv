from dataclasses import dataclass

from anson.io.odysz.anson import Anson


@dataclass
class Synode(Anson):
    version: str
    iso: str
    market: str
    lang: str
    langs: dict

    def __init__(self):
        super().__init__()
        self.version = "0.7.6"
        self.market = "TEST"
        self.langs = dict()
