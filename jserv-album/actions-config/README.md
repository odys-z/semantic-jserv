# Synode end-to-end tests (Linux)

Run by `.github/workflows/synode-e2e-linux.yml`: GitHub > Actions > **synode-e2e-linux** > **Run workflow**.

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

The central runs in the folder holding `WEB-INF/`, as `java -jar <central>.jar settings.json`. Without `regist-vol/system.sqlite` it
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
