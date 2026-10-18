# legacy-update

Upgrades a Windows synode installed by a package that predates the upgrade tool. Those packages have no
`winsrv\stop-winsrv.bat`, no `bin\resource.json` and no `upgrade.exe`, and their synode service can hang
at STOP_PENDING when stopped: before the fix in `a4b7d026`, `jvmStop()` called `System.exit()`.

The scripts stop the services (by force if needed), back up the local data and settings, then run the new
package's own `upgrade.exe`, which does the upgrade itself. They make no network connections.

## Which installations

Windows synodes of **Portfolio 0.8.0 installed by setup-gui**, from the oldest 0.8.0 builds on: those
whose `jserv-album-0.8.0.jar` doesn't stop as a service and whose package has no `bin\resource.json` (the
earliest of them have no upgrade tool either; later ones have an `upgrade.exe` that can't stop the
synode service). 0.7.x installations are not supported.

- **Versions:** the new package's `bin\resource.json` `upgrade_from` decides. The 0.8.0 package accepts
  0.8.0 and later (`jserv-album/tasks.0.8.0.json`: `"upgrade_from": {"min": "0.8.0"}`); an older
  installation is refused before anything is stopped or changed.
- **Installer:** setup-gui saves the services' names in `WEB-INF\settings.json` (`envars`:
  `winsrv.synode`, `winsrv.web`) and an absolute `volume` path, which the upgrade needs.
- **Not handled:** services installed by hand, or by the 0.8.0 setup-cli builds that didn't install
  Windows services (no names saved). The script stops and says so; add the names to `envars` first (see
  `sc query state= all | findstr Synode`).

## How to upgrade

1. Download the new package zip, e.g. `synode-0.8.0-x64_windows.zip`.
2. Copy this folder, `legacy-update\`, into the installation folder (the folder with `WEB-INF\`).
3. In the installation folder, run:

       legacy-update\update.bat <path to the new package zip>

   or drag the zip onto `update.bat`. Windows asks once for administrator permission; the upgrade then
   continues in a new window.
4. Follow the prompts:
   - **Close the desktop app** (`album_gui`) if it is running.
   - **Disconnect the network** (unplug the cable, turn off Wi-Fi) when asked, and keep it off until the
     upgrade is done. A synode stopped in the middle of a synchronization or a registry update can leave
     doc-refs unresolved; the upgrade itself needs no network. The script checks for connected networks
     and asks again if one is still connected.
   - **Check the backup notice**, then answer `y` to upgrade.
5. When it says `Upgraded.`, connect the network again.

What `update.bat` does, in order:

1. checks the package accepts the installed version (nothing is changed if not);
2. asks to disconnect the network;
3. takes `upgrade.pkg` out of the zip as `upgrade.exe` (the newest upgrade tool);
4. stops the 2 services; a service still not stopped after 40 s has its process killed;
5. backs up to a new folder, `pre-upgrade-<yyyyMMdd-HHmmss>\` (below);
6. writes `bin\resource.json` of the installed version if there is none, so that `upgrade.exe` knows it
   (otherwise it would take its own version for the installed one, and an older installation would keep
   running its old jar);
7. asks to go on (no: the services are started again, nothing else is changed);
8. runs `upgrade.exe <zip>`: it backs up again, unpacks the zip, copies the local files back, and restarts
   the services, reinstalled first with new names if the versions changed.

A log of every run is appended to `synode-services.log` in the installation folder.

## The backup

Made while the services are stopped, into a new `pre-upgrade-<yyyyMMdd-HHmmss>\` folder in the installation
folder, never written again:

| Backed up | From |
|---|---|
| `vol\dictionary.json`, `vol\jserv-main.db`, `vol\doc-jserv.db`, `vol\syntity.json` | the volume (`settings.json` `volume`) |
| `WEB-INF\` (all) | the installation folder |
| `web-dist\private\` (all) | the installation folder |
| `bin\resource.json`, if any | the installation folder |

The documents in the volume are not copied: neither this nor the upgrade changes them (the package has no
volume). Back up the whole volume yourself if you want a full copy.

**Why its own backup:** `upgrade.exe` backs up into `backup-<yyyyMMdd>\`, and a second run on the same day
writes into the same folder. After an aborted or failed upgrade, a retry would overwrite that backup with the
files as the failed run left them. `pre-upgrade-*\` is the safe copy: keep it until the synode works after
the upgrade.

**To restore:** `legacy-update\stop-services.bat`, then copy

- `pre-upgrade-...\vol\*` into the volume folder;
- `pre-upgrade-...\WEB-INF`, `web-dist`, `bin` into the installation folder;

then `legacy-update\start-services.bat`. If the upgrade renamed the services (new versions), restoring the
files doesn't restore the old services.

## Other commands

Run in the installation folder:

| Command | Does |
|---|---|
| `legacy-update\stop-services.bat` | stops the 2 services, killing a hanging service process |
| `legacy-update\start-services.bat` | starts them |
| `legacy-update\backup-services.bat` | stops them, backs up (as above), starts them again |
| `powershell -ExecutionPolicy Bypass -File legacy-update\synode-services.ps1` | shows the services' states and processes |

`synode-services.ps1` does the work; the `.bat` files only start it. Its options: `-Action status|stop|start|backup|upgrade`,
`-Package <zip>`, `-Root <installation folder>`, `-Wait <seconds>`, `-KeepTool` (run the installed
`upgrade.exe` instead of the package's), `-Yes` (no questions).

## If something goes wrong

| Message | What to do |
|---|---|
| `The installed version ... is older than ...` | The package doesn't upgrade this version. Nothing is changed. |
| `No service names in ... settings.json envars` | Add `"winsrv.synode"` and `"winsrv.web"` to `envars` (names from `sc query state= all \| findstr Synode`). |
| `settings.json volume is not an absolute path` | Set `volume` to the full path of the volume folder, e.g. `D:/portfolio/vol`. |
| `... can't be stopped` | End the service process in Task Manager (or `taskkill /F /PID <pid>`, pid from the status command), then run again. |
| `The upgrade did not complete` | Read the output above it. Keep the `pre-upgrade-*\` folder named in the message before you retry. The services may be stopped: `start-services.bat`. |

Requires Windows PowerShell 5.1 or newer (built into Windows 10 and 11).
