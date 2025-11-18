'''
Validators for both PyQt and Prompt
@since 0.7.7
'''

import ipaddress

from PySide6.QtGui import QValidator
from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.odysz.semantic.jprotocol import JServUrl, JProtocol
from prompt_toolkit.document import Document

from anson.io.odysz.common import LangExt

class PIPValidator(Validator):
    def validate(self, v: Document) -> None:
        if LangExt.isblank(v.text):
            return
        try:
            ipaddress.ip_address(v.text)
            return
        except:
            raise ValidationError(message=f'In valid IP address.')

class QIPValidator(QValidator):
    '''
    Not Used
    ========
    '''
    pv: PIPValidator

    def __init__(self):
        super().__init__()
        self.pv = PIPValidator()

    def validate(self, arg__1, arg__2, /):
        try:
            self.pv.validate(Document(arg__1))
            return QValidator.State.Acceptable
        except: return QValidator.State.Invalid

class PJservValidator(Validator):
    '''
    Synodes protocol validator ('jserv-album')
    '''

    def __init__(self, protocol_root: str = None):
        super().__init__()
        self.protocol_root = JProtocol.urlroot if LangExt.isblank(protocol_root) else protocol_root

    def validate(self, v):
        if not LangExt.isblank(v.text) and \
           not JServUrl.valid(v.text, rootpath=self.protocol_root):
            raise ValidationError(
                message=f'Jserv URL is invalid. Reqired format: http(s)://ip:port/{self.protocol_root}')

class QJservValidator(QValidator):
    '''
    Not Used
    ========
    Synodes protocol validator ('jserv-album')
    '''

    def __init__(self, protcol_rrot: str = None):
        super().__init__()
        self.pv = PJservValidator(protcol_rrot)

    def validate(self, arg__1, arg__2, /):
        try:
            self.pv.validate(Document(arg__1))
            return QValidator.State.Acceptable
        except: return QValidator.State.Invalid

