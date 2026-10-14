# Synode end-to-end tests (Linux, Windows)

Five workflows use these resources:

- `.github/workflows/synodes-linux-closed-test.yml`: GitHub > Actions > **synodes-linux-closed-test**
  > **Run workflow**. Sets up a central and 3 synodes, installed as systemd services by synode-cli,
  pushes a file from a test client, checks the synchronization.
- `.github/workflows/synodes-linux-upgrade-test.yml`: the Linux closed test, then upgrades every
  synode with the installed `synode-upgrade-srv` (uninstall the units, upgrade, reinstall and start
  them, install the new synode_py3 wheel), and checks again.
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
    upgrade.txt              synode-upgrade-srv, every node
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
- setup-cli.exe installs the synode and web services (synodepy3 `commands.install_winsrvs()`). The
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
envars `linusrv.synode` / `linusrv.web`), so the upgrade has units to stop and reinstall. In each
installation folder, the installed tool (the synode_py3 wheel of the installed package) runs:

```
synode-upgrade-srv <upgrade package tar.gz>
```

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
