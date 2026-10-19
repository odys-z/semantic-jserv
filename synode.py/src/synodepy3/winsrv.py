"""
Windows services of Synode: the synode (jserv-album) & web (html-web) services, installed and controlled
by Apache Commons Daemon Procrun, through the bats in winsrv/.

The service names are saved in WEB-INF/settings.json, envars[winsrv_synode / winsrv_websrv],
like the Linux units' names are in envars[linusrv_synode / linusrv_websrv] (linunits.py).

Used by prompt.py & __main__.py (install at the last step), cli.uninst_srv() and commands.update_srv().
"""
import os
import re
import subprocess
import sys
import time
from typing import cast, Optional

from anson.io.odysz.common import Utils
from invoke import Context, UnexpectedExit
from semanticshare.io.oz.syn.pkgresource import PkgResources

from .__version__ import jar_ver, html_srver
from .installer_api import InstallerCli, settings_json, web_inf

winsrv = 'winsrv'
winsrv_synode = f'{winsrv}.synode'
winsrv_websrv = f'{winsrv}.web'

install_html_w_bat  = os.path.join(winsrv, "install-html-w.bat")
install_jserv_w_bat = os.path.join(winsrv, "install-jserv-w.bat")
stop_w_bat    = os.path.join(winsrv, "stop-winsrv.bat")
restart_w_bat = os.path.join(winsrv, "restart-winsrv.bat")
winsrv_proc_exe = os.path.join(winsrv, "portfolio-ia64.exe")


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


def winsrvs_installed(cli: InstallerCli) -> list[str]:
    """
    :return: the synode & web services' names (by registry.config.synid) already installed,
             for InstallerCli.chk_srv_install().
    """
    return [s for s in (cli.gen_wsrv_name(), cli.gen_html_srvname()) if winsrv_exists(s)]


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


def winsrv_state(srvname: str) -> Optional[str]:
    """
    :return: the service's state from 'sc query', e.g. 'RUNNING', 'STOP_PENDING', 'STOPPED';
             None if the service is not installed (or sc failed)
    """
    r = subprocess.run(['sc', 'query', srvname], capture_output=True, text=True)
    if r.returncode != 0:
        return None
    m = re.search(r'STATE\s*:\s*\d+\s+(\w+)', r.stdout)
    return m.group(1) if m else None


def wait_winsrv_state(srvname: str, done: tuple, wait: float = 30) -> Optional[str]:
    """
    Poll the service's state (sc query, no elevation needed) until it is one of done,
    for at most wait seconds.
    :return: the last state
    """
    deadline = time.monotonic() + wait
    state = winsrv_state(srvname)
    while state not in done and time.monotonic() < deadline:
        time.sleep(1)
        state = winsrv_state(srvname)
    return state


def wait_winsrv_stopped(srvname: str, wait: float = 30) -> Optional[str]:
    """
    Wait until the service is stopped (or not installed), as Procrun //SS may give up
    (exit code 6) while the service is still STOP_PENDING.
    :return: the last state, 'STOPPED' or None (not installed) for stopped
    """
    return wait_winsrv_state(srvname, ('STOPPED', None), wait)


def run_winsrv_bat(bat: str, names: list, log: str) -> bool:
    """
    Run bat (stop-winsrv.bat / restart-winsrv.bat: [1] prunsrv [2] service name) for each service,
    as Administrator: in-process if this process is one, otherwise in one elevated cmd, asking the
    UAC permission once for all of them (like install_winsrvs()), instead of Procrun asking once per
    call. The elevated output goes to log, printed afterwards.
    Exit codes are only reported; callers verify the services' states (sc query).
    :return: False if the user declined the UAC request
    """
    if is_winadmin():
        for s in names:
            cmd = f'{bat} {winsrv_proc_exe} {s}'
            print(cmd)
            try:
                Context().run(cmd)
            except UnexpectedExit as e:
                print(f'Error: {cmd}: exit code {e.result.exited}', file=sys.stderr)
        return True

    calls = ' & '.join(f'call {bat} {winsrv_proc_exe} {s}' for s in names)
    cmds = f'cd /d "{os.getcwd()}" && ({calls}) > "{log}" 2>&1'
    print(f'[elevated] {cmds}')
    try:
        code = run_elevated(f'/c "{cmds}"')
    except PermissionError as e:
        print(e, file=sys.stderr)
        return False
    print(f'Elevated {os.path.basename(bat)} exited with code {code}. Log: {os.path.abspath(log)}')
    if os.path.isfile(log):
        with open(log, encoding='utf-8', errors='replace') as f:
            print(f.read())
    return True


