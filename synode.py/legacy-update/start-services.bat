@echo off
rem Start the synode's 2 Windows services again (e.g. after stop-services.bat, without upgrading).
rem In the installation folder (where WEB-INF\ is): legacy-update\start-services.bat.
set "root=%~dp0..\"
if not exist "%root%WEB-INF\settings.json" set "root=%CD%\"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0synode-services.ps1" -Action start -Root "%root%."
