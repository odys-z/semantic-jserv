"""
Linux systemd units of Synode: <name>.service (jserv-album) & <name>.web.service (html-web).

The unit names are saved in WEB-INF/settings.json, envars[linusrv_synode / linusrv_websrv],
like the Windows services' names are in envars[winsrv_synode / winsrv_websrv].

Used by prompt.py (generate & install at the last step) and commands.update_srv() (uninstall & reinstall).
"""
import os
import subprocess
from typing import Dict, List, Optional, Tuple

from anson.io.odysz.common import Utils
from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings
from semanticshare.io.oz.syn.registry import SynodeConfig

from .__version__ import jar_ver, web_ver
from .installer_api import InstallerCli, settings_json, web_inf, jserv_07_jar, html_web_jar
from .jre_downloader import _jre_

linusrv = 'linusrv'
linusrv_synode = f'{linusrv}.synode'
linusrv_websrv = f'{linusrv}.web'


def generate_service_templ(s: AppSettings, c: SynodeConfig, xms:str='1g', xmx='8g'):
    """
    :param s: settings
    :param c: synode registry config
    :param xms: JRE option Xms
    :param xmx: JRE option Xmx
    :return: (synode service file, web service file), generated in cwd: <synid>.service, <synid>.web.service
    """

    cwd = os.getcwd()
    java_home = f'{cwd}/{_jre_}'
    synode_desc = f'Synode {jar_ver} {c.synid}'
    etc_syn = f"""[Unit]


Description={synode_desc}
After=network.target

[Service]
Type=simple
User={os.getlogin()}
WorkingDirectory={cwd}
Environment="JAVA_HOME={java_home}"
ExecStart={java_home}/bin/java -jar {cwd}/bin/{jserv_07_jar}
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="JAVA_OPTS=-Xms{xms} -Xmx{xmx}"

[Install]
WantedBy=multi-user.target
    """

    web_desc = f'Synode {web_ver} {c.synid}'
    etc_web = f"""[Unit]
Description={web_desc}
After=network.target

[Service]
Type=simple
User={os.getlogin()}
WorkingDirectory={cwd}
Environment="JAVA_HOME={java_home}"
ExecStart={java_home}/bin/java -jar {cwd}/bin/{html_web_jar}
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="JAVA_OPTS=-Xms512m -Xmx2g"

[Install] 
WantedBy=multi-user.target
    """
    syn_templ, web_templ = f'{c.synid}.service', f'{c.synid}.web.service'
    with open(syn_templ, "w") as fo:
        fo.write(etc_syn)
    with open(web_templ, "w") as fo:
        fo.write(etc_web)

    return syn_templ, web_templ

class Sudo:
    """
    Run commands as root. Asks for the password at most once, and not at all
    if running as root, or sudo has a cached credential / NOPASSWD.
    """
    def __init__(self):
        self.is_root = os.geteuid() == 0
        self._pswd: Optional[str] = None

    def ensure(self, retries: int = 3):
        # -n: non-interactive, returns 0 if sudo is available without password
        if self.is_root or subprocess.run(['sudo', '-n', 'true'], capture_output=True).returncode == 0:
            return
        from prompt_toolkit import prompt
        for _ in range(retries):
            p = prompt('[sudo] password: ', is_password=True)
            # -k: ignore cached timestamp, so this really validates the password
            # -S: read from stdin, input: feed the stdin, -p: empty prompt (otherwise the password is echoed)
            if subprocess.run(['sudo', '-S', '-k', '-p', '', 'true'],
                              input=p + '\n', text=True, capture_output=True).returncode == 0:
                self._pswd = p
                return
            print('Sorry, try again.')
        raise PermissionError('sudo authentication failed.')

    def run(self, *cmd: str, stdin: str = '') -> subprocess.CompletedProcess:
        if self.is_root:
            argv, inp = list(cmd), stdin
        elif self._pswd is None:
            argv, inp = ['sudo', '-n', *cmd], stdin
        else:
            argv, inp = ['sudo', '-S', '-p', '', *cmd], self._pswd + '\n' + stdin
        return subprocess.run(argv, input=inp, text=True, capture_output=True)


def _unit_exists(unit: str) -> bool:
    return bool(subprocess.run(['systemctl', 'list-unit-files', '--no-legend', unit],
                               capture_output=True, text=True).stdout.strip())


def _unit_active(unit: str) -> str:
    return subprocess.run(['systemctl', 'is-active', unit], capture_output=True, text=True).stdout.strip()


