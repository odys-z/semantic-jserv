@echo off
rem Back up the synode's local data and settings (the volume's dbs, dictionary.json, syntity.json, WEB-INF\,
rem web-dist\private\, bin\resource.json) to a new pre-upgrade-<time>\ folder: stops the 2 services for a
rem consistent copy, then starts them again. In the installation folder (where WEB-INF\ is): legacy-update\backup-services.bat.
set "root=%~dp0..\"
if not exist "%root%WEB-INF\settings.json" set "root=%CD%\"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0synode-services.ps1" -Action backup -Root "%root%."
