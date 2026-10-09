import os
import sys
from pathlib import Path
from typing import cast, Optional, List

from anson.io.odysz.anson import AnsonException
from anson.io.odysz.common import LangExt, Utils, passwd_allow_ext
from prompt_toolkit import PromptSession
from prompt_toolkit.document import Document
from prompt_toolkit.shortcuts import choice
from prompt_toolkit.styles import Style
from prompt_toolkit.validation import Validator, ValidationError
from semanticshare.io.odysz.semantic.jprotocol import JServUrl, MsgCode
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException, AppSettings
from semanticshare.io.oz.syn import SynodeMode
from semanticshare.io.oz.syn.registry import CynodeStats, SynodeConfig

from synodepy3.systemd_units import install_linusrvs, linusrv_synode, linusrv_websrv, generate_service_templ
from synodepy3.installer_api import InstallerCli, jserv_07_jar, html_web_jar, web_port0, serv_port0, err_uihandlers, mypath
from synodepy3.validators import PJservValidator, PIPValidator
from synodepy3.get_avail_ports import report_port_ranges


def reach_central():
    pass

def readable_state(s: str = ''):
    return '' if LangExt.len(s) == 0 \
            else '✅ Available planned node' if s == CynodeStats.create \
            else '⛔ Already running as a Hub node' if s == CynodeStats.asHub \
            else '⛔ Already running as a Peer node' if s == CynodeStats.asPeer \
            else '[❗] Unknown state (Dangerous! Create a new domain if possible)'


from synodepy3.__version__ import synode_ver
from synodepy3.commands_help import commands_table, doc_link

cli_help = f'''Synode {synode_ver} command line setup.

Usage:
    synode-cli               configure and install this synode, interactively
    synode-cli -h | --help   show this help and quit

Run it in the synode's folder (with WEB-INF/settings.json). Return with empty input to abort.
At the ports prompt, enter '?' to list the available ports, or '??' to also show what holds the used ones.

{commands_table}

On Windows, list the available ports with the "find ports?" button next to the ports in setup-gui.exe.

Documentation: {doc_link}
'''

if any(a in ('-h', '--help', 'help') for a in sys.argv[1:]):
    print(cli_help)
    sys.exit(0)

_quit = False
details = [cast(Optional[str], None)]

def check_quit(q: bool):
    if q:
        for itm in details:
            if itm is not None:
                print(itm)
        sys.exit(-1)


style = Style.from_dict({
    'prompt': 'bg:#ansiblue #ffffff',  # Blue background, white text
})


class QuitValidator(Validator):
    def validate(self, document: Document) -> None:
        global _quit
        if not document.text.strip():
            _quit = True
        else:
            _quit = False


class VolumeValidator(Validator):
    def validate(self, document: Document) -> None:
        parent_dir = os.path.dirname(document.text)
        if not parent_dir:
            parent_dir = os.getcwd()
        if not os.path.isdir(parent_dir):
            raise ValidationError(message=f"Parent directory '{parent_dir}' does not exist.")
        if not os.access(parent_dir, os.W_OK):
            raise ValidationError(message=f"Permission denied to write in '{parent_dir}'.")

        if Utils.iswindows():
            for c in document.text:
                if c == '\\':
                    raise ValidationError(message=f'Please replace all "\\" with "/"')

        try:
            os.makedirs(mypath, exist_ok=True)
            if not os.listdir(mypath):
                os.rmdir(mypath)  # Only remove if empty
            elif cli.hasrun(mypath):
                raise ValidationError(message=f"The volume is already used by a running synode: {document}")

            return True
        except PermissionError:
            raise ValidationError(message=f"Permission denied: Unable to create '{mypath}'.")
        except FileExistsError:
            raise ValidationError(message=f"A file or directory already exists at '{mypath}'.")
        except OSError as e:
            raise ValidationError(message=f"An OS error occurred while testing creation: {e}")

class DomainValidator(Validator):
    def validate(self, document: Document) -> None:
        try: LangExt.only_id_len(document.text, minlen=2, maxlen=12)
        except AnsonException:
            raise ValidationError(message=f"domain length: 2 <= Len('{cfg.domain}') <= 12")