def _unit_path(unit: str, systemd_dir: str = '/etc/systemd/system') -> str:
    """
    Where the unit is installed (FragmentPath), or systemd_dir/unit if not installed.
    """
    return subprocess.run(['systemctl', 'show', '-p', 'FragmentPath', '--value', unit],
                          capture_output=True, text=True).stdout.strip() \
           or os.path.join(systemd_dir, unit)


def _daemon_reload(sudo: Sudo):
    r = sudo.run('systemctl', 'daemon-reload')
    if r.returncode != 0:
        Utils.warn(f'systemctl daemon-reload failed: {r.stderr.strip()}')


def install_linusrvs(cli: InstallerCli, units: Dict[str, Tuple[str, str]],
                     sudo: Optional[Sudo] = None) -> Dict[str, str]:
    """
    Install unit files as systemd services, save the names into settings.envars[linusrv_synode / linusrv_websrv]
    (WEB-INF/settings.json, like the Windows way), then daemon-reload and enable them,
    and start them by restart_sysunits(), on user's confirmation.
    Used by prompt.py's last step, and by reinstall_linusrvs() & stop_linusrvs() at upgrade.

    :param units: {linusrv_synode | linusrv_websrv: (unit name, unit file)}, jserv first.
                  Unit files are generated by generate_service_templ(), or backed up by stop_linusrvs().
    :return: {unit: state of systemctl is-active} of the installed units ('inactive' if not started),
             empty if user declined to overwrite, or failed.
    """
    from prompt_toolkit.shortcuts import confirm

    def install_unit_file(unit_file: str, unit: str) -> bool:
        dest = _unit_path(unit)
        r = sudo.run('install', '-m', '644', '-o', 'root', '-g', 'root', unit_file, dest)
        if r.returncode == 0:
            print(f'Installed {unit_file} -> {dest}')
            return True
        Utils.warn(f'Failed to install {unit_file} -> {dest}: {r.stderr.strip()}')
        return False

    existing = [u for u, _ in units.values() if _unit_exists(u)]
    if existing and not confirm(f'{", ".join(existing)} already installed. Overwrite?'):
        return {}

    sudo = sudo or Sudo()
    sudo.ensure()

    for unit, unit_file in units.values():
        if not install_unit_file(unit_file, unit):
            return {}

    # Save before starting services, like installWinsrv()
    for k, (unit, _) in units.items():
        cli.settings.envars[k] = unit
    cli.settings.toFile(os.path.join(web_inf, settings_json))

    _daemon_reload(sudo)

    names = [unit for unit, _ in units.values()]  # jserv first
    for unit in names:
        r = sudo.run('systemctl', 'enable', unit)
        if r.returncode != 0:
            Utils.warn(f'Failed to enable {unit}: {r.stderr.strip()}')

    return restart_sysunits(sudo, names)


def restart_sysunits(sudo: Sudo, units: List[str]) -> Dict[str, str]:
    """
    On user's confirmation, (re)start the installed units, or print the commands for starting them later.

    :param units: unit names, in starting order (jserv first)
    :return: {unit: state of systemctl is-active}
    """
    from prompt_toolkit.shortcuts import confirm

    if confirm(f'Start {", ".join(units)} now?'):
        for unit in units:
            # restart: also takes effect for an overwritten, running unit
            r = sudo.run('systemctl', 'restart', unit)
            if r.returncode != 0:
                Utils.warn(f'Failed to start {unit}: {r.stderr.strip()}')
    else:
        # an overwritten unit may still be running with the previous unit file
        print('Start the services later with:\n' +
              ''.join(f'  sudo systemctl {"restart" if _unit_active(u) == "active" else "start"} {u}\n'
                      for u in units))

    return {unit: _unit_active(unit) for unit in units}


