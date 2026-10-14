# Workflows

| Workflow | Started | What it does |
|---|---|---|
| `synodes-linux-closed-test.yml` | by hand | Central + 3 Linux synodes as systemd services, a push from the C++ test client, `h_photos` checked |
| `synodes-linux-upgrade-test.yml` | by hand | The Linux closed test, then `synode-upgrade-srv` on every synode, then the checks again |
| `synodes-windows-closed-test.yml` | by hand | The same on Windows: synodes as Windows services, a push from the Java (Android) client |
| `synodes-windows-upgrade-test.yml` | by hand | The Windows closed test, then `upgrade.exe` on every synode, then the checks again |
| `version-revival-7days.yml` | every 6 days, or by hand | Builds the test clients, echoes to the central, keeps the vcpkg cache alive |

Start one from the Actions tab: pick the workflow, **Run workflow**, choose the branch (e.g.
`portfolio-0.8`). The test resources and their details are in [`../closed-test/README.md`](../closed-test/README.md).

## Starting a workflow from the command line

All five workflows have a `workflow_dispatch` trigger, so the GitHub CLI can start them like the
**Run workflow** button. `--ref` is the branch whose workflow file and sources are used; each `-f`
sets one input of the run form, and an input left out takes its default.

```bash
R=odys-z/semantic-jserv      # for this terminal session; set it again in a new one

# Linux / Windows closed test: the newest synode package, the default central and Anclient branch
gh workflow run synodes-linux-closed-test.yml   -R $R --ref portfolio-0.8
gh workflow run synodes-windows-closed-test.yml -R $R --ref portfolio-0.8

# the same, with every input given (template: change or drop what you don't need)
gh workflow run synodes-windows-closed-test.yml -R $R --ref portfolio-0.8 \
    -f release_tag=portfolip-0.8 \
    -f central_repo=Acadynamo/Resources-release \
    -f central_release=alpha-central-0.1.8 \
    -f anclient_ref=portfolio-0.8

# Linux / Windows upgrade test: install release_tag, then upgrade to upgrade_tag (empty: the newest package)
gh workflow run synodes-linux-upgrade-test.yml   -R $R --ref portfolio-0.8 \
    -f release_tag=portfolip-0.8 -f upgrade_tag=
gh workflow run synodes-windows-upgrade-test.yml -R $R --ref portfolio-0.8 \
    -f release_tag=portfolip-0.8 -f upgrade_tag=

# revival test, by hand (it also runs every 6 days, from the default branch)
gh workflow run version-revival-7days.yml -R $R --ref portfolio-0.8

# follow the run just started (give GitHub a few seconds to create it), exit status = the run's result
sleep 5
id=$(gh run list -R $R --workflow synodes-windows-closed-test.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch $id -R $R --exit-status
gh run view $id -R $R --log-failed        # the failed steps' logs
gh run download $id -R $R -D ../run-$id   # the artifact (logs, settings, central tables)
```

Inputs and their defaults (the run form shows the same):

| Workflow | Inputs (default) |
|---|---|
| `synodes-linux-closed-test.yml` | `release_tag` (newest), `central_repo` (`Acadynamo/Resources-release`), `central_release` (`alpha-central-0.1.8`), `anclient_ref` (`portfolio-0.8`) |
| `synodes-windows-closed-test.yml` | the same as Linux |
| `synodes-linux-upgrade-test.yml` | the same, and `upgrade_tag` (newest) |
| `synodes-windows-upgrade-test.yml` | the same, and `upgrade_tag` (newest) |
| `version-revival-7days.yml` | `anclient_ref` (`portfolio-0.8`), `central_release` (`alpha-central-0.1.8`) |

## Managing the run history

Deleting a run also deletes its logs and artifacts; caches and the workflows themselves are kept.
In the browser: Actions > the workflow > a run's **⋯** > **Delete workflow run**.

With the GitHub CLI (`gh`), in Bash (Git Bash on Windows). `--workflow` takes the file name, not
its path:

```bash
R=odys-z/semantic-jserv      # for this terminal session; set it again in a new one

# list the runs of a workflow (add --status failure, success, cancelled, ... to filter)
gh run list -R $R --workflow synodes-windows-closed-test.yml --limit 100

# delete the failed runs of a workflow
gh run list -R $R --workflow synodes-windows-closed-test.yml --status failure --limit 500 \
    --json databaseId -q '.[].databaseId' |
  xargs -I{} gh api -X DELETE repos/$R/actions/runs/{}

# delete all runs of a workflow
gh run list -R $R --workflow synodes-windows-closed-test.yml --limit 500 \
    --json databaseId -q '.[].databaseId' |
  xargs -I{} gh api -X DELETE repos/$R/actions/runs/{}

# delete one run (the id is the number in the run's URL)
gh api -X DELETE repos/$R/actions/runs/<run-id>

# a run in progress can't be deleted: cancel it first
gh run cancel <run-id> -R $R
```

- More runs than `--limit`: raise it, or repeat the command until `gh run list` shows nothing.
- Runs of a workflow file that was renamed or removed (e.g. the old `synode-e2e-linux.yml`) are
  deleted the same way, with the old file name; its entry leaves the Actions sidebar when no run
  is left.
- Download a run's artifact (e.g. the logs) instead of unzipping it into the repository:
  `gh run download <run-id> -R $R -D ../run-<run-id>`