class PortsValidator(Validator):
    def __init__(self, allow_query: bool = False):
        '''
        :param allow_query: accept '?' and '??', asking for the list of local available ports
        '''
        self.allow_query = allow_query

    def validate(self, document: Document) -> None:
        if document is None or LangExt.isblank(document.text):
            return
        if self.allow_query and document.text.strip() in ('?', '??'):
            return
        try:
            poss = document.text.split(':')
            prts = [int(poss[0]), int(poss[1])]
            if 1024 <= prts[0] <= 655535 and 1024 <= prts[1] <= 65535 and prts[0] != prts[1]:
                return
        except:
            pass
        raise ValidationError(message=f"Valid format web-port:jserv-port, are different and in [1024-65535]")

class DomainTokenValidator(Validator):
    def validate(self, document: Document) -> None:
        if document is None or LangExt.isblank(document.text):
            return
        try: LangExt.only_passwdlen(document.text, minlen=8, maxlen=16)
        except AnsonException:
            raise ValidationError(message=f"token length must be in [8 ~ 16], allowed special chars: [{passwd_allow_ext}]")

class CentralPswdValidator(Validator):
    """
    The password of Central, settings.centralPswd, with the same rule as InstallerCli.validate_domain().
    Blank is passed to QuitValidator.
    """
    minlen, maxlen = 6, 32

    @staticmethod
    def check(pswd: Optional[str]) -> Optional[str]:
        """
        :return: the error message, or None if pswd is valid
        """
        if LangExt.isblank(pswd):
            return 'The password of Central is not set.'
        try:
            LangExt.only_passwdlen(pswd, minlen=CentralPswdValidator.minlen, maxlen=CentralPswdValidator.maxlen)
            return None
        except AnsonException:
            return f'Password length must be in [{CentralPswdValidator.minlen} ~ {CentralPswdValidator.maxlen}], ' \
                   f'allowed special chars: [{passwd_allow_ext}]'

    def validate(self, document: Document) -> None:
        if document is None or LangExt.isblank(document.text):
            return
        err = CentralPswdValidator.check(document.text)
        if err is not None:
            raise ValidationError(message=err)

class SyncInsValidator(Validator):
    def validate(self, document: Document) -> None:
        if not LangExt.isblank(document.text):
            err = cli.validate_synins(document.text)
            if err is not None:
                raise ValidationError(message=err['config.syncIns'])

class SrvNameValidator(Validator):
    """
    systemd unit name, without suffix .service. Blank is allowed (use the default).
    """
    def validate(self, document: Document) -> None:
        name = document.text.strip().removesuffix('.service')
        if not name:
            return
        try: LangExt.only_wordextlen(name, ext='_-.', maxlen=240)
        except AnsonException:
            raise ValidationError(message='Service name can only have letters, digits and "_-."')

class MultiValidator(Validator):
    valids = list[Validator]

    def __init__(self, *validators: Validator):
        self.valids = validators

    def validate(self, document):
        for vld in self.valids:
            vld.validate(document)

err_code = [cast(Optional[MsgCode], None)]
'''The code of the last error reported by Central, MsgCode or its name.'''

def err_ctx(c, e: str, *args: str) -> None:
    global _quit, details
    err_code[0] = c
    try: details[0] = e.format(args) if e is not None else e
    except Exception as ex:
        print(ex)
        print(type(e), e.format)
        details[0] = e
    _quit = True


err_uihandlers[0] = err_ctx

cli = InstallerCli()
# cli.registry = cli.load_settings()
cli.load_settings()
cli.registry = InstallerCli.loadRegistry(cli.settings.volume, 'registry')

ssclient = None
session = PromptSession(style=style)

cfg = cli.registry.config # for shot

print(f"Starting configure Synode {synode_ver}. Return with empty input to abort.")

has_run = cli.hasrun()

missing_requires = cli.check_prerequisites()

if missing_requires:
    details.extend(missing_requires)
    check_quit(True)

def ask_central_pswd(reason: str):
    """
    Ask for the password of the central user at settings.regiserv, empty to quit.
    The central client is dropped, so the next request logs in with the new password.
    """
    global _quit
    print(reason)
    print(f'Central: {cli.settings.regiserv}')
    # A separate session: is_password=True sticks to a PromptSession, masking all its later prompts.
    pswd = PromptSession(style=style).prompt(
        message=f'Password of "{cli.central_uid()}" (empty to quit): ',
        is_password=True,
        validator=MultiValidator(QuitValidator(), CentralPswdValidator()))
    check_quit(_quit)

    cli.update_central_pswd(pswd)
    details[0] = None

