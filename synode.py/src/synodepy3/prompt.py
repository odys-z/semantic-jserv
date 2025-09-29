import os
import sys
from typing import cast

from anclient.io.odysz.jclient import SessionClient
from anson.io.odysz.anson import Anson
from anson.io.odysz.common import LangExt, Utils
from prompt_toolkit import PromptSession
from prompt_toolkit.document import Document
from prompt_toolkit.shortcuts import choice
from prompt_toolkit.styles import Style
from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.odysz.semantic.jprotocol import JServUrl
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException, AppSettings
from semanticshare.io.oz.syn.registry import CynodeStats, SynodeConfig

from synodepy3 import SynodeUi
from synodepy3.installer_api import InstallerCli, jserv_07_jar, html_web_jar, web_port0, serv_port0, err_uihandlers


def reach_central():
    pass

def readable_state(s: str = None):
    return '' if LangExt.len(s) == 0 \
            else '✅ Available planned node' if s == CynodeStats.create \
            else '⛔ Already installed as a Hub node' if s == CynodeStats.asHub \
            else '⛔ Already installed as a Peer node' if s == CynodeStats.asPeer \
            else '[❗] Unknown state (dangerous)'

def generate_service_templ(settings, config, xms:str='1g', xmx='8g'):
    """
    :param settings: 
    :param config:
    :param xms: JRE option Xms
    :param xmx: JRE option Xmx
    :return:
    """

    cwd = os.getcwd()
    from synodepy3.__version__ import jar_ver, web_ver
    synode_desc = f'Synode {jar_ver} {synid}'
    etc_syn = f"""[Unit]
Description={synode_desc}
After=network.target

[Service]
Type=simple
User={os.getlogin()}
WorkingDirectory={cwd}
ExecStart=java -jar {cwd}/bin/{jserv_07_jar}
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="JAVA_OPTS=-Xms{xms} -Xmx{xmx}"

[Install]
WantedBy=multi-user.target
    """

    web_desc = f'Synode {web_ver} {synid}'
    etc_web = f"""[Unit]
Description={web_desc}
After=network.target

[Service]
Type=simple
User={os.getlogin()}
WorkingDirectory={cwd}
ExecStart=java -jar {cwd}/bin/{html_web_jar}
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="JAVA_OPTS=-Xms512m -Xmx2g"

[Install] 
    """
    syn_templ, web_templ = f'{config.synid}.service', f'{config.synid}.web.service'
    with open(syn_templ, "w") as fo:
        fo.write(etc_syn)
    with open(web_templ, "w") as fo:
        fo.write(etc_web)

    return syn_templ, web_templ

quite = False
details = [cast(str, None)]

def check_quit(q: bool):
    if q:
        print(details)
        sys.exit()

style = Style.from_dict({
    'prompt': 'bg:#ansiblue #ffffff',  # Blue background, white text
})


path = os.path.dirname(__file__)
synode_ui = cast(SynodeUi, Anson.from_file(os.path.join(path, "synode.json")))

class IPValidator(Validator):
    pass

class JservValidator(Validator):
    def validate(self, v):
        if not JServUrl.valid(v, rootpath=synode_ui.central_path):
            raise ValidationError(message="Jserv URL is invalid.")

class QuiteValidator(Validator):
    def validate(self, v):
        global quite
        if not v.text.strip():
            # raise ValidationError(message="Input cannot be empty")
            quite = True
            return

class NodeStateValidator(Validator):
    def validate(self, v):
        if v[1] == 'installed':
            raise ValidationError(message=f'Node {v[0]} is installed.')

class DomainValidator(Validator):
    def validate(self, v: Document) -> None:
        e = cli.validate_domain()
        if e is not None:
            raise ValidationError(message=str(e))

class PortValidator(Validator):
    def validate(self, v: Document) -> None:
        pass

class MultiValidator(Validator):
    valids = list[Validator]

    def __init__(self, *validators: Validator):
        self.valids = validators

    def validate(self, v):
        for vld in self.valids:
            vld.validate(v)

