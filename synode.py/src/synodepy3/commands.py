
import os
import re
import signal
import sys
import time
import zipfile
from datetime import datetime

from anson.io.odysz.common import Utils
from invoke import task, Context, UnexpectedExit
from semanticshare.io.oz.jserv.docs.syn.singleton import sys_db, syn_db

from .__version__ import jar_ver, html_srver
from .installer_api import InstallerCli, dictionary_json, settings_json, web_inf, album_web_dist, web_host_json

winsrv = 'winsrv'
winsrv_synode = f'{winsrv}.synode'
winsrv_websrv = f'{winsrv}.web'

install_html_w_bat  = os.path.join(winsrv, "install-html-w.bat")
install_jserv_w_bat = os.path.join(winsrv, "install-jserv-w.bat")
stop_w_bat    = os.path.join(winsrv, "stop-winsrv.bat")
restart_w_bat = os.path.join(winsrv, "restart-winsrv.bat")

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

    ctx = Context()
    cmd = f'{install_html_w_bat} install {srvname}'
    print(cmd)
    ctx.run(cmd)
    return srvname


def stop_wsrv_byname(srvname: str):
    ctx = Context()
    cmd = f'{stop_w_bat} {srvname}'
    print(cmd)
    ctx.run(cmd)


def restart_wsrv_byname(srvname: str):
    ctx = Context()
    cmd = f'{restart_w_bat} {srvname}'
    print(cmd)
    ctx.run(cmd)


def update_srv(zip_path: str):
    """
    Update a running Synode install in-place:
      1. stop the jserv-album and html-service Windows services
      2. back up vol/dictionary.json, vol/*.db, WEB-INF/settings.json and
         web-dist/private/host.json into a dated backup-YYYYMMDD/ folder,
         preserving each file's exact relative sub-path
         (vol = WEB-INF/settings.json's "volume")
      3. unpack zip_path over the current working directory
      4. restore the backed-up files (so the new package doesn't clobber
         local data/config)
      5. restart both services

    :param zip_path: path to the update package (zip).
    """
    if not os.path.isfile(zip_path):
        print(f'Error: update package not found: {zip_path}')
        return

    cli = InstallerCli()
    cli.load_settings()

    if winsrv_synode not in cli.settings.envars or winsrv_websrv not in cli.settings.envars:
        print('Error: cannot find target service name(s). The configuration are damaged.')
        return

    syn_srvname = cli.settings.envars[winsrv_synode]
    web_srvname = cli.settings.envars[winsrv_websrv]

    # 1. stop services
    for srvname in [syn_srvname, web_srvname]:
        try:
            stop_wsrv_byname(srvname)
        except UnexpectedExit as e:
            print(f"Error stopping {srvname}: {e}", file=sys.stderr)

    # 2. backup, preserving each file's exact relative sub-path under backup_dir
    vol = cli.settings.Volume()
    backup_dir = f'backup-{datetime.now().strftime("%Y%m%d")}'
    print(f'Backing up to: {os.path.abspath(backup_dir)}')

    backups = []  # [(orig-path), ...] -- restore just re-copies orig <- backup_dir/orig

    def stash(src):
        if os.path.isfile(src):
            dst = os.path.join(backup_dir, src)
            Utils.copy_anyway(src, dst, log=True)
            backups.append(src)
        else:
            print(f'Skipped (not found): {src}')

    stash(os.path.join(vol, dictionary_json))
    stash(os.path.join(vol, sys_db))
    stash(os.path.join(vol, syn_db))
    stash(os.path.join(web_inf, settings_json))
    stash(os.path.join(album_web_dist, web_host_json))

    # 3. unpack the update package over cwd
    print(f'Unpacking {zip_path} to {os.getcwd()} ...')
    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall('.')

    # 4. restore the backed-up files, from their mirrored sub-path back to the original
    for orig in backups:
        Utils.copy_anyway(os.path.join(backup_dir, orig), orig, log=True)
        print(f'Restored: {orig}')

    # 5. restart services
    for srvname, label in [(syn_srvname, 'jserv-album'), (web_srvname, 'html-service')]:
        try:
            restart_wsrv_byname(srvname)
        except UnexpectedExit as e:
            print(f"Error restarting {label}: {e}", file=sys.stderr)

    print(f'Update complete. Backup kept at: {os.path.abspath(backup_dir)}')