def ensure_central_pswd():
    """
    Ask for the central password if it's not set or invalid.
    It is set in WEB-INF/settings.json by the distribution build (jserv-album/tasks.py).
    """
    pswd_err = CentralPswdValidator.check(cli.settings.centralPswd)
    if pswd_err is not None:
        ask_central_pswd(pswd_err)

def query_domains(orgid: str):
    """
    Query the domains from Central, the first request to it.
    A wrong password is answered with exSession, by AnSession / JUser: ask for it and retry.
    :return: (domains, error code); domains is None if failed, with the error in details[0]
    """
    global _quit
    while True:
        err_code[0] = None
        domains = cli.query_domx(market=cli.settings.market_id, commu=orgid)
        if domains is not None:
            return domains, None

        if err_code[0] in (MsgCode.exSession, MsgCode.exSession.name):
            # ISSUE central-uid: the synode logs in central as the domain admin, so in 0.8.0 the central
            # user id must be 'admin'; it is not asked for, nor saved. See issues/central-uid-synode-login.md
            central_uid = cli.central_uid()
            if central_uid != 'admin':
                details.append(f'Central refused the login of user "{central_uid}", which must be "admin" in '
                               f'Portfolio {synode_ver} (ISSUE central-uid, the synode logs in central as the '
                               f'domain admin). Check centralUid in desktop/settings/app-settings.json.')
                check_quit(True)

            _quit = False  # set by err_ctx(); ask_central_pswd() quits on empty input
            ask_central_pswd(f'Central refused the login of user "{central_uid}": {details[0]}')
            continue

        return None, err_code[0]

if not has_run:
    # 0. central jserv, 2. bind domains, e.g. ['zsu', 'edu-0']
    orgs: list[str] = None # type: ignore
    orgid: str = None # type: ignore
    domains = None
    while True:
        cli.settings.regiserv = session.prompt(
              message="Please input central service url (empty to quit): ",
              validator=MultiValidator(QuitValidator(), PJservValidator(JServUrl(cli.settings.regiserv).jprotocol.protocolpath)),
              default=cli.settings.regiserv,
              validate_while_typing=True)
        check_quit(_quit)

        ensure_central_pswd()

        ssclient = cli.check_cent_login()
        orgs, orgid = cli.query_orgs()

        domains, code = query_domains(orgid)
        if domains is not None:
            break

        if code in (MsgCode.exIo, MsgCode.exIo.name):
            # e.g. a correct url of an unreachable site: let the user fix it and try again
            print(f'Cannot reach Central at {cli.settings.regiserv}:\n{details[0]}\n'
                  'Please check the url and try again.')
            details[0] = None
            _quit = False  # set by err_ctx()
            continue

        Utils.warn('Cannot find domains in market {}, community / org: {}',
                   cli.settings.market_id, orgid)
        check_quit(True)

    # 1. orgs / community
    session.prompt(
        message=f"Portfolio {synode_ver} market ID: {cli.settings.market_id}. ",
        validator=QuitValidator(),
        default="Return to continue ...")
    check_quit(_quit)

    # 3. create or select a domain
    def create_find_update_dom():
        '''
        The process / interaction of create / find a domain
        :return: response to A.queryDomConfig or A.registDom
        '''
        if LangExt.len(domains.orgDomains) == 0:
            options = []
        else:
            options = [(d, d) for d in domains.orgDomains]

        options.append((None, 'Create a new domain...'))
        domid : Optional[str] = choice(message="Please select a domain:",
                       options=options,
                       default=cli.registry.config.domain)

        if domid is not None:
            # 3.1. select domain
            cli.update_domain(orgtype=cli.settings.market_id, domain=domid, orgid=orgid)
            resp = cli.query_domconf(commuid=orgid, domid=domid)
        else:
            # 3.2 create domain
            cfg.domain = session.prompt(
                message='Please input new domain name: ',
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
            # -> ui.bind_hubjserv(registry.config, settings)
            cfg.overlay(resp.diction)
            cli.settings.acceptj_butme(cfg.synid, cfg.peers)
        return resp

    create_find_update_dom()

    # 4 local synode
    # 4.1 resp -> nodes
    def respeers_options(diction: SynodeConfig):
        opts = [] if LangExt.len(diction.peers) == 0 else \
            [((p.synid, p.stat), f'{p.synid} - {readable_state(p.stat)}') for p in diction.peers]
        opts.append((('', CynodeStats.die), '[Select another domain]'))
        opts.append(((None, CynodeStats.die), '[Quit]'))
        return opts

    # 4.2 select a peer
    synid, cynstat = None, CynodeStats.die
    while not _quit and cynstat is not None and cynstat != CynodeStats.create:
        nodes = respeers_options(cli.registry.config)

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
        message=f"Set volume path, emtpy to quit (volume is where the files and data saved). ",
        validator = MultiValidator(QuitValidator(), VolumeValidator()),
        default=f"{Path(os.getcwd()).as_posix()}/vol")

    check_quit(_quit)
    print(cli.settings.volume)

