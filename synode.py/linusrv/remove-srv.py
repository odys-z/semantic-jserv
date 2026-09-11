#!/usr/bin/env python3
import os
import sys
import subprocess
from typing import List, Tuple

from antson.py3.src.anson.io.odysz import common

def run_command(cmd, ignore_fail=False):
    """Helper to run system commands."""
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as e:
        if not ignore_fail:
            print(f"⚠️  Warning while running: {' '.join(cmd)}")
            print(f"   {e.stderr.decode().strip()}")


def check_arg() -> Tuple[str, List[str]]:
    '''
    Enforce command line argument
    :return: service-id (synnode-id), [service-name, web.service-name]
    '''
    if len(sys.argv) < 2:
        print("❌ Missing argument!")
        print(f"👉 Usage: {sys.argv[0]} <service_prefix>")
        print(f"   Example: {sys.argv[0]} ody-6-hub")
        sys.exit(1)

    service_prefix = sys.argv[1]
    service_names = [f"{service_prefix}.service", f"{service_prefix}.web.service"]
    return service_prefix, service_names 


def check_linux_root() -> None:
    if os.geteuid() != 0:
        print("⚠️  Root privileges required. Attempting to elevate using sudo...")
        try:
            os.execvp("sudo", ["sudo", sys.executable] + sys.argv)
        except Exception as e:
            print(f"❌ Failed to elevate privileges: {e}")
            sys.exit(1)


def main():
    systemd_dir = "/etc/systemd/system"
    service_prefix, [srname, websrname] = check_arg()
    check_linux_root()

    print(f"🗑️  Starting precise removal for prefix: '{service_prefix}'")
    for service in [srname, websrname]:
        print(f"🛑 Stopping and disabling {service}...")
        run_command(["systemctl", "stop", service], ignore_fail=True)
        run_command(["systemctl", "disable", service], ignore_fail=True)

        common.rm_any([
            os.path.join(systemd_dir, service),                                      # Main service definition
            os.path.join(systemd_dir, "multi-user.target.wants", service),           # Standard multi-user target link
            os.path.join(systemd_dir, "graphical.target.wants", service),            # Alternative GUI target link
        ], verbose=True)

    print("🔄 Resetting systemd manager state...")
    run_command(["systemctl", "daemon-reload"])
    run_command(["systemctl", "reset-failed"])

    print("✅ Precise removal complete! Tab-completion cache cleared safely.")

if __name__ == "__main__":
    main()
