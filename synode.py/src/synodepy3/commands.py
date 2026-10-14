
import filecmp
import os
import re
import signal
import subprocess
import sys
import time
import zipfile
from datetime import datetime
from typing import cast

from anson.io.odysz.common import Utils
from invoke import task, Context, UnexpectedExit
from semanticshare.io.oz.jserv.docs.syn.singleton import sys_db, syn_db

from .__version__ import jar_ver, html_srver
from semanticshare.io.oz.syn.pkgresource import PkgResources

from .installer_api import promote_upgrade_whl, load_resources, running_resources, read_package_res, \
    resource_json, InstallerCli, dictionary_json, settings_json, web_inf, album_web_dist, web_host_json, \
    html_service_json
from .systemd_units import Sudo, stop_sysunits, restart_sysunits, apply_unit_resources

winsrv = 'winsrv'
winsrv_synode = f'{winsrv}.synode'
winsrv_websrv = f'{winsrv}.web'

install_html_w_bat  = os.path.join(winsrv, "install-html-w.bat")
install_jserv_w_bat = os.path.join(winsrv, "install-jserv-w.bat")
stop_w_bat    = os.path.join(winsrv, "stop-winsrv.bat")
restart_w_bat = os.path.join(winsrv, "restart-winsrv.bat")
winsrv_proc_exe = os.path.join(winsrv, "portfolio-ia64.exe")

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


def stamp_winsrv_bats(jver: str = None, hver: str = None):
    """
    Update the jar versions in the install bats. Done without elevation, before installing.
    :param jver: jserv-album's version, default this synodepy3's (__version__.jar_ver);
                 an upgrade takes the new package's (bin/resource.json versions.jar_ver)
    :param hver: html-web's version, default __version__.html_srver; or versions.html_srver
    """
    Utils.update_patterns(install_jserv_w_bat, {'@set jar_ver=[0-9\\.]+': f'@set jar_ver={jver or jar_ver}'})
    Utils.update_patterns(install_html_w_bat, {'@set jar_ver=[0-9\\.]+': f'@set jar_ver={hver or html_srver}'})


def is_winadmin() -> bool:
    try:
        import ctypes
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except (AttributeError, OSError):
        return False


def run_elevated(params: str, exe: str = 'cmd.exe') -> int:
    """
    Run exe with params as Administrator, asking the UAC permission once, and wait for it.
    Child processes (e.g. Procrun, portfolio-ia64.exe) inherit the elevated token, so no more confirmations.

    :return: exit code of the elevated process
    :raise PermissionError: user declined the UAC request
    """
    import ctypes
    from ctypes import wintypes

    class SHELLEXECUTEINFOW(ctypes.Structure):
        _fields_ = [('cbSize', wintypes.DWORD), ('fMask', ctypes.c_ulong), ('hwnd', wintypes.HWND),
                    ('lpVerb', wintypes.LPCWSTR), ('lpFile', wintypes.LPCWSTR),
                    ('lpParameters', wintypes.LPCWSTR), ('lpDirectory', wintypes.LPCWSTR),
                    ('nShow', ctypes.c_int), ('hInstApp', wintypes.HINSTANCE), ('lpIDList', ctypes.c_void_p),
                    ('lpClass', wintypes.LPCWSTR), ('hkeyClass', wintypes.HKEY), ('dwHotKey', wintypes.DWORD),
                    ('hIconOrMonitor', wintypes.HANDLE), ('hProcess', wintypes.HANDLE)]

    SEE_MASK_NOCLOSEPROCESS = 0x00000040
    SW_SHOWMINNOACTIVE = 7
    INFINITE = 0xFFFFFFFF
    ERROR_CANCELLED = 1223

    sei = SHELLEXECUTEINFOW()
    sei.cbSize = ctypes.sizeof(sei)
    sei.fMask = SEE_MASK_NOCLOSEPROCESS
    sei.lpVerb = 'runas'
    sei.lpFile = exe
    sei.lpParameters = params
    sei.lpDirectory = os.getcwd()
    sei.nShow = SW_SHOWMINNOACTIVE

    shell32, kernel32 = ctypes.windll.shell32, ctypes.windll.kernel32
    shell32.ShellExecuteExW.argtypes = [ctypes.POINTER(SHELLEXECUTEINFOW)]
    kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel32.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]

    if not shell32.ShellExecuteExW(ctypes.byref(sei)):
        err = ctypes.GetLastError()
        if err == ERROR_CANCELLED:
            raise PermissionError('Administrator permission was declined. Services are not installed.')
        raise ctypes.WinError(err)

    code = wintypes.DWORD(0)
    try:
        kernel32.WaitForSingleObject(sei.hProcess, INFINITE)
        kernel32.GetExitCodeProcess(sei.hProcess, ctypes.byref(code))
    finally:
        kernel32.CloseHandle(sei.hProcess)
    return code.value


