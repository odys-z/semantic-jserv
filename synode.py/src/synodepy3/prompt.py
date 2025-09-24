import os
from typing import Optional, cast

from anclient.io.odysz.jclient import OnError
from anson.io.odysz.anson import Anson
from anson.io.odysz.common import LangExt
from prompt_toolkit import PromptSession
from prompt_toolkit.shortcuts import choice
from prompt_toolkit.styles import Style
from prompt_toolkit.formatted_text import HTML
from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.oz.syn.registry import CynodeStats

from synodepy3 import SynodeUi
from synodepy3.installer_api import InstallerCli


def reach_central():
    pass

quit = False

style = Style.from_dict({
    'prompt': 'bg:#ansiblue #ffffff',  # Blue background, white text
})


path = os.path.dirname(__file__)
synode_ui = cast(SynodeUi, Anson.from_file(os.path.join(path, "synode.json")))
err_handlers: list[Optional[OnError]] = [None]

class UrlValidator(Validator):
    def validate(self, v):
        global quit
        if not v.text.strip():
            # raise ValidationError(message="Input cannot be empty")
            quit = True
            return


class NodeStateValidator(Validator):
    def validate(self, v):
        if v[1] == 'installed':
            raise ValidationError(message=f'Node {v[0]} is installed.')


cli = InstallerCli()
session = PromptSession(style=style)

print(f"Starting configure Synode {synode_ui.version}. Return with empty input to abort.")

orgs = None
while not quit and not reach_central():
    regi_url = session.prompt(message="Please input central service url: ",
                              validator=UrlValidator(),
                              validate_while_typing=False)
    orgs = cli.check_cent_login()
    if LangExt.len(orgs) > 0:
        break

# domains = ['zsu', 'edu-0']
domains = cli.query_domx(market=synode_ui.market_id, commu=orgs)
# bind domains

nodes = [('node-0', CynodeStats.asHub), ('node-1', CynodeStats.create), ('node-2', None)]
domid = None
while domid is None:
    domid = session.prompt(message="Please select a domain:",
                          validator=NodeStateValidator(),
                          validate_while_typing=False)

resp = cli.query_domconf(commuid=orgid, domid=domid)

if resp is not None:
    cli.registry.config.synid = session.prompt(message="Please select a domain:")
    ui.update_bind_domconf(resp)




