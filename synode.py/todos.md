- Restart synode service after Windows is updated

  ISSUE 2026-03-02

  The service is registered by Procrun.exe as \SOFTWARE\WOW6432Node\Apache Software Foundation\Procrun 2.0\...,
  rather than HKLM\SYSTEM\CurrentControlSet\Services\Synode-7.10-service-id.
  That makes the services lost after Windows updated.
  Brutally re-install and start the service solved problem, and files are synchronized.

  TODO: source review for re-installation is allowed.

- IPC-Agent installation vs. Standalone Download

  The standalone version doesn't has synode_id & synode_vol in the settings.json.

- IPC-Agent configuration

- Pswd & jserv setup

- Register device