def stop_winsrvs(wait: float = 30, log: str = 'winsrv-stop.log') -> bool:
    """
    Stop the 2 Windows services saved in settings.envars[winsrv_synode / winsrv_websrv], by
    stop-winsrv.bat (Procrun //SS, which calls the service's StopMethod, e.g. SynotierJettyApp.jvmStop()),
    as Administrator (run_winsrv_bat(), one UAC request), then verify each is stopped (sc query).
    If one doesn't stop, the ones that did are restarted, so a failed stop leaves the synode as it was.

    :param wait: seconds to wait for a service to leave STOP_PENDING, after Procrun's own wait
    :return: False if the service names are not found in settings.json, or a service can't be stopped;
             the upgrade must not unpack over the running services' files (jars, portfolio-ia64.exe).
    """
    envars = InstallerCli().load_settings().envars
    if winsrv_synode not in envars or winsrv_websrv not in envars:
        print('Error: cannot find target service name(s). The configuration are damaged.')
        return False

    names = [envars[winsrv_synode], envars[winsrv_websrv]]
    running = [s for s in names if winsrv_state(s) not in ('STOPPED', None)]
    for s in names:
        if s not in running:
            print(f'{s}: {winsrv_state(s) or "not installed"}')
    if not running:
        return True

    if not run_winsrv_bat(stop_w_bat, running, log):
        print('Upgrade aborted, nothing is changed.', file=sys.stderr)
        return False

    states = {s: wait_winsrv_stopped(s, wait) for s in running}
    stuck = {s: st for s, st in states.items() if st not in ('STOPPED', None)}
    for s, st in states.items():
        print(f'{s}: {st or "not installed"}')
    if not stuck:
        return True

    print(f'Error: {", ".join(f"{s} is still {st}" for s, st in stuck.items())}.\n'
          f'  Upgrade aborted, nothing is changed. See logs\\commons-daemon.*.log and the services\' stdout logs\n'
          f'  (StopMethod, e.g. jvmStop) for why; "sc queryex <service>" shows the PID, which an administrator\n'
          f'  can end with "taskkill /F /PID <pid>".', file=sys.stderr)
    stopped = [s for s in running if s not in stuck]
    if stopped:
        restart_wsrvs(stopped)
    return False


def restart_wsrvs(names: list = None, wait: float = 30, log: str = 'winsrv-start.log') -> bool:
    """
    Start the Windows services, default the 2 saved in settings.envars[winsrv_synode / winsrv_websrv],
    by restart-winsrv.bat (Procrun //ES) as Administrator (run_winsrv_bat(), one UAC request),
    then verify each is RUNNING (sc query).
    :return: True if all of them are running
    """
    if names is None:
        envars = InstallerCli().load_settings().envars
        names = [envars[winsrv_synode], envars[winsrv_websrv]]

    if not run_winsrv_bat(restart_w_bat, names, log):
        return False

    states = {s: wait_winsrv_state(s, ('RUNNING', 'STOPPED', None), wait) for s in names}
    for s, st in states.items():
        print(f'{s}: {st or "not installed"}')
    failed = [s for s, st in states.items() if st != 'RUNNING']
    if failed:
        print(f'Error: not running: {", ".join(failed)}. See logs\\commons-daemon.*.log and the services\'\n'
              f'  stderr / stdout logs (StartMethod, e.g. jvmStart) for why.', file=sys.stderr)
    return not failed


def apply_winsrv_resources(cli: InstallerCli, old: PkgResources, new: PkgResources) -> bool:
    """
    Upgrade's step 5 on Windows: restart the 2 services. If the new package's versions differ from the
    installed one's (bin/resource.json versions), the services' names and jars have changed: uninstall
    them, then install them with the new names (settings.envars) and jars (the bats) and start them.
    :param old: the installed package's manifest
    :param new: the new package's manifest
    :return: False if a service is not installed or not running
    """
    ov, nv = old.versions, new.versions
    keys = ('jar_ver', 'web_ver', 'html_srver')
    if all(ov.get(k) == nv.get(k) for k in keys):
        # The unpacking replaced the install bats with the package's unstamped ones (e.g. @set jar_ver=0.8.0
        # in a 0.8.1 package): stamp them again, so a later reinstall / uninstall uses the installed jars.
        stamp_winsrv_bats(nv.get('jar_ver'), nv.get('html_srver'))
        return restart_wsrvs()

    envars = cli.settings.envars
    print('Reinstalling the services: ' + ', '.join(f'{k} {ov.get(k)} -> {nv.get(k)}' for k in keys))
    states = install_winsrvs(cli, versions=nv, replace=(envars.get(winsrv_synode), envars.get(winsrv_websrv)))
    for s, ok in states.items():
        print(f'{s}: {"installed" if ok else "NOT installed"}')
    started = restart_wsrvs()
    return all(states.values()) and started
