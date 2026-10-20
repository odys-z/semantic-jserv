"""
The upgrade, update_srv() (synode-upgrade-srv / upgrade.exe), for both Windows services (winsrv.py)
and Linux systemd units (linunits.py).
"""
import filecmp
import os
import re
import signal
import subprocess
import sys
import time
import zipfile
from datetime import datetime
from typing import cast, Optional

from anson.io.odysz.common import Utils, LangExt
from invoke import task
from semanticshare.io.oz.jserv.docs.syn.singleton import sys_db, syn_db

from .__version__ import jar_ver

from .installer_api import promote_upgrade_whl, load_resources, running_resources, read_package_res, \
    resource_json, InstallerCli, dictionary_json, settings_json, web_inf, album_web_dist, web_host_json, \
    html_service_json, desktop_exe, desktop_running
from .linunits import Sudo, stop_sysunits, restart_sysunits, apply_unit_resources
from .winsrv import stop_winsrvs, restart_wsrvs, apply_winsrv_resources


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



def installed_settings_error(cli: InstallerCli) -> Optional[str]:
    """
    :return: why WEB-INF/settings.json is not an installed synode's (see update_srv() step 0), or None
    """
    st = cli.settings
    errs = []
    if not st.volume or not os.path.isabs(st.volume):
        errs.append(f'volume is not an absolute path: {st.volume}')
    if not LangExt.isblank(st.installkey) or LangExt.isblank(st.rootkey):
        errs.append('installkey is set or rootkey is empty, the synode has never been set up with it')
    return f'{web_inf}/{settings_json} is not an installed synode\'s: {"; ".join(errs)}.' if errs else None


def update_srv(pkg_path: str):
    """
    Update a running Synode install in-place, in the installation folder (cwd):
      0. check WEB-INF/settings.json is an installed synode's: absolute volume, installkey cleared and
         rootkey set (installed_settings_error()), not e.g. a package template unpacked over it;
         check the new package accepts the installed version: its bin/resource.json upgrade_from
         {min, max}, and no downgrade (PkgResources.refuses()); read out of the package, before
         anything is stopped or changed, so a refused upgrade changes nothing;
         Windows: wait until the desktop app (desktop/album_gui.exe) is closed, asking the user
         (installer_api.desktop_running())
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
         Linux: replace the changed jar paths in the unit files (linunits.apply_unit_resources());
         Windows: if the versions changed, reinstall the services with the new names and jars, saved
         in settings.envars (winsrv.apply_winsrv_resources())
      6. Linux: install the new package's bin/synode_py3-*.whl, the upgrade tool itself, so that the
         next upgrade runs the new version (installer_api.promote_upgrade_whl()). Windows: the new
         upgrade.exe is unpacked as upgrade.pkg, beside the running upgrade.exe, and moved by setup-*.
      No step asks the user. Linux: sudo asks for a password only if it needs one; Windows: installing
      services needs the administrator, asked once (UAC) unless upgrade.exe runs as one.

    :param pkg_path: path to the update package, zip on Windows, tar.gz on Linux.
    """
    if not os.path.isfile(pkg_path):
        print(f'Error: update package not found: {pkg_path}')
        return

    cli = InstallerCli()
    cli.load_settings()

    # 0. is WEB-INF/settings.json an installed synode's? The installer saves the volume as an absolute
    #    path, and the synode's first start clears installkey and sets rootkey (AppSettings.save_rt()).
    #    A relative volume is resolved by the synode against WEB-INF but by Volume() against cwd, so the
    #    backup would miss the synode's files; and an installkey would make the synode set up the dbs
    #    again. Such a settings.json is likely a package's template unpacked over the installed one.
    why = installed_settings_error(cli)
    if why:
        print(f'Error: {why}\n  The installed one may be found in {web_inf}/{settings_json}-fingerprint, '
              f'written by the synode at its first start, or in a backup-*/ folder.\n'
              f'Upgrade aborted, nothing is changed.', file=sys.stderr)
        return

    # 0. does the new package accept the installed version?
    # PkgResources is an Anson, a dict subclass with no items: always falsy, so test None, never truthiness
    installed = load_resources()
    if installed is None:
        installed = running_resources()
    try:
        pkg_res = read_package_res(pkg_path)
    except Exception as e:
        print(f'Error: cannot read {pkg_path}: {e}\nUpgrade aborted, nothing is changed.', file=sys.stderr)
        return
    if pkg_res is None:
        print(f'Warning: the package has no {resource_json}, its versions are not checked.', file=sys.stderr)
    else:
        why = pkg_res.refuses(installed.jar_ver())
        if why:
            print(f'Upgrade refused: {why}.\nNothing is changed.', file=sys.stderr)
            return
        print(f'Upgrading {installed.jar_ver()} -> {pkg_res.jar_ver()}')

    # 0. the desktop app must be closed: Windows can't replace a running desktop/album_gui.exe, and the
    #    unpack would stop half way, leaving a mix of the old and new packages
    while desktop_running():
        print(f'The desktop app is running: {desktop_exe()}\n'
              f'Please close it, then press Enter to check again (q to quit) ...')
        try:
            if input().strip().lower() == 'q':
                raise EOFError
        except EOFError:
            print('Upgrade aborted, nothing is changed.', file=sys.stderr)
            return

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
        try:
            with zipfile.ZipFile(pkg_path, 'r') as zf:
                zf.extractall('.')
        except OSError as e:
            # e.g. a file locked by a still running service; restore the backups below as rollback
            unpacked = False
            print(f'Error unpacking {pkg_path}:\n{e}', file=sys.stderr)
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
    if new_res is not None:
        # also the closed tests' mark that the upgrade tool applies the manifests (.github/closed-test)
        print(f'Applying {resource_json}: ' + ', '.join(
            f'{k} {old_res.versions.get(k)} -> {new_res.versions.get(k)}' for k in ('jar_ver', 'web_ver', 'html_srver')))
    applied = True
    if Utils.iswindows():
        if new_res is not None:
            applied = apply_winsrv_resources(cli, old_res, new_res)
        else:
            applied = restart_wsrvs()
    else:
        if new_res is not None:
            applied = apply_unit_resources(cast(Sudo, sudo), units, old_res, new_res)
        for u, st in restart_sysunits(cast(Sudo, sudo), units, ask=False).items():
            print(f'{u}: {st}')

    # 6. the upgrade tool itself (Linux), last: the running process keeps the modules it has loaded
    if unpacked and not Utils.iswindows():
        promote_upgrade_whl()

    ok = unpacked and restored and applied
    print(f'Update {"complete" if ok else "FAILED"}. Backup kept at: {os.path.abspath(backup_dir)}')
