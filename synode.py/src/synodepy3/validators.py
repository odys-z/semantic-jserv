'''
Validators for Prompt (synode-cli). The Qt widget validators, wrapping these, are in qt_validators.py.
@since 0.7.7
'''

import ipaddress
from typing import Optional

from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.odysz.semantic.jprotocol import JServUrl
from prompt_toolkit.document import Document

from anson.io.odysz.anson import AnsonException
from anson.io.odysz.common import LangExt

synid_maxlen = 12
'''
The central's synode id columns, c_synodes.sid and oper, are varchar2(12).
'''

domain_maxlen = synid_maxlen - len('-hub')
'''
8, the length of a new domain's id: the central names the domain's synodes
<domain>-hub, <domain>-1, <domain>-2 (Registry.createDomx), which must fit in synid_maxlen.
@since 0.8.0
'''

domain_maxlen_existing = 12
'''
An existing domain's id, c_syndomx.domid varchar2(12): domains created before 0.8.0 can be joined.
'''

def check_domain_id(domid: str, creating: bool) -> Optional[str]:
    '''
    The rule of domain ids, for synode-cli, setup-gui and InstallerCli.validate_domain().
    :param domid:
    :param creating: True for a new domain (at most domain_maxlen chars), False for joining an existing one.
    :return: None if valid, otherwise the error message.
    '''
    maxlen = domain_maxlen if creating else domain_maxlen_existing
    try:
        if LangExt.isblank(domid):
            raise AnsonException(0, 'blank domain id')
        LangExt.only_id_len(domid, minlen=2, maxlen=maxlen)
        return None
    except AnsonException:
        return f"Domain id: 2 <= len('{domid}') <= {maxlen}" + \
               (f", so that '<domain>-hub' fits the {synid_maxlen} chars of a synode id." if creating else '.')


class PDomainValidator(Validator):
    '''
    Domain id. See check_domain_id().
    '''
    def __init__(self, creating: bool = True):
        super().__init__()
        self.creating = creating

    def validate(self, v: Document) -> None:
        err = check_domain_id(v.text, self.creating)
        if err is not None:
            raise ValidationError(message=err)


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
