from dataclasses import dataclass

from anson.io.odysz.anson import Anson
from anson.io.odysz.common import LangExt


@dataclass
class Synode(Anson):
    version: str
    iso: str
    market: str
    lang: str
    langs: dict
    ui: str

    def __init__(self):
        super().__init__()
        self.ui = 'ui_form.py'
        self.version = "0.7.6"
        self.market = "TEST"
        self.langs = dict()

    def signup_prompt(self, defl = None):
        return LangExt.ifblank(self.langs[self.lang].signup_prompt, defl)
