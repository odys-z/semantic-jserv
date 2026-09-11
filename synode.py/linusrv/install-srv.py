#!/usr/bin/env python3
import os
import sys
import shutil
import subprocess

# Configuration
SERVICE_PREFIX = "ody-6-hub"
SYSTEMD_DIR = "/etc/systemd/system"

# Mapping: Local filename -> Systemd service filename
SERVICES = {
    "hub.service": f"{SERVICE_PREFIX}.service",
    "hub.web.service": f"{SERVICE_PREFIX}.web.service"
}

def run_command(cmd):
    """Helper to run system commands and handle errors."""
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as e:
        print(f"❌ Error executing: {' '.join(cmd)}")
        print(f"   Stderr: {e.stderr.decode().strip()}")
        sys.exit(1)

def main():
    if os.geteuid() != 0:
        print("❌ This script must be run as root (sudo).")
        sys.exit(1)

    print(f"🚀 Starting installation for prefix: '{SERVICE_PREFIX}'")

    for local_file, systemd_name in SERVICES.items():
        if not os.path.exists(local_file):
            print(f"❌ Local file '{local_file}' not found in the current directory.")
            sys.exit(1)

        dest_path = os.path.join(SYSTEMD_DIR, systemd_name)
        print(f"📦 Copying {local_file} -> {dest_path}")
        
        # Copy file and enforce proper root-only write permissions (644)
        shutil.copy2(local_file, dest_path)
        os.chmod(dest_path, 0o644)
        shutil.chown(dest_path, user="root", group="root")

    print("🔄 Reloading systemd daemon...")
    run_command(["systemctl", "daemon-reload"])

    for systemd_name in SERVICES.values():
        print(f"⚙️  Enabling and starting {systemd_name}...")
        run_command(["systemctl", "enable", systemd_name])
        run_command(["systemctl", "start", systemd_name])

    print("✅ Installation complete! Checking service statuses:")
    for systemd_name in SERVICES.values():
        status = subprocess.run(["systemctl", "is-active", systemd_name], stdout=subprocess.PIPE, text=True)
        print(f"   - {systemd_name}: {status.stdout.strip()}")

if __name__ == "__main__":
    main()
