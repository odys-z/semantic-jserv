# ISSUE central-uid: Synode logs in central as the domain admin, not centralUid

- Found: 2026-10-05, Portfolio 0.8.0 (`portfolio-0.8`)
- Status: open
- Tags: `ISSUE central-uid` in
  - `docsync.jserv/src/main/java/io/oz/jserv/docs/syn/singleton/AppSettings.java`, `merge_ip_json2db()`
  - `jserv-album/tasks.py`, `validate()`
  - `synode.py/tasks.py`, `config()`
  - `synode.py/src/synodepy3/prompt.py`, the `exSession` retry when querying domains

## Problem

Since semantics.py3 0.6.11 the task config has `deploy.centralUid` for the central (registry) account,
next to `deploy.central_pswd`, the same as `io.odysz.jclient.AnclientSettings.centralUid` used by clients.

But the Synode side doesn't use it. `AppSettings.merge_ip_json2db()` logs in central with

```java
registryClient = SessionClient.loginWithUri(
        regiserv, reg_uri + "/" + c.synid, synusr.uid(), centralPswd, c.synid);
```

where `synusr` is `Syngleton.synuser` (via `ExpSynodetier.jserv_worker()`), i.e. the domain admin,
`deploy.admin` in the task config, written into `registry/dictionary.json` synusers by synode.py `tasks.py config`.
`settings.json` (`AppSettings`) has `centralPswd` but no `centralUid` field.

So the central account is `(deploy.admin, deploy.central_pswd)` for Synodes, and
`(deploy.centralUid, deploy.central_pswd)` for desktop / clients.

## Consequence

If `deploy.centralUid != deploy.admin`, desktops can log in central while the Synodes fail to
log in central, so they cannot submit jservs (`synotifyCentral()`) and peers can't find them.
It works today only because the configs use `admin` for both.

## Constraint (0.8.0)

`synusr` is `YellowPages.robots().get(0)` (`SynotierJettyApp`, commented `// or get 'admin'?`), i.e.
`registry/dictionary.json` synusers[0], written by synode.py `tasks.py config()` as
`SyncUser(userId=deploy.admin, pswd=deploy.domain_token)`; the installer only changes its pswd.

The same uid is the domain admin (with `domain_token`) and the central account (with `central_pswd`).
With central's account being `admin`, **`deploy.admin` and `deploy.centralUid` must both be `admin`**.

## Build-time guard

Both stop the build (`sys.exit(-1)`) unless `deploy.admin == deploy.centralUid == 'admin'`:

- `jserv-album/tasks.py validate()`
- `synode.py/tasks.py config()`, before `registry/dictionary.json` is written

## Installer (synode-cli)

When central answers `exSession`, synode-cli asks for the central password and saves it to
`settings.json` (`centralPswd`). The central user id (`InstallerCli.central_uid()`) is not asked for,
nor saved: if it isn't `admin`, synode-cli reports this issue and quits, since a password can't fix it.

## Possible fix (not implemented)

1. Add `centralUid` to `AppSettings` (Java docsync.jserv and Python semantics.py3).
   Java Anson throws `Field not found` on unknown fields, so the Java field must be released before
   any `settings.json` carries it.
2. `jserv-album/tasks.py config` writes `synode_settings.centralUid = taskcfg.deploy.centralUid`;
   synode.py installer keeps it.
3. `merge_ip_json2db()` logs in with `centralUid`, falling back to `synusr.uid()` if blank,
   for `settings.json` of 0.7.x / 0.8.0.
4. Revisit `YellowPages.robots().get(0)` in `SynotierJettyApp` (`// or get 'admin'?`).
5. Relax the guards to `deploy.admin` being a domain admin only.