else:
    print(f'This folder and the volume has already run as [{cfg.domain}]{cfg.synid}')
    ensure_central_pswd()

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
                message='Please set the synchronization interval, in seconds. (Empty to quit) ',
                default=str(cfg.syncIns) if not LangExt.isblank(cfg.syncIns) else '0' if cfg.mode == SynodeMode.peer else '45',
                validator=MultiValidator(QuitValidator(), SyncInsValidator()))
        cfg.syncIns = float(sync_insnds) if not LangExt.isblank(sync_insnds) else 0
check_quit(_quit)

# 5 ports
def parse_web_jserv_ports(ports: str) -> List[int]:
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

def print_avail_ports(verbose: int = 0):
    print('Scanning local TCP ports, this may take a few seconds ...')
    try:
        report_port_ranges(1024, 65535, verbose=verbose)
    except Exception as e:
        print(f'Cannot list the ports: {e}')

while True:
    ports = session.prompt(
        message=f'Please set the ports. Format: "www-port : synode-service", [1024-65535]. '
                f"'?' to list available ports, '??' with owners.\n",
        default=default_ports(cli.settings),
        validator=MultiValidator(QuitValidator(), PortsValidator(allow_query=True)))
    if ports.strip() in ('?', '??'):
        print_avail_ports(verbose=len(ports.strip()) - 1)
        continue
    break

check_quit(_quit)

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
        message='Please set the public (reverse proxy) ip. Clear to quit:\n',
        default=cli.reportIp() if LangExt.isblank(cli.settings.proxyIp) else cli.settings.proxyIp,
        validator=MultiValidator(QuitValidator(), PIPValidator()))
    check_quit(_quit)

    reverseports = session.prompt(
        message='Please set the public ports. Format: "www-port:data-service-port":\n',
        default=default_proxy_ports(cli.settings),
        validator=MultiValidator(QuitValidator(), PortsValidator()))

    if LangExt.len(ports) == 0:
        _quit = True
    else:
        [cli.settings.webProxyPort, cli.settings.proxyPort] = parse_web_jserv_ports(reverseports)

check_quit(_quit)

caninstall = choice(
        message=f'All settings are collected, install Synode {cfg.synid}?',
        options=[(1, 'Yes'), (2, 'No, and quit')])

# 6A domain token
admin = cli.registry.find_synuser(users=cli.registry.synusers, id='admin')
admin_token = session.prompt(
    message=f'Please set domain token of user Admin, which must be set as the same across all synodes in the domain:\n',
    default=admin.pswd,
    validator=MultiValidator(QuitValidator(), DomainTokenValidator()))

check_quit(_quit)

admin.pswd = admin_token

# 6 ping hub
if cli.registry.config.mode != SynodeMode.hub.name:
    hub_node = cli.registry.find_hubpeer()
    if hub_node is None:
        session.prompt(message='Cannot find the hub node, which is possibly can be found automatically. Press Enter to continue.\n')
    else:
        s_j = cli.settings.jservs[hub_node.synid]
        hub_jserv = hub_node.jserv if LangExt.isblank(s_j) else s_j
        cli.settings.jservs[hub_node.synid] = session.prompt(
                message=f'Pinging the hub node, {hub_node.synid} ? (Empty to quit) ',
                default=hub_jserv,
                validator=MultiValidator(QuitValidator(), PJservValidator(cli.syn_protocol.protocolpath)))
        try:
            # rsp = cli.ping(hub_node.jserv)
            rep = cli.ping(cli.settings.jservs[hub_node.synid], timeout=6) 
        except Exception as e:
            print(e)
            print("There are errors while finding the hub node. But it can still work. Let's continue ...")

        go_on = choice( message=f'Continue installation? (Can re-configure, or can auto-connect if both nodes can visit Central)' if not has_run \
                        else f'Continue to save changes?',
                        options=[(1, 'Yes, go on.'),
                                 (2, 'No, stop here.')],
                        default=1)
        _quit = go_on == 2

