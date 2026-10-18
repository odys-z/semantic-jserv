@echo off
rem Stop the synode's 2 Windows services, killing a service process that doesn't stop.
rem Copy the folder legacy-update\ into the installation folder (where WEB-INF\ is), then run there:
rem legacy-update\stop-services.bat (or double-click it). Administrator permission is asked once.
set "root=%~dp0..\"
if not exist "%root%WEB-INF\settings.json" set "root=%CD%\"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0synode-services.ps1" -Action stop -Root "%root%."
