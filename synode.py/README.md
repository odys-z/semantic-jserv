# About

Synode.py is the synchronization node for Portfolio, a file / resources synchronization framework.


# Python Version Selection

    2026-09-05

  Minimum Supported OS Matrix for Python 3.9 ~ 3.12.9 & Pyside6

  ```
    Operating System    Supported?     Reason
    Windows 11          Yes            Fully supported by PySide6, Python 3.9/3.12, and PyInstaller.
    Windows 10          Yes            Fully supported by PySide6, Python 3.9/3.12, and PyInstaller.
    Windows 8 / 8.1     No             Blocked by PySide6 / Qt 6 runtime dependencies.
    Windows 7           No             Blocked by PySide6, Python 3.9+, and PyInstaller.  
  ```

  To support Windows 8, needs PySide2 (Qt 5.15) and Python 3.8.

  **Decision**:
  Python 3.12.9 for Setup-gui.exe & Snode.py3.
  
  ```
    The anson.py3 and semantics.py3 will keep on Python 3.9.1
  ```

- Key PySide6 Release Series for Python 3.12

  ```
    PySide 6.11.x (Latest Stable Series):
    6.11.2 (Latest release)
    6.11.1, 6.11.0

    PySide 6.10.x & 6.9.x Series:
    Minor feature and stability releases tracking Qt 6.10 / Qt 6.9 updates.

    PySide 6.8.x Series (Recommended Long-Term Stable):
    6.8.2.1, 6.8.2, 6.8.1, 6.8.0

    PySide 6.7.x Series:
    6.7.3, 6.7.2, 6.7.1, 6.7.0

    PySide 6.6.x Series (First Official Release with Native Python 3.12 Support):
    6.6.3, 6.6.2, 6.6.1, 6.6.0
  ```

  There is an issue in 6.8.2.2 (not repeatable), and only 6.6.0 - 6.8.2.1 are suppoted.

#### FIY

  To install multiple Python, clean and without interfering system paths, etc., 

  ```
    wget https://www.python.org/ftp/python/3.12.9/Python-3.12.9.tgz
    tar -xf Python-3.12.9.tgz
    cd Python-3.12.9

    # install to /opt/python3.12.9
    ./configure --prefix=/opt/python3.12.9 --enable-optimizations

    make -j$(nproc)
    sudo make altinstall

    # For the porject, cd here
    /opt/python3.12.9/bin/python3.12.9 -m venv .ven3.12.9
  ```

# Service Scripts for Linux Versions

The systemctl command and these scripts will work exclusively on Linux distributions that use systemd as their default init system.
Here is a breakdown of where it works and where it won't:
## 🐧 Fully Supported Linux Distributions
Systemd is standard on almost all major modern Linux distributions:

* Ubuntu (version 15.04 and newer)
* Debian (version 8 / Jessie and newer)
* Red Hat Enterprise Linux (RHEL) / CentOS / Rocky Linux / AlmaLinux (version 7 and newer)
* Fedora (all recent versions)
* Arch Linux and Manjaro
* openSUSE and SUSE Linux Enterprise

## ❌ Where it will NOT work
These scripts will fail on operating systems or environments that do not use systemd:

* macOS: Uses launchd (managed via launchctl), not systemd.
* Windows: Uses the Windows Services Manager (managed via sc or PowerShell commands like Start-Service), not systemd.
* Lightweight / Specialized Linux: Distros like Alpine Linux (which uses OpenRC) or Void Linux (which uses runit) do not have systemctl.
* Standard Docker Containers: Standard, minimal Docker containers typically do not run systemd inside them. If you run these scripts inside a stock Ubuntu or Debian container, systemctl will fail with an error like “System has not been booted with systemd as init system”.
* WSL 1 / Older WSL 2: Windows Subsystem for Linux disables systemd by default, though newer versions of WSL 2 allow you to enable it explicitly in /etc/wsl.conf.