def winsrv_exists(srvname: str) -> bool:
    return subprocess.run(['sc', 'query', srvname], capture_output=True).returncode == 0


def install_winsrvs(cli: InstallerCli, log: str = 'winsrv-install.log',
                    versions: dict = None, replace: tuple = ()) -> dict[str, bool]:
    """
    Install the synode & web services, asking the administrator permission only once.
    Service names are saved in settings.envars[winsrv_synode / winsrv_websrv] before installing.

    :param versions: an upgrade's new versions, bin/resource.json versions {jar_ver, web_ver, html_srver},
                     for the service names and the bats; default this synodepy3's (__version__.py)
    :param replace: (synode service, web service) installed before, uninstalled first (upgrade)
    :return: {service-name: installed}
    :raise PermissionError: user declined the UAC request
    """
    versions = versions or {}
    srvname = cli.gen_wsrv_name(versions.get('jar_ver'))
    websrvname = cli.gen_html_srvname(versions.get('web_ver'))
    cli.settings.envars.update({winsrv_synode: srvname, winsrv_websrv: websrvname})

    # 2026-01-06 Must save before start service to avoid competition.
    cli.settings.toFile(cast(str, os.path.join(web_inf, settings_json)))

    stamp_winsrv_bats(versions.get('jar_ver'), versions.get('html_srver'))

    old = [(bat, name) for bat, name in zip((install_jserv_w_bat, install_html_w_bat), replace) if name]
    if is_winadmin():
        for bat, name in old:
            try:
                Context().run(f'{bat} uninstall {name}')
            except UnexpectedExit as e:
                print(f'Error uninstalling {name}: {e}', file=sys.stderr)
        for inst, name in ((install_wsrv_byname, srvname), (install_htmlsrv, websrvname)):
            try:
                inst(name, stamp=False)
            except UnexpectedExit as e:
                print(f'Error installing {name}: {e}', file=sys.stderr)
    else:
        # The elevated cmd.exe starts in System32, so cd /d first.
        # cmd /c strips the first and the last quotes only, keeping the inner ones.
        uninst = ''.join(f'call {bat} uninstall {name} & ' for bat, name in old)
        cmds = (f'cd /d "{os.getcwd()}" && '
                f'({uninst}call {install_jserv_w_bat} install {srvname} & '
                f'call {install_html_w_bat} install {websrvname}) > "{log}" 2>&1')
        print(f'[elevated] {cmds}')
        code = run_elevated(f'/c "{cmds}"')
        if os.path.isfile(log):
            with open(log, encoding='utf-8', errors='replace') as f:
                print(f.read())
        print(f'Elevated installation exited with code {code}. Log: {os.path.abspath(log)}')

    return {s: winsrv_exists(s) for s in (srvname, websrvname)}


def install_wsrv_byname(srvname: str, stamp: bool = True):
    if stamp:
        Utils.update_patterns(install_jserv_w_bat, {'@set jar_ver=[0-9\\.]+': f'@set jar_ver={jar_ver}'})

    ctx = Context()
    cmd = f'{install_jserv_w_bat} install {srvname}'
    print(cmd)
    ctx.run(cmd)
    return srvname


def install_htmlsrv(srvname: str, stamp: bool = True):
    if stamp:
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


def apply_winsrv_resources(cli: InstallerCli, old: PkgResources, new: PkgResources) -> bool:
    """
    Upgrade's step 5 on Windows: restart the 2 services. If the new package's versions differ from the
    installed one's (bin/resource.json versions), the services' names and jars have changed: uninstall
    them, then install them with the new names (settings.envars) and jars (the bats) and start them.
    :param old: the installed package's manifest
    :param new: the new package's manifest
    :return: False if a service is not installed
    """
    ov, nv = old.versions, new.versions
    keys = ('jar_ver', 'web_ver', 'html_srver')
    if all(ov.get(k) == nv.get(k) for k in keys):
        restart_wsrvs()
        return True

    envars = cli.settings.envars
    print('Reinstalling the services: ' + ', '.join(f'{k} {ov.get(k)} -> {nv.get(k)}' for k in keys))
    states = install_winsrvs(cli, versions=nv, replace=(envars.get(winsrv_synode), envars.get(winsrv_websrv)))
    for s, ok in states.items():
        print(f'{s}: {"installed" if ok else "NOT installed"}')
    restart_wsrvs()
    return all(states.values())