# InstallerForm.bind_config
def err_ctx(c, e: str, *args: str) -> None:
    global quite, details
    try: details[0] = e.format(args) if e is not None else e
    except Exception as ex:
        print(ex)
        print(type(e), e.format)
        details[0] = e
    quite = True

err_uihandlers[0] = err_ctx

cli = InstallerCli()
cli.registry = cli.load_settings()
cli.registry = InstallerCli.loadRegistry(cli.settings.volume, 'registry')

ssclient = cast(SessionClient, None)
session = PromptSession(style=style)

print(f"Starting configure Synode {synode_ui.version}. Return with empty input to abort.")

# 0. central jserv
orgs: list[str] = cast(list, None)
orgid: str = cast(str, None)
while not quite and not reach_central():
    cli.settings.regiserv = session.prompt(
          message="Please input central service url (empty to quite): ",
          validator=QuiteValidator(),
          default=cli.settings.regiserv,
          validate_while_typing=False)

    ssclient = cli.check_cent_login()
    orgs, orgid = cli.query_orgs()

    if LangExt.len(orgs) > 0:
        break

check_quit(quite)

# 1. orgs / community
session.prompt(
    message=f"Portfolio {synode_ui.version} market ID: {synode_ui.market_id}. ",
    default="Return to continue ...")

# 2. bind domains
# e.g. ['zsu', 'edu-0']
domains = cli.query_domx(market=synode_ui.market_id, commu=orgid)

if domains is None:
    quite = True
check_quit(quite)

# 3. create or select a domain
def create_find_update_dom():

    if LangExt.len(domains.orgDomains) == 0:
        options = []
    else:
        options = [(d, d) for d in domains.orgDomains]

    options.append((None, 'Create a new domain...'))
    domid = choice(message="Please select a domain:",
                   options=options)

    if domid is not None:
        # 3.1. select domain
        domid = cast(str, domid)
        cli.update_domain(domain=domid)
        resp = cli.query_domconf(commuid=orgid, domid=domid)
    else:
        # 3.2 create domain
        cli.registry.config.domain = session.prompt(
            message='Please input new domain name:',
            validator=MultiValidator(QuiteValidator(), DomainValidator()),
            validate_while_typing=False)
        resp = cli.register()
        Utils.logi("Doamin created: {}\n{}", domid, resp.peer_ids())
        # ui.update_bind_domconf() -> ui.bind_cbbpeers()

    if resp is None:
        print("Error: the domain id is not found, or cannot be created.")
        quite = True
        check_quit(quite)
    else:
        # ui.update_bind_domconf()
        cli.registry.config.overlay(resp.diction)
    return resp

create_find_update_dom()

# 4 local synode
# 4.1 resp -> nodes
def respeers_options(diction: SynodeConfig):
    peer_ids = diction.peers if diction is not None else None
    return None if LangExt.len(peer_ids) == 0 else \
        [((p.synid, p.stat), f'{p.synid} - {readable_state(p.stat)}') for p in peer_ids]

# 4.2 select a peer

synid, synstat = None, CynodeStats.die
while not quite and synstat is not None and synstat != CynodeStats.create:
    # [(('node-1', CynodeStats.create), readable_state(CynodeStats.create)), ...]
    nodes = respeers_options(cli.registry.config)
    nodes.append((('', CynodeStats.die), '[Select another domain]'))
    nodes.append(((cast(str, None), CynodeStats.die), '[Quite]'))

    synid, synstat = choice(
        message="Please select a Synode not running / installed:",
        options=nodes,
        style=style)

    if synid is None:
        quite = True
    elif synid == '':
        create_find_update_dom()
    else: # ui.select_peer()
        cli.registry.config.synid = synid

check_quit(quite)

# 5 ports
def parse_web_jserv_ports(ports: str) -> [int, int]:
    try:
        if LangExt.len(ports) == 0:
            ports = f'{web_port0}:{serv_port0}'

        ports = ports.split(':')
        ports = [int(p) for p in ports]
    except Exception:
        ports = [web_port0, serv_port0]
    return ports

