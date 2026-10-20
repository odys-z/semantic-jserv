# Synode end-to-end tests (Linux, Windows)

Five workflows use these resources:

- `.github/workflows/synodes-linux-closed-test.yml`: GitHub > Actions > **synodes-linux-closed-test**
  > **Run workflow**. Sets up a central and 3 synodes, installed as systemd services by synode-cli,
  pushes a file from a test client, checks the synchronization.
- `.github/workflows/synodes-linux-upgrade-test.yml`: the Linux closed test, then upgrades every
  synode with the installed `synode-upgrade-srv` (stop the units, upgrade, point them to the new
  jars, restart them, install the new synode_py3 wheel; nothing asked), and checks again.
- `.github/workflows/synodes-windows-closed-test.yml`: the same on Windows, with the synodes
  installed as Windows services by setup-cli.exe, and the Java (Android) client pushing.
- `.github/workflows/synodes-windows-upgrade-test.yml`: the Windows closed test, then upgrades every
  synode with the package's `upgrade.exe` (stop, upgrade, restart), and checks again.
- `.github/workflows/version-revival-7days.yml`: every 6 days, or by hand. Builds the test clients
  and echoes to the central; keeps the vcpkg cache alive (see below).

Shared steps are composite actions in `.github/actions/`: `start-central` (download and start the
central), `build-github-tests` (build the test clients); for the Linux tests `linux-synodes`
(package, nodes, systemd services, central report) and `linux-push` (build the C++ test client, push
a file, check `h_photos`); for the Windows tests `windows-synodes` (package, nodes, services, central
report), `java-push` (build the Java client, push a photo, check `h_photos`) and `windows-logs`.

Each run downloads two things from this repo's releases, so nothing heavy is committed here:

- the newest `synode-*-x64_linux*.tar.gz` / `synode-*-x64_windows*.zip` in any release (or the
  release tag typed in the run form);
- the registry central, from the release `alpha-central-0.1.8` of the private repository
  `Acadynamo/Resources-release` (both can be changed in the run form).

## Layout

```
.github/closed-test/
  feed_answers.py          feeds answers/<os>/<node>.txt into synode-cli / setup-cli.exe
  closed_test.py           the workflows' checks (no sqlite3 / jq tools on Windows runners)
  windows_nodes.sh         bash functions of the Windows workflows: setup_node, upgrade_node, ...
  linux_nodes.sh           bash functions of the Linux workflows: setup_node, upgrade_node, ...
  answers/linux/           synode-cli answers, synodes-linux-{closed,upgrade}-test.yml
    hub.txt                  the hub   (jserv 8961, web 8951), creates the domain ci-0.8
    prv1.txt                 prv1      (jserv 8962, web 8952)
    prv2.txt                 prv2      (jserv 8963, web 8953)
  answers/windows/         setup-cli.exe answers, synodes-windows-closed-test.yml
    hub.txt, prv1.txt, prv2.txt
```

## The central release

All assets of the release are downloaded: `.zip`, `.tar.gz` and `.tgz` are unpacked, a loose
`.jar` is copied. Together they must give:

```
<central>.jar            the registry central, runnable with java -jar
WEB-INF/                 settings.json (port 1991, protocolRoot regist-central), xml, ...
regist-vol/              the volume, settings.json "volume": "../regist-vol"
  system.sqlite          a snapshot of a working central (no initialization in the test)
  syslog.sqlite
```

`.github/actions/start-central` runs the central in the folder holding `WEB-INF/`, as `java -jar <central>.jar settings.json`. Without `regist-vol/system.sqlite` it
initializes a new volume from `WEB-INF/*.sql` (the run shows a warning).

The repository is private, so the workflow reads it with the secret
`REGIST_CENTRAL_0_1_8_SNAPSHOT_TOKEN` of this repository: a fine-grained personal access token,
resource owner `Acadynamo`, repository `Resources-release`, permission Contents: Read-only.

### The volume snapshot

The synode package (0.8.0) registers in market `alpha`, org `closed-test`. The snapshot in
`alpha-central-0.1.8` has the domains `closed-1` and `closed-2` of that org, each with three synodes
in state `c` (create). The test doesn't use them: the hub creates the domain `ci-0.8`
(`CENTRAL_DOMAIN`), for which the central plans the synodes `ci-0.8-hub`, `ci-0.8-1`, `ci-0.8-2`;
prv1 and prv2 join it. The run checks that:

