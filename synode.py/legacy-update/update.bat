@echo off
rem Upgrade a synode installed before the upgrade tool: stop its services (by force if they hang), back up
rem the local data and settings to a new pre-upgrade-<time>\ folder and ask to go on,
rem then run the new package's upgrade.exe with the package. See README.md.
rem In the installation folder (where WEB-INF\ is): legacy-update\update.bat; or drag the new package
rem zip onto this file: legacy-update\update.bat <package.zip>
if "%~1"=="" (
  echo Upgrades a Portfolio 0.8.0 synode installed by setup-gui before the upgrade tool could stop it.
  echo.
  echo Usage, in the installation folder ^(where WEB-INF\ is^):
  echo     legacy-update\update.bat ^<new package zip^>
  echo or drag the zip onto this file.
  echo.
  echo It checks the package accepts the installed version, asks you to disconnect the network,
  echo stops the services ^(by force if they hang^), backs up to a new pre-upgrade-^<time^>\ folder,
  echo asks to go on, then runs the package's upgrade.exe. See legacy-update\README.md.
  pause
  exit /b 1
)
set "root=%~dp0..\"
if not exist "%root%WEB-INF\settings.json" set "root=%CD%\"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0synode-services.ps1" -Action upgrade -Package "%~f1" -Root "%root%."
