import os
import sys
from pathlib import Path
from typing import cast

from anclient.io.odysz.jclient import SessionClient
from anson.io.odysz.anson import Anson, AnsonException
from anson.io.odysz.common import LangExt, Utils
from prompt_toolkit import PromptSession
from prompt_toolkit.document import Document
from prompt_toolkit.shortcuts import choice
from prompt_toolkit.styles import Style
from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.odysz.semantic.jprotocol import JServUrl
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException, AppSettings
from semanticshare.io.oz.syn import SynodeMode
from semanticshare.io.oz.syn.registry import CynodeStats, SynodeConfig

from synodepy3 import SynodeUi
from synodepy3.installer_api import InstallerCli, jserv_07_jar, html_web_jar, web_port0, serv_port0, err_uihandlers


def reach_central():
    pass

def readable_state(s: str = None):
    return '' if LangExt.len(s) == 0 \
            else '✅ Available planned node' if s == CynodeStats.create \
            else '⛔ Already running as a Hub node' if s == CynodeStats.asHub \
            else '⛔ Already running as a Peer node' if s == CynodeStats.asPeer \
            else '[❗] Unknown state (dangerous)'

def generate_service_templ(s: AppSettings, c: SynodeConfig, xms:str='1g', xmx='8g'):
    """
    :param s: settings
    :param c: synode registry config
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
    syn_templ, web_templ = f'{c.synid}.service', f'{c.synid}.web.service'
    with open(syn_templ, "w") as fo:
        fo.write(etc_syn)
    with open(web_templ, "w") as fo:
        fo.write(etc_web)

    return syn_templ, web_templ

_quit = False
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
        if not JServUrl.valid(v.text, rootpath=synode_ui.central_path):
            raise ValidationError(message="Jserv URL is invalid.")

class QuitValidator(Validator):
    def validate(self, v):
        global _quit
        if not v.text.strip():
            _quit = True
            return

class VolumeValidator(Validator):
    def validate(self, v):
        parent_dir = os.path.dirname(v.text)
        if not parent_dir:
            parent_dir = os.getcwd()
        if not os.path.isdir(parent_dir):
            raise ValidationError(message=f"Parent directory '{parent_dir}' does not exist.")
        if not os.access(parent_dir, os.W_OK):
            raise ValidationError(message=f"Permission denied to write in '{parent_dir}'.")

        if Utils.iswindows():
            for c in v.text:
                if c == '\\':
                    raise ValidationError(message=f'Please replace all "\\" with "/"')

        try:
            os.makedirs(path, exist_ok=True)
            if not os.listdir(path):
                os.rmdir(path)  # Only remove if empty
            elif cli.hasrun(path):
                raise ValidationError(message=f"The volume is already used by a running synode: {v}")

            return True
        except PermissionError:
            raise ValidationError(message=f"Permission denied: Unable to create '{path}'.")
        except FileExistsError:
            raise ValidationError(message=f"A file or directory already exists at '{path}'.")
        except OSError as e:
            raise ValidationError(message=f"An OS error occurred while testing creation: {e}")

class NodeStateValidator(Validator):
    def validate(self, v):
        if v[1] == 'installed':
            raise ValidationError(message=f'Node {v[0]} is installed.')

class DomainValidator(Validator):
    def validate(self, v: Document) -> None:
        # e = cli.validate_domain()
        # if e is not None:
        #     raise ValidationError(message=str(e))
        try: LangExt.only_id_len(v.text, minlen=2, maxlen=12)
        except AnsonException:
            raise ValidationError(message=f"domain length: 2 <= Len('{cfg.domain}') <= 12")

class PortValidator(Validator):
    def validate(self, v: Document) -> None:
        pass

class SyncInsValidator(Validator):
    def validate(self, v: Document) -> None:
        if not LangExt.isblank(v.text):
            err = cli.validate_synins(v.text)
            if err is not None:
                raise ValidationError(message=err['config.syncIns'])

class MultiValidator(Validator):
    valids = list[Validator]

    def __init__(self, *validators: Validator):
        self.valids = validators

    def validate(self, v):
        for vld in self.valids:
            vld.validate(v)

# InstallerForm.bind_config
def err_ctx(c, e: str, *args: str) -> None:
    global _quit, details
    try: details[0] = e.format(args) if e is not None else e
    except Exception as ex:
        print(ex)
        print(type(e), e.format)
        details[0] = e
    _quit = True

err_uihandlers[0] = err_ctx

cli = InstallerCli()
cli.registry = cli.load_settings()
cli.registry = InstallerCli.loadRegistry(cli.settings.volume, 'registry')

ssclient = cast(SessionClient, None)
session = PromptSession(style=style)

cfg = cli.registry.config # for shot

print(f"Starting configure Synode {synode_ui.version}. Return with empty input to abort.")

if not cli.hasrun():
    # 0. central jserv
    orgs: list[str] = cast(list, None)
    orgid: str = cast(str, None)
    while not _quit and not reach_central():
        cli.settings.regiserv = session.prompt(
              message="Please input central service url (empty to quit): ",
              validator=QuitValidator(),
              default=cli.settings.regiserv,
              validate_while_typing=False)

        ssclient = cli.check_cent_login()
        orgs, orgid = cli.query_orgs()

        if LangExt.len(orgs) > 0:
            break

    check_quit(_quit)

    # 1. orgs / community
    session.prompt(
        message=f"Portfolio {synode_ui.version} market ID: {synode_ui.market_id}. ",
        default="Return to continue ...")

    # 2. bind domains
    # e.g. ['zsu', 'edu-0']
    domains = cli.query_domx(market=synode_ui.market_id, commu=orgid)

    if domains is None:
        _quit = True
    check_quit(_quit)

    # 3. create or select a domain
    def create_find_update_dom():

        if LangExt.len(domains.orgDomains) == 0:
            options = []
        else:
            options = [(d, d) for d in domains.orgDomains]

        options.append((None, 'Create a new domain...'))
        domid = choice(message="Please select a domain:",
                       options=cast(list[(str, str)], options),
                       default=cli.registry.config.domain)

        if domid is not None:
            # 3.1. select domain
            domid = cast(str, domid)
            cli.update_domain(domain=domid)
            resp = cli.query_domconf(commuid=orgid, domid=domid)
        else:
            # 3.2 create domain
            cfg.domain = session.prompt(
                message='Please input new domain name:',
                validator=MultiValidator(QuitValidator(), DomainValidator()),
                validate_while_typing=False)
            resp = cli.register()
            Utils.logi('Doamin created: {}\n{}', domid, 'None' if resp is None else resp.diction)

        if resp is None:
            print("Error: the domain id is not found, or cannot be created.")
            _quit = True
            check_quit(_quit)
        else:
            # ui.update_bind_domconf()
            cfg.overlay(resp.diction)
        return resp

    create_find_update_dom()

    # 4 local synode
    # 4.1 resp -> nodes
    def respeers_options(diction: SynodeConfig):
        peer_ids = diction.peers if diction is not None else None
        return None if LangExt.len(peer_ids) == 0 else \
            [((p.synid, p.stat), f'{p.synid} - {readable_state(p.stat)}') for p in peer_ids]

    # 4.2 select a peer
    synid, cynstat = None, CynodeStats.die
    while not _quit and cynstat is not None and cynstat != CynodeStats.create:
        # [(('node-1', CynodeStats.create), readable_state(CynodeStats.create)), ...]
        nodes = respeers_options(cli.registry.config)
        nodes.append((('', CynodeStats.die), '[Select another domain]'))
        nodes.append(((cast(str, None), CynodeStats.die), '[Quit]'))

        selected_id = cli.registry.config.synid, ''
        for s in nodes:
            if s[0][0] == cli.registry.config.synid:
                selected_id = s[0]
                break

        synid, cynstat = choice(
            message="Please select a Synode which is not running (can re-install if has not run):",
            options=nodes, default=selected_id,
            style=style)

        if synid is None:
            _quit = True
        elif synid == '': # another domain
            create_find_update_dom()
        elif cynstat is not None and cynstat != CynodeStats.create:
            print(f'Cannot re-install {synid}.\n'
                  '[Note 0.7.6] Some settings can be modified in settings.json, e.g. port or ip, by which way is not recommended.')
        else: # ui.select_peer()
            cfg.synid = synid

    check_quit(_quit)

    # 4.3 volume
    cli.settings.volume = session.prompt(
        message=f"Set volume path, emtpy to quit (volume is where the files and data saved).",
        validator = MultiValidator(QuitValidator(), VolumeValidator()),
        default=f"{Path(os.getcwd()).as_posix()}/vol")

    check_quit(_quit)

else:
    print(f'This folder and the volume has already run as [{cfg.domain}]{cfg.synid}')

# 5A mode & syncIns
synmode_v = choice(
        message='Please select the Synode mode:',
        default=SynodeMode.hub.value if SynodeMode.hub.name == cfg.mode else SynodeMode.peer.value,
        options=[(SynodeMode.hub.value,    'Domain Hub / Centre'),
                 (SynodeMode.peer.value,   'Primary Storage Node'),
                 (SynodeMode.nonsyn.value, 'Abort Installation')])

if synmode_v == SynodeMode.nonsyn.value:
    _quit = True
else:
    cfg.mode = SynodeMode(synmode_v).name
    if synmode_v == SynodeMode.peer.value:
        sync_insnds = session.prompt(
                message='Please set the synchronization interval, in seconds. (Empty to quit)',
                default=str(cfg.syncIns) if not LangExt.isblank(cfg.syncIns) else '0' if cfg.mode == SynodeMode.peer else '45',
                validator=MultiValidator(QuitValidator(), SyncInsValidator()))
        cfg.syncIns = float(sync_insnds) if not LangExt.isblank(sync_insnds) else 0
check_quit(_quit)

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
    default=default_ports(cli.settings),
    validator=MultiValidator(QuitValidator(), PortValidator()))

[cli.settings.webport, cli.settings.port] = parse_web_jserv_ports(ports)

reverse = choice(message='Is this node mapped to a public address (or behind a reverse proxy)?',
                 options=[(1, 'Yes'), (2, 'No'), (3, "Don't know, stop here.")],
                 default=1 if cli.settings.reverseProxy else 2)

# 5.1 revers proxy
def default_proxy_ports(s: AppSettings) -> str:
    return f'{s.webport if s.webProxyPort == 0 else s.webProxyPort}:{s.port if s.proxyPort == 0 else s.proxyPort}'

if reverse == 3:
    _quit = True
    check_quit(_quit)
elif reverse == 1:
    cli.settings.reverseProxy = True
else:
    cli.settings.reverseProxy = False

if cli.settings.reverseProxy:
    cli.settings.proxyIp = session.prompt(
        message='Please set the public (reverse proxy) ip. Return to quit: ',
        default=cli.reportIp() if LangExt.isblank(cli.settings.proxyIp) else cli.settings.proxyIp,
        validator=MultiValidator(QuitValidator(), PortValidator()))
    check_quit(_quit)

    reverseports = session.prompt(
        message='Please set the public ports. Format: "date-service-prot : www-port": ',
        default=default_proxy_ports(cli.settings),
        validator=MultiValidator(QuitValidator(), PortValidator()))

    if LangExt.len(ports) == 0:
        _quit = True
    else:
        [cli.settings.webProxyPort, cli.settings.proxyPort] = parse_web_jserv_ports(reverseports)

check_quit(_quit)

caninstall = choice(
        message=f'All settings are collected, install Synode {cfg.synid}?',
        options=[(1, 'Yes'), (2, 'No, and quit')])

def post_install():
    resp = cli.submit_mysettings()
    if resp is not None:
        cli.after_submit(resp)
    else:
        Utils.warn('TODO 0.7.6, RESP == NULL, handle errors...')

# 6 save & install
if caninstall == 1:
    # ui.cli.settings.save()
    # ui.cli.registry.config.save()
    try:
        # in case central replied empty value
        cli.updateWithUi(market=synode_ui.market_id)
        v = cli.validate()
        if v is not None:
            session.prompt(message='There are error in settings / configurations ...')
            Utils.warn(v)
            _quit = True
            check_quit(_quit)

        cli.install()
        post_err = cli.postFix()

        # ui: submit_jserv()
        # cli.registry.config.peers = resp.diction.peers
        # ui: bind_hubjserv()
        post_install()
    except FileNotFoundError or IOError as e:
        # Changing vol path can reach here ?
        Utils.warn(e)
        session.prompt('Setting up synodepy3 failed.')
        _quit = True
        check_quit(_quit)
    except PortfolioException as e:
        Utils.warn(e)
        session.prompt('Configuration is updated with errors. Check the details.\n'
                        'If this is not switching volume, that is not correct')
        post_install() # let's still take effects for changes
        _quit = True
        check_quit(_quit)

    if Utils.iswindows():
        session.prompt('Synode-cli 0.7.6 cannot install the required Windows services.\n'
                       'Please install it with the GUI version:\n'
                       'py -m synodepy3\n'
                       'And click "install Windows service" with default settings.')
    else:
        syn_templ, web_templ = generate_service_templ(cli.settings, cfg)
        # TODO merge with the cli function
        login_url = f'{"https" if cli.registry.config.https else "http"}://' + \
                    f'{ cli.settings.proxyIp if cli.settings.reverseProxy else "127.0.0.1"}:' + \
                    f'{cli.settings.webProxyPort if cli.settings.reverseProxy else cli.settings.webport}/login.html'

        session.prompt(message=
               f'The service configuration template is saved in ./{syn_templ} & ./{web_templ}.\n\n'
                'Before setup the services, it is recommend to try the service with this two commands:\n'
               f'java -jar bin/{jserv_07_jar}\n'
               f'java -jar bin/{html_web_jar}\n\n'
               f'Then try login with user Id "{cli.registry.config.admin}" & password, your-domain-token at\n'
               f'{login_url}\n\n'
                'A simple tutorial of install a Unix service is to be build. You have to Google it. Sorry!\n'
               f'Return to quit Portfolio {synode_ui.version} Setup ...'
               )

def main():
    '''
    The stub for pyproject.toml's main entry
    :return: 0
    '''
    return 0