- the release doesn't have `ci-0.8` yet (otherwise its creation isn't tested);
- each node installed as the expected domain and synode. The answers pick menu options by
  number, so if the central lists domains or synodes in another order, the run stops with the
  mismatched ids. Today the central lists domains by id (`ci-0.8` before `closed-1`) and a
  domain's hub before its other synodes;
- the synodes of `ci-0.8` end in state `h` (hub) and `p` (peers);
- the synodes of the org's other domains are unchanged.

A new domain's id is at most 8 chars: the central's synode ids are 12 chars at most, and it
appends `-hub` (synodepy3 `validators.domain_maxlen`).

Every run starts from the released snapshot, so runs don't affect each other.

The package logs in to Central as `admin` with `centralPswd` from its `WEB-INF/settings.json`
(0.8.0: `temp-0.8.0`). 

## The desktop push

The test client is `odys-z/Anclient` `examples/example.slint/github-tests` (branch from the run
form, default `portfolio-0.8`): the desktop's ws client and IPC agent tests without Slint, webview
or SurrealismUI. Its targets:

- `revial_tests`: echo to the central (`CENTRAL_URL`), used by version-revival-7days;
- `doclient_tests`: `../tests/test_01_wsclient_ipcagent.cpp`, used by the closed test.

`.github/actions/build-github-tests` builds it with CMake 3.30+, Ninja and g++-14 (C++20
`std::chrono::parse` needs GCC 14 or newer), vcpkg at commit `7e43e0768a`, triplet `x64-linux`.

The IPC agent jar and the settings come from the `desktop-*.zip` of the same release as the synode
package; the settings are pointed at the hub (`synode_jserv`, `synode_id` `ci-0.8-hub`, device
`ci-desktop`). `Ipclient.E2E_PushSync` pushes `res/182x121.png` with a unique name, waits for it in
the hub's and prv1's volumes, then the workflow checks `h_photos` of hub, prv1 and prv2.

## The vcpkg cache

vcpkg's compiled packages are cached under a fixed key: runner image, vcpkg commit and a hash of
the package list (`vcpkg-ubuntu24-x64-linux-7e43e0768a-<hash>`). GitHub evicts a cache entry that
no run has restored for 7 days. version-revival-7days restores it every 6 days.

## Windows

- setup-cli.exe reads the console, not stdin (prompt_toolkit opens `CONIN$`), so
  `feed_answers.py --console` runs it in a pseudo console (package pywinpty) and types the answers
  one at a time, each after the output has been quiet for `--settle` seconds (default 3). Text and
  its Enter are sent apart: read together, prompt_toolkit on Windows takes them as a paste.
- setup-cli.exe installs the synode and web services (synodepy3 `winsrv.install_winsrvs()`). The
  runner runs elevated, so there is no UAC request; the workflow stops first if it isn't. The
  workflow then checks the services named in `settings.json` (`envars` `winsrv.synode`,
  `winsrv.web`) and waits for the jserv ports.
- The Java client is built from source: `semantic.jserv` and `jserv-album-lib` of the workflow's
  branch, `java/eclipse-workspace/anclient.jserv` of Anclient (`anclient_ref`, default
  `portfolio-0.8`). albumtier takes version ranges, so the run warns if it resolved another version
  (a newer one in Maven Central). Then `E2EPushTest` (albumtier `src/test`) logs in to the hub as
  `admin` with the domain token, as the Android app does (`AlbumContext`), pushes a copy of
  `64x48.png` named `64x48_e2e_<time>_<nonce>.png`, and the workflow waits for it in `h_photos` of
  hub, prv1 and prv2.

## Windows upgrade

`synodes-windows-upgrade-test` installs the package of `release_tag` and pushes photo 1, then
downloads the package of `upgrade_tag` (empty: the newest; it may be the installed one, which tests
a re-deployment) and, node by node, runs in the installation folder

```
upgrade.exe <upgrade package zip>
```

## Linux upgrade

`synodes-linux-upgrade-test` is the same on Linux. The closed test's synode-cli installs each node's
2 systemd services (`<synid>.service`, `<synid>.web.service`, names saved in `settings.json`,
envars `linusrv.synode` / `linusrv.web`), so the upgrade has units to stop and restart. In each
installation folder, the installed tool (the synode_py3 wheel of the installed package) runs, with
stdin from `/dev/null`, so any prompt fails the upgrade:

