# This Python file uses the following encoding: utf-8
"""
Prerequisites of Synode installers: exiftool, and for the GUI (setup-gui), Qt (PySide6) system libraries.

Keep this module light: setup-gui calls check_prerequisites(gui=True) before anything imports PySide6.
"""
import os
import re
import shutil
import subprocess
import sys
import zipfile
from glob import glob
from typing import Dict, Final, Optional

from anson.io.odysz.common import Utils
from semanticshare.io.oz.jserv.docs.syn.singleton import PortfolioException

exiftool_zip = 'exiftool.zip'
exiftool_v_exe = 'exiftool*.exe'
exiftool_exe = 'exiftool.exe'
exiftool_testver = 'exiftool -ver'

qt_runtime_debs: Final[list[str]] = ['libegl1', 'libgl1', 'libxkbcommon0', 'libfontconfig1', 'libdbus-1-3']
'''
Debian / Ubuntu packages of the system libraries Qt (PySide6) loads, e.g. libEGL.so.1.
pip can't install them: a PySide6 wheel bundles Qt, but links the platform's EGL, GL, xkb, fontconfig and dbus.
'''


def decode(warns: bytes):
    lines = []
    if warns is not None:
        for l in warns.split(b'\n'):
            if isinstance(l, bytes):
                try:
                    s = l.decode('utf-8')
                except UnicodeDecodeError:
                    try:
                        s = l.decode('gbk')
                    except UnicodeDecodeError:
                        s = ''.join(str(l))
                if s is not None:
                    lines.extend(s.split('\n'))
            else:
                lines.append(str(l))
    return lines


def unzip_file(zip_filepath, extract_to_path):
    """
    Unzips a file to a specified directory.

    Args:
        zip_filepath (str): The path to the zip file.
        extract_to_path (str): The directory to extract the contents to.
    """
    try:
        with zipfile.ZipFile(zip_filepath, 'r') as zip_ref:
            zip_ref.extractall(extract_to_path)
        print(f"Successfully extracted '{zip_filepath}' to '{extract_to_path}'")
    except FileNotFoundError:
        print(f"Error: Zip file '{zip_filepath}' not found.")
    except zipfile.BadZipFile:
        print(f"Error: '{zip_filepath}' is not a valid zip file.")
    except Exception as e:
        print(f"An error occurred: {e}")


def get_zipath(zipath):
    if os.path.isfile(zipath):
        with zipfile.ZipFile(zipath, 'r') as zip_ref:
            for subf in zip_ref.infolist():
                if not subf.is_dir():
                    raise PortfolioException('Not a recognized zip file structure in: {zippath}')
                return subf.filename
    return None


def install_exiftool_win():
    """
    If in Windows, unzip exiftool-13.21_64 and move to root path
    :return: None
    """

    zips = glob(os.path.join('bin', exiftool_zip))
    if len(zips) > 0:
        xtract_files = glob(exiftool_v_exe)
        if len(xtract_files) == 0:
            subfolder = get_zipath(zips[-1])
            unzip_file(zips[-1], '.')
            for res in glob(os.path.join(subfolder or '', '*')):
                print(res)
                if re.match(f'{subfolder.removesuffix("/")}.exiftool.*', res):
                    shutil.move(res, '.')

            try: os.remove(exiftool_exe)
            except FileNotFoundError: pass

            xtract_files = glob(exiftool_v_exe)
            os.rename(xtract_files[0], exiftool_exe)


