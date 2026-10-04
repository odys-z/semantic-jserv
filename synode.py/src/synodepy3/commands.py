
import os
import re
import signal
import subprocess
import sys
import time
import zipfile
from datetime import datetime
from typing import Dict, Optional, Tuple, cast

from anson.io.odysz.common import Utils
from invoke import task, Context, UnexpectedExit
from semanticshare.io.oz.jserv.docs.syn.singleton import sys_db, syn_db

from .__version__ import jar_ver, html_srver
from .installer_api import InstallerCli, dictionary_json, settings_json, web_inf, album_web_dist, web_host_json, \
    jserv_07_jar, html_web_jar, generate_service_templ

winsrv = 'winsrv'
winsrv_synode = f'{winsrv}.synode'
winsrv_websrv = f'{winsrv}.web'

install_html_w_bat  = os.path.join(winsrv, "install-html-w.bat")
install_jserv_w_bat = os.path.join(winsrv, "install-jserv-w.bat")
stop_w_bat    = os.path.join(winsrv, "stop-winsrv.bat")
restart_w_bat = os.path.join(winsrv, "restart-winsrv.bat")
winsrv_proc_exe = 'winsrv\portfolio-ia64.exe'

@task
def run_jserv(c, bin = 'bin'):
    def signal_handler(sig, frame):
        print('Ctrl+C detected. Performing cleanup...')
        time.sleep(2) # TODO accept signal from service
        print('Cleanup finished. Exiting.')
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    cd = bin or 'bin'
    if os.name == 'nt':
        cd = re.sub('/', '\\\\', cd)

    try:
        ret = c.run(f'cd {cd} && java -jar bin/jserv-album-{jar_ver}.jar')
        print(ret.ok)
    except KeyboardInterrupt as e:
        print('KeyboardInterrupt', e)
        time.sleep(.5)


def uninstall_wsrv_byname(srvname: str = None):
    ctx = Context()
    if srvname is None:
        srvname = InstallerCli().load_settings().envars[winsrv_synode]
    cmd = f'{install_jserv_w_bat} uninstall {srvname}'
    print(cmd)
    ctx.run(cmd)


def install_wsrv_byname(srvname: str):
    Utils.update_patterns(install_jserv_w_bat, {'@set jar_ver=[0-9\\.]+': f'@set jar_ver={jar_ver}'})

    ctx = Context()
    cmd = f'{install_jserv_w_bat} install {srvname}'
    print(cmd)
    ctx.run(cmd)
    return srvname


def install_htmlsrv(srvname: str):
    Utils.update_patterns(install_html_w_bat, {'@set jar_ver=[0-9\\.]+': f'@set jar_ver={html_srver}'})
    '''
    FIXME: This replace is not correct. See comments of upgrade_cli.
    '''
    ctx = Context()
    cmd = f'{install_html_w_bat} install {srvname}'
    print(cmd)
    ctx.run(cmd)
    return srvname


def stop_wsrv_byname(srvname: str):
    ctx = Context()
    cmd = f'{stop_w_bat} {winsrv_proc_exe} {srvname}'
    print(cmd)
    ctx.run(cmd)


def restart_wsrv_byname(srvname: str):
    ctx = Context()
    cmd = f'{restart_w_bat} {winsrv_proc_exe} {srvname}'
    print(cmd)
    ctx.run(cmd)


def stop_winsrvs() -> bool:
    """
    Stop the 2 Windows services saved in settings.envars[winsrv_synode / winsrv_websrv].
    :return: False if the service names are not found in settings.json.
    """
    envars = InstallerCli().load_settings().envars
    if winsrv_synode not in envars or winsrv_websrv not in envars:
        print('Error: cannot find target service name(s). The configuration are damaged.')
        return False

    for srvname in (envars[winsrv_synode], envars[winsrv_websrv]):
        try:
            stop_wsrv_byname(srvname)
        except UnexpectedExit as e:
            print(f"Error stopping {srvname}: {e}", file=sys.stderr)
    return True