def update_srv(pkg_path: str, force: bool = False):
    """
    Update a running Synode install in-place, in the installation folder (cwd):
      0. check the new package accepts the installed version: its bin/resource.json upgrade_from
         {min, max}, and no downgrade (PkgResources.refuses()); read out of the package, before
         anything is stopped or changed, so a refused upgrade changes nothing
      1. stop the 2 services saved in settings.json (Windows services, or Linux systemd units,
         which stay installed); nothing is upgraded if they can't be stopped
      2. back up vol/dictionary.json, vol/*.db, WEB-INF/settings.json, WEB-INF/html-service.json
         and web-dist/private/host.json into a dated backup-YYYYMMDD/ folder,
         preserving each file's exact relative sub-path
         (vol = WEB-INF/settings.json's "volume")
         Also kept there, as a reference only (not restored): bin/resource.json, the installed
         package's manifest (or, for packages before it, installer_api.running_resources()).
      3. unpack pkg_path over the current working directory
         (Windows: *.zip; Linux: *.tar.gz, by tar -xf)
      4. copy the backed-up files back, over the package's ones (so the new package doesn't clobber
         local data/config), and verify each copy
      5. point the services to the new package's files, by comparing its bin/resource.json with the
         installed one, and restart them:
         Linux: replace the changed jar paths in the unit files (systemd_units.apply_unit_resources());
         Windows: if the versions changed, reinstall the services with the new names and jars, saved
         in settings.envars (apply_winsrv_resources())
      6. Linux: install the new package's bin/synode_py3-*.whl, the upgrade tool itself, so that the
         next upgrade runs the new version (installer_api.promote_upgrade_whl()). Windows: the new
         upgrade.exe is unpacked as upgrade.pkg, beside the running upgrade.exe, and moved by setup-*.
      No step asks the user. Linux: sudo asks for a password only if it needs one; Windows: installing
      services needs the administrator, asked once (UAC) unless upgrade.exe runs as one.

    :param pkg_path: path to the update package, zip on Windows, tar.gz on Linux.
    :param force: upgrade even if step 0 refuses (support cases)
    """
    if not os.path.isfile(pkg_path):
        print(f'Error: update package not found: {pkg_path}')
        return

    cli = InstallerCli()
    cli.load_settings()

    # 0. does the new package accept the installed version?
    installed = load_resources() or running_resources()
    try:
        pkg_res = read_package_res(pkg_path)
    except Exception as e:
        print(f'Error: cannot read {pkg_path}: {e}\nUpgrade aborted, nothing is changed.', file=sys.stderr)
        return
    if pkg_res is None:
        print(f'Warning: the package has no {resource_json}, its versions are not checked.', file=sys.stderr)
    else:
        why = pkg_res.refuses(installed.jar_ver(), force)
        if why:
            print(f'Upgrade refused: {why}.\nNothing is changed. To upgrade anyway: --force', file=sys.stderr)
            return
        print(f'Upgrading {installed.jar_ver()} -> {pkg_res.jar_ver()}' + (' (forced)' if force else ''))

    backup_dir = f'backup-{datetime.now().strftime("%Y%m%d")}'

    # 1. stop services
    sudo, units = None, None
    if Utils.iswindows():
        if not stop_winsrvs():
            return
    else:
        sudo = Sudo()
        try:
            units = stop_sysunits(cli, sudo)
        except PermissionError as e:
            print(e, file=sys.stderr)
        if not units:
            print('Upgrade aborted, nothing is changed.', file=sys.stderr)
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
    stash(web_inf, html_service_json)
    stash(album_web_dist, web_host_json)

    # the installed package's manifest: a reference, not restored (the new package's one replaces it)
    old_res = installed
    if os.path.isfile(resource_json):
        Utils.copy_anyway(resource_json, os.path.join(backup_dir, resource_json), log=True)

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
    restored = True
    for orig, backed in backups:
        Utils.copy_anyway(backed, orig, log=True)
        if filecmp.cmp(backed, orig, shallow=False):
            print(f'Restored: {orig}')
        else:
            restored = False
            print(f'Error: {orig} is not the same as its backup {backed}', file=sys.stderr)

    # 5. point the services to the new package's files, restart them
    new_res = load_resources() if unpacked else None
    if unpacked and new_res is None:
        print(f'Warning: the package has no {resource_json}, the services are kept as they were.', file=sys.stderr)
    applied = True
    if Utils.iswindows():
        if new_res:
            applied = apply_winsrv_resources(cli, old_res, new_res)
        else:
            restart_wsrvs()
    else:
        if new_res:
            applied = apply_unit_resources(cast(Sudo, sudo), units, old_res, new_res)
        for u, st in restart_sysunits(cast(Sudo, sudo), units, ask=False).items():
            print(f'{u}: {st}')

    # 6. the upgrade tool itself (Linux), last: the running process keeps the modules it has loaded
    if unpacked and not Utils.iswindows():
        promote_upgrade_whl()

    ok = unpacked and restored and applied
    print(f'Update {"complete" if ok else "FAILED"}. Backup kept at: {os.path.abspath(backup_dir)}')
