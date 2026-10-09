
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
from .installer_api import InstallerCli, dictionary_json, settings_json, web_inf, album_web_dist, web_host_json, \
    html_service_json
from .systemd_units import Sudo, linusrv, stop_linusrvs, reinstall_linusrvs

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
      2. back up vol/dictionary.json, vol/*.db, WEB-INF/settings.json, WEB-INF/html-service.json
         and web-dist/private/host.json into a dated backup-YYYYMMDD/ folder,
         preserving each file's exact relative sub-path
         (vol = WEB-INF/settings.json's "volume")
      3. unpack pkg_path over the current working directory
         (Windows: *.zip; Linux: *.tar.gz, by tar -xf)
      4. restore the backed-up files (so the new package doesn't clobber
         local data/config)
      5. Windows: restart both services;
         Linux: install the units regenerated for the new version (or the previous units,
         if unpacking failed), and start them on user's confirmation

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
            print(f'{e}\nUpgrade aborted. Unit files of the uninstalled services (if any) are kept in '
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
    stash(web_inf, html_service_json)
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
        reinstall_linusrvs(cli, cast(Sudo, sudo), srvs, unpacked)

    print(f'Update {"complete" if unpacked else "FAILED"}. Backup kept at: {os.path.abspath(backup_dir)}')