def stop_linusrvs(cli: InstallerCli, sudo: Sudo, backup_dir: str) -> Dict[str, Tuple[str, str]]:
    """
    Uninstall (stop, disable, remove) the systemd units saved in settings.envars[linusrv_synode / linusrv_websrv],
    if there are, keeping a copy of each unit file in backup_dir/linusrv/.

    For services installed by user before the names were saved (synodepy3 < this patch), ask for the names.

    If a unit cannot be stopped or removed, the units already uninstalled are restored
    (reinstalled and, on user's confirmation, started, see install_linusrvs()) before raising.

    :return: {linusrv_synode | linusrv_websrv: (unit name, backed-up unit file)}
    :raise RuntimeError: a unit cannot be stopped or removed.
    """
    from prompt_toolkit import prompt

    def ask_unit(label: str, dflt: str) -> Optional[str]:
        while True:
            n = prompt(f'systemd service of {label} (empty to skip): ', default=dflt).strip()
            if not n:
                Utils.warn(f'{label}: skipped, will not be stopped / reinstalled.')
                return None
            n = n if n.endswith('.service') else f'{n}.service'
            if _unit_exists(n):
                return n
            print(f'{n} is not found by systemctl.')

    names: dict[str, str | None] = {k: cli.settings.envars.get(k) for k in (linusrv_synode, linusrv_websrv)}
    if not any(names.values()):
        synid = cli.registry.config.synid
        Utils.warn('No systemd service names are saved in settings.json.')
        names = {linusrv_synode: ask_unit('synode (jserv-album)', f'{synid}.service'),
                 linusrv_websrv: ask_unit('html-web', f'{synid}.web.service')}

    for k, unit in names.items():
        if unit and not _unit_exists(unit):
            Utils.warn(f'{unit} (saved in settings.json) is not installed, skipped.')
            names[k] = None

    if not any(names.values()):
        return {}

    sudo.ensure()

    removed = {}

    def jserv_first() -> Dict[str, Tuple[str, str]]:
        return {k: removed[k] for k in (linusrv_synode, linusrv_websrv) if k in removed}

    try:
        for k in (linusrv_websrv, linusrv_synode):  # web first
            unit = names[k]
            if not unit:
                continue
            unit_file = _unit_path(unit)

            print(f'Stopping {unit} ...')
            r = sudo.run('systemctl', 'stop', unit)
            if r.returncode != 0 or _unit_active(unit) == 'active':
                raise RuntimeError(f'Error stopping {unit}: {r.stderr.strip()}')
            sudo.run('systemctl', 'disable', unit)

            bak = os.path.join(backup_dir, linusrv, unit)
            Utils.copy_anyway(unit_file, bak, log=True)
            r = sudo.run('rm', '-f', unit_file)
            if r.returncode != 0:
                raise RuntimeError(f'Error removing {unit_file}: {r.stderr.strip()}')
            print(f'Uninstalled {unit}')
            removed[k] = (unit, bak)
    except RuntimeError:
        _daemon_reload(sudo)
        if removed:
            Utils.warn(f'Restoring the uninstalled service(s): {", ".join(u for u, _ in removed.values())}')
            install_linusrvs(cli, jserv_first(), sudo)
        raise

    _daemon_reload(sudo)
    return jserv_first()  # for install_linusrvs()


def reinstall_linusrvs(cli: InstallerCli, sudo: Sudo, srvs: Dict[str, Tuple[str, str]], unpacked: bool):
    """
    Upgrade's last step: install again the units uninstalled by stop_linusrvs(),
    regenerated by generate_service_templ() for the new version,
    or the backed-up unit files if unpacking failed.
    Starting them needs user's confirmation, see install_linusrvs() / restart_sysunits().

    :param srvs: returned by stop_linusrvs()
    :param unpacked: whether the new package is unpacked successfully
    """
    if unpacked:
        if not os.path.isfile(os.path.join('bin', jserv_07_jar)) or not os.path.isfile(os.path.join('bin', html_web_jar)):
            Utils.warn(f'bin/{jserv_07_jar} or bin/{html_web_jar} is not in the new package. '
                       'Is the synodepy3 running this upgrade the same version as the package?')
        syn_templ, web_templ = generate_service_templ(cli.settings, cli.registry.config)
        templs = {linusrv_synode: syn_templ, linusrv_websrv: web_templ}
        units = {k: (unit, templs[k]) for k, (unit, _) in srvs.items()}
    else:
        Utils.warn('Reinstalling the previous services ...')
        units = srvs

    states = install_linusrvs(cli, units, sudo)

    if states:
        for u, st in states.items():
            print(f'{u}: {st}')
    else:
        print('Services are not installed. To install them later, copy the files to /etc/systemd/system/:\n' +
              ''.join(f'  sudo install -m 644 {f} /etc/systemd/system/{u}\n' for u, f in units.values()) +
              '  sudo systemctl daemon-reload\n'
              f'  sudo systemctl enable --now {" ".join(u for u, _ in units.values())}')