def restart_wsrvs():
    """
    Restart the 2 Windows services saved in settings.envars[winsrv_synode / winsrv_websrv].
    """
    envars = InstallerCli().load_settings().envars
    for srvname in (envars[winsrv_synode], envars[winsrv_websrv]):
        try:
            restart_wsrv_byname(srvname)
        except UnexpectedExit as e:
            print(f"Error restarting {srvname}: {e}", file=sys.stderr)


def update_srv(pkg_path: str):
    """
    Update a running Synode install in-place, in the installation folder (cwd):
      1. Windows: stop the jserv-album and html-service services;
         Linux: uninstall (stop, disable, remove) the 2 systemd units saved in settings.json,
         keeping the unit files in backup_dir/linusrv/
      2. back up vol/dictionary.json, vol/*.db, WEB-INF/settings.json and
         web-dist/private/host.json into a dated backup-YYYYMMDD/ folder,
         preserving each file's exact relative sub-path
         (vol = WEB-INF/settings.json's "volume")
      3. unpack pkg_path over the current working directory
         (Windows: *.zip; Linux: *.tar.gz, by tar -xf)
      4. restore the backed-up files (so the new package doesn't clobber
         local data/config)
      5. Windows: restart both services;
         Linux: on user's confirmation, install & start the units regenerated for the new version
         (or the previous units, if unpacking failed)

    :param pkg_path: path to the update package, zip on Windows, tar.gz on Linux.
    """
    if not os.path.isfile(pkg_path):
        print(f'Error: update package not found: {pkg_path}')
        return

    cli = InstallerCli()
    cli.load_settings()

    backup_dir = f'backup-{datetime.now().strftime("%Y%m%d")}'

    # 1. stop services
    sudo, srvs = None, None
    if Utils.iswindows():
        if not stop_winsrvs():
            return
    else:
        sudo = Sudo()
        try:
            srvs = stop_linusrvs(cli, sudo, backup_dir)
        except (RuntimeError, PermissionError) as e:
            print(f'{e}\nUpgrade aborted. Removed unit files (if any) are kept in '
                  f'{os.path.abspath(os.path.join(backup_dir, linusrv))}', file=sys.stderr)
            return

    # 2. backup, preserving each file's relative sub-path (basename(basedir)/fname)
    #    under backup_dir
    vol = cli.settings.Volume()
    print(f'Backing up to: {os.path.abspath(backup_dir)}')

    backups = []  # [(orig-path, backup-path), ...]

    def stash(basedir: str, fname: str):
        """
        src = basedir + fname (basedir may be absolute, e.g. vol).
        dst = backup_dir/basename(basedir)/fname, e.g. backup-20260923/vol/dictionary.json
        """
        src = os.path.join(basedir, fname)
        if os.path.isfile(src):
            dst = os.path.join(backup_dir, os.path.basename(os.path.normpath(basedir)), fname)
            Utils.copy_anyway(src, dst, log=True)
            backups.append((src, dst))
        else:
            print(f'Skipped (not found): {src}')

    stash(vol, dictionary_json)
    stash(vol, sys_db)
    stash(vol, syn_db)
    stash(web_inf, settings_json)
    stash(album_web_dist, web_host_json)

    # 3. unpack the update package over cwd
    print(f'Unpacking {pkg_path} to {os.getcwd()} ...')
    unpacked = True
    if Utils.iswindows():
        with zipfile.ZipFile(pkg_path, 'r') as zf:
            zf.extractall('.')
    else:
        r = subprocess.run(['tar', '-xf', pkg_path, '-C', '.'], capture_output=True, text=True)
        if r.returncode != 0:
            unpacked = False
            print(f'Error unpacking {pkg_path}:\n{r.stderr}', file=sys.stderr)

    # 4. restore the backed-up files, from their mirrored sub-path back to the original
    #    (also when tar failed, as rollback of a partial extraction)
    for orig, backed in backups:
        Utils.copy_anyway(backed, orig, log=True)
        print(f'Restored: {orig}')

    # 5. restart services
    if Utils.iswindows():
        restart_wsrvs()
    elif srvs:
        restart_sysunits(cli, cast(Sudo, sudo), srvs, unpacked)

    print(f'Update {"complete" if unpacked else "FAILED"}. Backup kept at: {os.path.abspath(backup_dir)}')


