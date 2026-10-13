# Synode end-to-end tests (Linux)

Two workflows use these resources:

- `.github/workflows/synodes-linux-closed-test.yml`: GitHub > Actions > **synodes-linux-closed-test**
  > **Run workflow**. Sets up a central and 3 synodes, pushes a file from a test client, checks the
  synchronization.
- `.github/workflows/version-revival-7days.yml`: every 6 days, or by hand. Builds the test clients
  and echoes to the central; keeps the vcpkg cache alive (see below).

Shared steps are composite actions in `.github/actions/`: `start-central` (download and start the
central) and `build-github-tests` (build the test clients).

Each run downloads two things from this repo's releases, so nothing heavy is committed here:

- the newest `synode-*-x64_linux*.tar.gz` in any release (or the release tag typed in the run form);
- the registry central, from the release `alpha-central-0.1.8` of the private repository
  `Acadynamo/Resources-release` (both can be changed in the run form).

## Layout

```
jserv-album/actions-config/
  feed_answers.py          feeds answers/<node>.txt into synode-cli
  answers/hub.txt          answers for the hub   (jserv 8961, web 8951)
  answers/prv1.txt         answers for prv1      (jserv 8962, web 8952)
  answers/prv2.txt         answers for prv2      (jserv 8963, web 8953)
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
in state `c` (create): `<dom>-hub`, `<dom>-1`, `<dom>-2`. The answer files take `closed-1`:
hub = `closed-1-hub`, prv1 = `closed-1-1`, prv2 = `closed-1-2`. A synode that is no longer in
state `c` can't be selected again, so keep the released snapshot in that state.

Every run starts from the released snapshot, so runs don't affect each other.

The package logs in to Central as `admin` with `centralPswd` from its `WEB-INF/settings.json`
(0.8.0: `temp-0.8.0`). The test central's admin password is `123456` (`CENTRAL_PSWD` in the
workflow, test only): the workflow writes it into each node's settings.json before running
synode-cli, which would otherwise stop to ask for it and shift the answers.

The snapshot also holds other orgs. This repository is public, so the run only reports the rows
of `closed-test` and doesn't upload the database.

## The desktop push

The test client is `odys-z/Anclient` `examples/example.slint/github-tests` (branch from the run
form, default `portfolio-0.8`): the desktop's ws client and IPC agent tests without Slint, webview
or SurrealismUI. Its targets:

- `revial_tests`: echo to the central (`CENTRAL_URL`), used by version-revival-7days;
- `doclient_tests`: `../tests/test_01_wsclient_ipcagent.cpp`, used by the closed test.

`.github/actions/build-github-tests` builds it with CMake 3.30+, Ninja and g++-14 (C++20
`std::chrono::parse` needs GCC 14 or newer), vcpkg at commit `7e43e0768a`, triplet `x64-linux`.

The IPC agent jar and the settings come from the `desktop-*.zip` of the same release as the synode
package; the settings are pointed at the hub (`synode_jserv`, `synode_id` `closed-1-hub`, device
`ci-desktop`). `Ipclient.E2E_PushSync` pushes `res/182x121.png` with a unique name, waits for it in
the hub's and prv1's volumes, then the workflow checks `h_photos` of hub, prv1 and prv2.

## The vcpkg cache

vcpkg's compiled packages are cached under a fixed key: runner image, vcpkg commit and a hash of
the package list (`vcpkg-ubuntu24-x64-linux-7e43e0768a-<hash>`). GitHub evicts a cache entry that
no run has restored for 7 days (not configurable; the 90 days in Settings > Actions are for
artifacts and logs). version-revival-7days restores it every 6 days, so the closed test finds it.
Changing the commit or the packages makes a new entry, and the next run builds the packages again.

Schedules run only from the default branch, and GitHub disables a schedule after 60 days without
activity in the repository: re-enable it on the workflow's page.

## Answer files

One answer per line, in the order of the prompts (see `feed_answers.py`):

| Line | Meaning |
|---|---|
| `1` | menu option number, 1-based |
| `<enter>` | accept the prompt's default |
| `<clear>8951:8961` | replace the pre-filled default |
| `${CENTRAL_URL}` | from the workflow `env` |
| `# ...` | comment, whole line or trailing |

Check a file without running anything:

```
python feed_answers.py answers/prv1.txt --dry-run
```

If a run fails, compare `logs/<node>-cli.log` in the run's artifact with the answer file:
an unexpected prompt shifts every later answer by one.

## Secrets

Settings > Secrets and variables > Actions:

- `REGIST_CENTRAL_0_1_8_SNAPSHOT_TOKEN`: read access to `Acadynamo/Resources-release`, see above.

## Domain token

The answer files accept the packaged default: `synode.py/tasks.py` writes `deploy.domain_token` of
`tasks.*.json` into the package's `registry/dictionary.json` (user `admin`). It must be 8-16 chars,
or synode-cli rejects the default and the run stops.