```
synode-upgrade-srv <upgrade package tar.gz>
```

## Upgrade: what is checked

`commands.update_srv()` first reads the new package's `bin/resource.json` out of the package and
checks it accepts the installed version (`upgrade_from` {min, max}, no downgrade); if not, it stops
there with "Upgrade refused", having changed nothing. It also refuses, before anything is changed,
a `WEB-INF/settings.json` that is not an installed one (a relative `volume`, or `installkey` still set).
Then it stops the 2 services, backs up the node's data and settings
(`<volume>/dictionary.json`, `jserv-main.db`, `doc-jserv.db`, `WEB-INF/settings.json`,
`WEB-INF/html-service.json`, `web-dist/private/host.json`) into `backup-YYYYMMDD/`, unpacks the
package, copies the backups back over the package's files and verifies each copy ("Restored: ..."),
then points the services to the new package's files and restarts them. Nothing is asked.

The package's manifest `bin/resource.json` (semanticshare `PkgResources`, written by
`jserv-album/tasks.py` `package()` from `SynodeTask.pkg_res()`) names its files, versions and the
installed versions it accepts (`{"files": {"synode": "bin/jserv-album-<v>.jar", ...}, "versions":
{...}, "upgrade_from": {"min": "0.8.0"}}`, `upgrade_from` from `tasks.*.json`). It holds nothing of
the build configuration (no passwords, hosts or local paths).
The installed one is kept in the backup as a reference (not copied back); comparing it with the new
one, the upgrade replaces the changed jar paths in the Linux unit files, or, on Windows, if the
versions changed, reinstalls the services with the new names (`Synode-<jar_ver>-<synid>`, saved in
`settings.json` envars) and jars (`winsrv/*.bat`).

The checks: first, a copy of the upgrade package with `upgrade_from` min 99.0.0
(`closed_test.py repack`) is refused on the hub, with no backup made and the services still up
(skipped when the installed package has no `bin/resource.json`, as its tool doesn't check). Then,
after the upgrade, "Update complete" and every backed-up file "Restored" in the upgrade's log
(`closed_test.py upgraded`; the files are not compared afterwards, as the restarted jserv rewrites
`settings.json`), the node is the same domain / synode, the services run the jars of the new
`bin/resource.json` (`closed_test.py resources-applied`), they are up, and on Linux the installed
synode_py3 is the package's.

### Build marks

Two marks in each run's summary say the package was built from the code these tests expect:

- `### Build mark: winsrv bats stamped at build by jserv-album/tasks.py config (...)` (Windows, after
  extracting the package; `closed_test.py packed-bats`). `tasks.py config` stamps the jar versions into
  `synode.py/winsrv/install-*.bat`, whose BEGIN line carries that text; installing no longer stamps them.
  A package built before has no mark and fails here; one built without `inv config` for its version
  fails on the versions differing from its `bin/resource.json`.
- `### <node> upgrade mark: Applying bin/resource.json: jar_ver <old> -> <new>, ...` (both OSes, after
  each upgrade; `upgrade_mark` in `windows_nodes.sh` / `linux_nodes.sh`), printed by the installed
  upgrade tool (synodepy3 `commands.update_srv()`). Tools built before it never applied the manifests
  (`PkgResources`, a `dict` subclass with no items, tested falsy) and fail here.

## Answer files

Linux and Windows have their own answer files: the prompts are the same up to the install, then
synode-cli on Linux installs the systemd services (name, start now?), while setup-cli.exe on
Windows installs the Windows services. Keep the two in step when prompts change.

One answer per line, in the order of the prompts (see `feed_answers.py`):

| Line | Meaning |
|---|---|
| `1` | menu option number, 1-based |
| `<enter>` | accept the prompt's default |
| `<clear>8951:8961` | replace the pre-filled default |
| `<key>y` | one key, no Enter: a prompt_toolkit `confirm()` (y/n) takes the key itself, and an Enter after it would answer the next prompt |
| `${CENTRAL_URL}` | from the workflow `env` |
| `# ...` | comment, whole line or trailing |

Check a file without running anything:

```
python feed_answers.py answers/linux/prv1.txt --dry-run
```

## Secrets

Settings > Secrets and variables > Actions:

- `REGIST_CENTRAL_0_1_8_SNAPSHOT_TOKEN`: read access to `Acadynamo/Resources-release`, see above.
