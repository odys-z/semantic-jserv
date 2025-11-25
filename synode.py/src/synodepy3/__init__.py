from dataclasses import dataclass

from anson.io.odysz.anson import Anson
from anson.io.odysz.common import LangExt

jre_mirror_key = 'jre_mirror'

@dataclass
class SynodeUi(Anson):
    version: str
    central_iport: str
    central_path: str
    market: str
    market_id: str
    iso: str
    lang: str
    langs: dict
    ui: str

    ### consts
    credits: str

    def __init__(self):
        super().__init__()
        self.ui = 'ui_form.py'
        self.version = "0.7.6"
        self.central_iport = '<invalid>'
        self.central_path  = 'regist_central'
        self.market = "TEST"
        self.market = "test.org"
        self.langs = dict()

    def signup_prompt(self, defl = None):
        return LangExt.ifblank(self.langs[self.lang].signup_prompt, defl)

    def langstr(self, res):
        """
        Get a string resource for the language, self.langs[self.lang][res].
        Return the en version if not found
        :param res:
        :return: self.langs[self.lang][res].
        """
        return self.langs[self.lang][res] \
            if self.lang in self.langs and res in self.langs[self.lang] \
            else self.langs['en'][res]

    def langstrf(self, res, **args):
        return self.langstr(res).format(**args)