check_quit(_quit)

def post_install():
    resp = cli.submit_mysettings()
    if resp is not None:
        cli.after_submit(resp)
    else:
        Utils.warn('TODO 0.7.6, RESP == NULL, handle errors...')

# 7 save & install
jredownloader = None

def jreprog_hook(blocknum, blocksize, totalsize):
    read = blocknum * blocksize
    if totalsize > 0:
        percent = min(100, read * 100 // totalsize)
        print(f"\rDownloading JRE... {percent}% ", end="")

if caninstall == 1:
    try:
        # in case central replied empty value
        cli.updateWithUi(market=cli.registry.config.org.orgType)
        v = cli.validate(ping_hub=False)
        if v is not None:
            session.prompt(message='There are error in settings / configurations ...')
            print(v, file=sys.stderr)
            _quit = True
            check_quit(_quit)

        jredownloader = cli.check_install_jre(jredownloader, cli_progress=jreprog_hook)

        cli.install()
        post_err = cli.postFix()
        post_install()
    except FileNotFoundError or IOError as e:
        Utils.warn(e)
        session.prompt('Setting up synodepy3 failed.')
        _quit = True
        check_quit(_quit)
    except PortfolioException as e:
        Utils.warn(e.msg)
        session.prompt('Configuration is updated with cautions. Check the details.')
        post_install() # let's still take effects for changes
        # e.g. reinstalling over an existing volume (dbs not empty) - the services,
        # possibly uninstalled at upgrade or removed manually, still need to be installed.
        _quit = choice(
            message='Continue to install the services?',
            options=[(1, 'Yes, continue.'),
                     (2, 'No, quit.')],
            default=1) == 2
        check_quit(_quit)

    if Utils.iswindows():
        session.prompt(f'Synode-cli is for the remote servers, and cannot install the required Windows services.\n'
                       'Please install it with the GUI version:\n'
                       './setup-gui.exe\n'
                       'And click "install Windows service" with default settings.')
    else:
        syn_templ, web_templ = generate_service_templ(cli.settings, cfg)
        # TODO merge with the cli function
        login_url = f'{"https" if cli.registry.config.https else "http"}://' + \
                    f'{ cli.settings.proxyIp if cli.settings.reverseProxy else "127.0.0.1"}:' + \
                    f'{cli.settings.webProxyPort if cli.settings.reverseProxy else cli.settings.webport}/login.html'
        login_tip = f'Login with user Id "{cli.registry.config.admin}" & password, your-domain-token at\n{login_url}\n\n'

        print(f'The service configuration files are generated: ./{syn_templ} & ./{web_templ}.')
        install_units = choice(
            message='Install them as systemd services now? (sudo required)',
            options=[(1, 'Yes, install the services.'),
                     (2, 'No, I will install them myself.')],
            default=1)

        states = {}
        if install_units == 1:
            srv_name = session.prompt(
                message='Service name (installed as <name>.service & <name>.web.service): ',
                default=cfg.synid, validator=SrvNameValidator()).strip().removesuffix('.service') or cfg.synid
            try:
                states = install_linusrvs(cli, {linusrv_synode: (f'{srv_name}.service', syn_templ),
                                                linusrv_websrv: (f'{srv_name}.web.service', web_templ)})
            except PermissionError as e:
                Utils.warn(e)

        if states:
            session.prompt(message=
                '\n'.join(f'{u}: {st}' for u, st in states.items()) + '\n\n' +
                'Check logs with:\n' +
                ''.join(f'journalctl -u {u} -f\n' for u in states) + '\n' +
                login_tip +
                'Return to quit Portfolio Setup ...')
        else:
            session.prompt(message=
                'Services are not installed.\n\n'
                'You can try the service with these two commands:\n'
               f'java -jar bin/{jserv_07_jar}\n'
               f'java -jar bin/{html_web_jar}\n\n'
                'Then ' + login_tip +
                'To install the services, copy the files to /etc/systemd/system/, then:\n'
                'sudo systemctl daemon-reload\n'
               f'sudo systemctl enable --now {Path(syn_templ).name} {Path(web_templ).name}\n\n'
                'Return to quit Portfolio Setup ...')

def main():
    '''
    The stub for pyproject.toml's main entry
    :return: 0
    '''
    return 0