def check_exiftool():
    if Utils.iswindows():
        return os.path.isfile(exiftool_exe)
    else:
        print('exiftool -ver')
        try:
            p = subprocess.Popen(['exiftool', '-ver'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if p is None:
                return False
            out, err = p.communicate()
            if len(err) > 0:
                print(err, file=sys.stderr)
                return False

            print(decode(out), 'exiftool is working...')
            return True
        except Exception as e:
            print(e)
            return False


def checkinstall_exiftool():
    check = check_exiftool()

    if Utils.iswindows() and not check:
        install_exiftool_win()
        return True
    else:
        return check


def exiftool_missing_msg() -> str:
    return "Check and install exiftool failed!" \
        if Utils.get_os() == 'Windows' \
        else "Please install exiftool and test it's working with command 'exiftool -ver'"


def check_qt_runtime() -> Optional[str]:
    """
    Load PySide6.QtWidgets in a child process, so that a failure leaves this process clean.
    :return: None if Qt can be loaded, else the error, e.g.
             "ImportError: libEGL.so.1: cannot open shared object file: No such file or directory"
    """
    if Utils.iswindows() or getattr(sys, 'frozen', False):
        return None  # the PySide6 wheel / the PyInstaller bundle carries what Qt needs

    r = subprocess.run([sys.executable, '-c', 'import PySide6.QtWidgets'], capture_output=True, text=True)
    if r.returncode == 0:
        return None
    lines = [l for l in r.stderr.splitlines() if l.strip()]
    return lines[-1] if lines else f'importing PySide6.QtWidgets failed, exit code {r.returncode}'


def confirm_tty(question: str) -> bool:
    """
    Ask a yes / no question in the terminal, before Qt is available.
    :return: True for yes (the default); False for no, or if there is no terminal to ask in
    """
    if not sys.stdin or not sys.stdin.isatty():
        return False
    return input(f'{question} [Y/n] ').strip().lower() in ('', 'y', 'yes')


def check_confirm_install_qtruntime() -> Optional[str]:
    """
    Check Qt (PySide6) can be loaded; if not, on Debian / Ubuntu, ask in the terminal, then install
    its system libraries with apt-get, as root (Sudo asks for the password if needed).
    :return: None if Qt can be loaded (now), else the error with the command to run by hand
    """
    err = check_qt_runtime()
    if err is None:
        return None

    manual = f'sudo apt-get install -y {" ".join(qt_runtime_debs)}'
    if shutil.which('apt-get') is None:
        return f'{err}\nInstall the system libraries Qt needs with the package manager, e.g. on Debian / Ubuntu:\n{manual}'

    if not confirm_tty(f'Qt (PySide6) cannot be loaded: {err}\nInstall the system libraries now?\n  {manual}\n'):
        return f'{err}\nPlease install the system libraries Qt needs:\n{manual}'

    from .linunits import Sudo  # imported here: linunits imports installer_api, which is heavy
    sudo = Sudo()
    try:
        sudo.ensure()
    except Exception as e:  # PermissionError, or no terminal to ask for the password
        return f'{err}\n{e}\nPlease install the system libraries Qt needs:\n{manual}'

    print(f'Installing {" ".join(qt_runtime_debs)} ...')
    r = sudo.run('apt-get', 'install', '-y', *qt_runtime_debs)
    if r.returncode != 0:  # e.g. no package lists yet on a fresh system
        sudo.run('apt-get', 'update')
        r = sudo.run('apt-get', 'install', '-y', *qt_runtime_debs)
    if r.returncode != 0:
        return f'{err}\napt-get failed: {r.stderr.strip()[-500:]}\nPlease install the system libraries Qt needs:\n{manual}'

    return check_qt_runtime()


def check_prerequisites(gui: bool = False) -> Optional[Dict[str, str]]:
    """
    :param gui: also check Qt (PySide6) can be loaded, and install its system libraries if confirmed.
                Call it before anything imports PySide6. synode-cli doesn't need Qt.
    :return: None if all is ready, else {prerequisite: problem}
    """
    print("Checking prerequisites")
    if gui:
        qt_err = check_confirm_install_qtruntime()
        if qt_err is not None:
            return {"Qt runtime": qt_err}

    # Windows: a fresh package has bin/exiftool.zip, unpacked into ./exiftool.exe here (as validate() does)
    if not checkinstall_exiftool():
        return {"exiftool": exiftool_missing_msg()}

    return None
