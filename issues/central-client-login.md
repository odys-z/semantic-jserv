# ISSUE central-login: Python clients of central use SessionClient.loginWithUri()

- Found: 2026-10-05, Portfolio 0.8.0 (`portfolio-0.8`)
- Status: open
- Tags: `ISSUE central-login` in
  - `synode.py/tasks.py`, `register_org()`
  - `synode.py/src/synodepy3/installer_api.py`, `check_cent_login()` and above `query_domx()`
  - `synode.py/src/synodepy3/prompt.py`, the `check_cent_login()` call

## Notes

`anclient.py3` (0.2.7) `SessionClient.loginWithUri()` builds the client locally; it makes no request to
`login.serv`, unlike Java `SessionClient.loginWithUri()`. Callers:

- `synode.py/tasks.py register_org()` – uid `deploy.admin`, pswd `deploy.central_pswd`
- `synode.py/src/synodepy3/installer_api.py check_cent_login()` – uid `central_uid()`, i.e.
  `centralUid` in the packaged `desktop/settings/app-settings.json` (from `deploy.centralUid`),
  falling back to `registry.synusers[0]` (domain admin) without a desktop; pswd `settings.centralPswd`.
  Used by `query_domx()`, `query_domconf()`, `register()`, `submit_settings()`,
  i.e. both setup-gui (`__main__.py`) and setup-cli (`prompt.py`).
  Before 2026-10-05 it used `synusers[0]` uid and `domain_token`.

Related: [central-uid-synode-login.md](central-uid-synode-login.md)