# ---------------------------------------------------------------- Linux (systemd)

linusrv = 'linusrv'
linusrv_synode = f'{linusrv}.synode'
linusrv_websrv = f'{linusrv}.web'

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
    (WEB-INF/settings.json, like the Windows way), then daemon-reload, enable and (re)start them.
    Used by prompt.py's last step and by update_srv().

    :param units: {linusrv_synode | linusrv_websrv: (unit name, unit file)}, jserv first.
                  Unit files are generated by installer_api.generate_service_templ(), or backed up by stop_linusrvs().
    :return: {unit: state of systemctl is-active}, empty if user declined to overwrite, or failed.
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
    if existing and not confirm(f'{", ".join(existing)} already installed. Overwrite (and restart)?'):
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

    states = {}
    for unit, _ in units.values():  # jserv first
        r = sudo.run('systemctl', 'enable', unit)
        if r.returncode != 0:
            Utils.warn(f'Failed to enable {unit}: {r.stderr.strip()}')
        # restart: also takes effect for an overwritten, running unit
        r = sudo.run('systemctl', 'restart', unit)
        if r.returncode != 0:
            Utils.warn(f'Failed to start {unit}: {r.stderr.strip()}')
        states[unit] = _unit_active(unit)
    return states


def stop_linusrvs(cli: InstallerCli, sudo: Sudo, backup_dir: str) -> Dict[str, Tuple[str, str]]:
    """
    Uninstall (stop, disable, remove) the systemd units saved in settings.envars[linusrv_synode / linusrv_websrv],
    if there are, keeping a copy of each unit file in backup_dir/linusrv/.

    For services installed by user before the names were saved (synodepy3 < this patch), ask for the names.

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

    _daemon_reload(sudo)
    # jserv first, for install_linusrvs()
    return {k: removed[k] for k in (linusrv_synode, linusrv_websrv) if k in removed}


def restart_sysunits(cli: InstallerCli, sudo: Sudo, srvs: Dict[str, Tuple[str, str]], unpacked: bool):
    """
    On user's confirmation, reinstall and start the units uninstalled by stop_linusrvs():
    regenerated by installer_api.generate_service_templ() for the new version,
    or the backed-up unit files if unpacking failed.

    :param srvs: returned by stop_linusrvs()
    :param unpacked: whether the new package is unpacked successfully
    """
    from prompt_toolkit.shortcuts import confirm

    if unpacked:
        if not os.path.isfile(os.path.join('bin', jserv_07_jar)) or not os.path.isfile(os.path.join('bin', html_web_jar)):
            Utils.warn(f'bin/{jserv_07_jar} or bin/{html_web_jar} is not in the new package. '
                       'Is the synodepy3 running this upgrade the same version as the package?')
        syn_templ, web_templ = generate_service_templ(cli.settings, cli.registry.config)
        templs = {linusrv_synode: syn_templ, linusrv_websrv: web_templ}
        units = {k: (unit, templs[k]) for k, (unit, _) in srvs.items()}
        question = 'Install and start the services for the new version?'
    else:
        units = srvs
        question = 'Reinstall and restart the previous services?'

    states = install_linusrvs(cli, units, sudo) if confirm(question) else {}

    if states:
        for u, st in states.items():
            print(f'{u}: {st}')
    else:
        print('Services are not installed. To install them later, copy the files to /etc/systemd/system/:\n' +
              ''.join(f'  sudo install -m 644 {f} /etc/systemd/system/{u}\n' for u, f in units.values()) +
              '  sudo systemctl daemon-reload\n'
              f'  sudo systemctl enable --now {" ".join(u for u, _ in units.values())}')
