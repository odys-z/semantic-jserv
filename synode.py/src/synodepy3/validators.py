'''
Validators for both PyQt and Prompt
@since 0.7.7
'''

import ipaddress

from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.odysz.semantic.jprotocol import JServUrl
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


class PJservValidator(Validator):
    '''
    Synodes protocol validator ('jserv-album')
    '''

    def __init__(self, protocol_root):
        super().__init__()
        self.protocol_root = protocol_root

    def validate(self, v):
        if LangExt.isblank(v.text):
            return
        try:
            valid = JServUrl.valid(v.text, rootpath=self.protocol_root)
        except ValueError:
            # urlparse().port raises on a non-numeric or out-of-range port, e.g. http://127.0.0.1:dddd/...
            valid = False
        if not valid:
            raise ValidationError(
                message=f'Jserv URL is invalid. Required format: http(s)://ip:port/{self.protocol_root}')