def default_ports(s: AppSettings) -> str:
    return f'{web_port0 if s.webport == 0 else s.webport}:{serv_port0 if s.port == 0 else s.port}'

ports = session.prompt(
    message=f'Please set the ports. Format: "synode-port : www-port"',
    # default=f'{web_port0}:{serv_port0}',
    default=default_ports(cli.settings),
    validator=MultiValidator(QuiteValidator(), PortValidator()))

[cli.settings.webport, cli.settings.port] = parse_web_jserv_ports(ports)

reverse = choice(message='Is this node mapped to a public address?',
                 options=[(1, 'Yes'), (2, 'No'), (3, "Don't know, stop here.")])

# 5.1 revers proxy
def default_proxy_ports(s: AppSettings) -> str:
    return f'{s.webport if s.webProxyPort == 0 else s.webProxyPort}:{
              s.port    if s.proxyPort == 0    else s.proxyPort}'

if reverse == 3:
    quite = True
    check_quit(quite)
elif reverse == 1:
    cli.settings.reverseProxy = True
else:
    cli.settings.reverseProxy = False

if cli.settings.reverseProxy:
    cli.settings.proxyIp = session.prompt(
        message='Please set the public ip. Return to quite: ',
        default=cli.reportIp(),
        validator=MultiValidator(QuiteValidator(), PortValidator()))
    check_quit(quite)

    reverseports = session.prompt(
        message='Please set the public ports. Format: "date-service-prot : www-port": ',
        default=default_proxy_ports(cli.settings),
        validator=MultiValidator(QuiteValidator(), PortValidator()))

    if LangExt.len(ports) == 0:
        # reverseports = f'{web_port0}:{serv_port0}'
        quite = True
    else:
        [cli.settings.webProxyPort, cli.settings.proxyPort] = parse_web_jserv_ports(reverseports)

check_quit(quite)

caninstall = choice(
        message=f'All settings are collected, install Synode {synid}?',
        options=[(1, 'Yes'), (2, 'No, and quite')])

# 6 save & install
if caninstall == 1:
    # ui.cli.settings.save()
    # ui.cli.registry.config.save()
    try:
        cli.install()
        post_err = cli.postFix()

        # ui: submit_jserv()
        resp = cli.submit_mysettings()
        # cli.registry.config.peers = resp.diction.peers
        # ui: bind_hubjserv()
        cli.after_submit(resp)
    except FileNotFoundError or IOError as e:
        # Changing vol path can reach here ?
        Utils.warn(e)
        session.prompt('Setting up synodepy3 failed.')
        quite = True
        check_quit(quite)
    except PortfolioException as e:
        Utils.warn(e)
        session.prompt('Configuration is updated with errors. Check the details.\n'
                        'If this is not switching volume, that is not correct')
        quite = True
        check_quit(quite)

    if Utils.iswindows():
        session.prompt('Synode-cli 0.7.6 cannot install the required Windows services.\n'
                       'Please install it with the GUI version:\n'
                       'py -m synodepy3\n'
                       'And click "install Windows service" with default settings.')
    else:
        syn_templ, web_templ = generate_service_templ(cli.settings, cli.registry.config)
        # TODO merge with the cli function
        login_url = f'{
                    'https' if cli.registry.config.https else 'http'
                    }://{
                    cli.settings.proxyIp if cli.settings.reverseProxy else '127.0.0.1' 
                    }:{
                    cli.settings.webProxyPort if cli.settings.reverseProxy else cli.settings.webport
                    }/login.html'
        session.prompt(message=
               f'The service configuration template is saved in ./{syn_templ} & ./{web_templ}.\n\n'
                'Before setup the services, it is recommend to try the service with this two commands:\n'
               f'java -jar bin/{jserv_07_jar}\n'
               f'java -jar bin/{html_web_jar}\n\n'
               f'Then try login with user Id "{cli.registry.config.admin}" & password, your-domain-token at\n'
               f'{login_url}\n\n'
                'A simple tutorial of install a Ubuntu Service is to be build. You have to Google it, sorry!\n'
               f'Return to quite Portfolio {synode_ui.version} Setup ...'
               )
