# Synode end-to-end tests (Linux)

Run by `.github/workflows/synode-e2e-linux.yml`: GitHub > Actions > **synode-e2e-linux** > **Run workflow**.

Each run downloads the newest `synode-*-x64_linux*.tar.gz` from this repo's releases (or the
release tag typed in the run form), so only the resources below are kept here.

## Layout

```
jserv-album/actions-config/
  feed_answers.py          feeds answers/<node>.txt into synode-cli
  answers/hub.txt          answers for the hub   (jserv 8961, web 8951)
  answers/prv1.txt         answers for prv1      (jserv 8962, web 8952)
  answers/prv2.txt         answers for prv2      (jserv 8963, web 8953)
  central/                 upload by hand:
    <central>.jar            the registry central, runnable with java -jar
    WEB-INF/                 its WEB-INF: settings.json (port 1991, protocolRoot regist-central), xml, ...
    regist-vol/              its volume, settings.json "volume": "../regist-vol"
      system.sqlite          a snapshot of a working central (no initialization in the test)
      syslog.sqlite
```

`*.jar` is ignored by the root `.gitignore`; `actions-config/.gitignore` re-includes `central/*.jar`.

## The central volume snapshot

The synode package logs in to Central as `admin` with `centralPswd` from the package's
`WEB-INF/settings.json` (0.8.0: `temp-0.8.0`), market `alpha`, org `closed-test`. The snapshot
must have that org and user, and should have **no domain** of that org yet: `answers/hub.txt`
creates the domain, and the private nodes then join it. A snapshot with the domain already
registered changes the menus (and a synode already installed can't be selected again).

Every run starts from the committed snapshot, so runs don't affect each other.

## Answer files

One answer per line, in the order of the prompts (see `feed_answers.py`):

| Line | Meaning |
|---|---|
| `1` | menu option number, 1-based |
| `<enter>` | accept the prompt's default |
| `<clear>8951:8961` | replace the pre-filled default |
| `${DOMAIN_TOKEN}` | from the workflow `env` |
| `# ...` | comment, whole line or trailing |

Check a file without running anything:

```
python feed_answers.py answers/prv1.txt --dry-run
```

If a run fails, compare `logs/<node>-cli.log` in the run's artifact with the answer file:
an unexpected prompt shifts every later answer by one.

## Secret

Settings > Secrets and variables > Actions > `E2E_DOMAIN_TOKEN`: the domain token, 8-16 chars.
