# ISSUE upgrade-config-picks: the upgrade keeps configuration files by a pick list, not by versioned entries

- Found: 2026-11-13, Portfolio 0.8.2 (in development)
- Status: to do 1-3 done 2026-11-13 (semantics.py3 `ConfigPicks`, `PkgResources.config_picks` / `pick_config()`;
  synode.py `update_srv()` step 4; `resource.json` `config_picks`; dynaResource's defaults in the template
  `WEB-INF-0.8/html-service.json`; html-service's `DynaResource.synode` -> `syn_jserv`).
  Open before the next release: 4-6, the CI test of history fixtures and the generated markdown.
- Tags: `ISSUE upgrade-config-picks` in
  - `synode.py/src/synodepy3/commands.py`, `update_srv()` step 4
  - `jserv-album/src/main/resource.json`, `// config_picks`
  - semantics.py3 (github antson) `semanticshare/io/oz/syn/pkgresource.py`, `ConfigPicks`, `PkgResources.config_picks`

## The problem

An upgrade (`update_srv()`) backs up `WEB-INF/settings.json` and `WEB-INF/html-service.json`, unpacks the
new package, and copies the backups back. So what a new version needs in these files never arrives:
a renamed type (`io.oz.srv.ResPath` -> `StaticResource`, html-web 0.2.1), a new section (`dynaResource`).
html-web 0.2.1 doesn't start with a 0.8.1's `html-service.json`.

## What is in the code now (0.8.2 development)

`ConfigUpgrades`, semantics.py3 0.6.20: `bin/resource.json` has `upgrades`, a list of
`{since, files: {path: {replace, add}}}`; `since` is the minimum installed version an entry applies to,
and every such entry is applied at every upgrade. Its weakness: an old entry runs again forever, so a
later rename can't be expressed (the old entry adds the old name back), and a user's deleted key returns.

## The decision (2026-11-13)

A pick list. The new configuration is the new package's template, plus the user's values of the fields listed:

    new config = the package's template + the installed file's values of the picked fields

    "config_picks": {
      "WEB-INF/html-service.json": {
        "port":                   ["port"],
        "dynaResource.syn_jserv": ["dynaResource.syn_jserv", "dynaResource.synode"]
      }
    }

- Each picked field has the names it ever had, the newest first; the first one found in the installed
  file is taken. No version numbers: the name present tells the file's shape. A release renaming a field
  appends the old name; names are kept while `upgrade_from` still accepts their versions.
- The installed file is read as plain json, never by the typed classes, so an old type name or an
  unknown section can't fail it. Not json at all: nothing picked, the template is used, and it's reported.
- A picked value of another kind than the template's (number, text, list, object) is not taken, and reported.
- Then the installer fills the computed values, as for a new installation (`update_htmlsrv()`:
  `port` from `settings.webport`, `dynaResource.syn_jserv` from `settings.port`).
- A file not in `config_picks` is restored whole, as today: `settings.json`. Its new fields get their
  defaults in the code (`AppSettings`).
- `paths` is not picked: the distribution's template has it, with the current type name.
- No confirm page: the upgrade prints what is kept and what is not carried over; the old file is in `backup-*/`.
- Sandbox refresh (`--sandbox`): configuration files are not touched.
- Users upgrade 0.8.0 / 0.8.1 with the 0.8.2 upgrader; `bin/resource.json` `upgrader.min` tells the
  oldest tool a package accepts (already in the code).

Rejected on the way: versioned entries applied once (needs a record of what was applied, and a known
installed version, which an installation without `bin/resource.json` doesn't have); a policy per field,
`user | overwrite | default | delete` (still too much to maintain).

## To do

1. semantics.py3: the pick, in place of `ConfigUpgrades`; `PkgResources.upgrades` -> `config_picks`.
2. synode.py `update_srv()` step 4: a picked file is merged, the others restored; then `update_htmlsrv()`.
3. `jserv-album/src/main/resource.json`: `config_picks`, `upgrades` removed.
4. CI test (with synode.py's tests, `.github/closed-test`):
   - fixtures, one `html-service.json` of every shape ever shipped (table below);
   - each merged with the current template, the result must load in the current html-service and in
     the Python `WebConfig`;
   - a guard: every field name ever in the history is in the pick list (a name or an alias) or in an
     explicit dropped list, so a new or renamed field fails until it's decided.
5. A markdown of the pick list, generated from it: per file, the field, the names it had, since when,
   picked or dropped.
6. Needed from Ody: the list of released Portfolio versions (html-service has no release tags).

## Field names of html-service.json, from the history (traced 2026-11-13)

html-service (all 88 commits), semantics.py3's `io.oz.srv`, and the templates of semantic-jserv
(`jserv-album/src/main/webapp/WEB-INF-0.7*`, `WEB-INF-0.8`, `synode.py/WEB-INF`).

| field | first | notes |
|---|---|---|
| `port`, `paths` | 2025-04-03, html-web 0.0.1 | never renamed |
| `startHandler` | 2025-04-20, html-web 0.1.3 | never renamed; only `io.oz.srv.JservLocalHandler` |
| `welcomepages` | 2025-04-25, html-web 0.1.4 | Java only until semantics.py3 0.6.20 |
| `dynHandler` | 2026-11-12, one commit | became `dynaResource` the same day, not released |
| `dynaResource` | 2026-11-12, html-web 0.2.1 | |
| `comments` | 2025-04-20, jserv-album's templates | a note of the template, not a Java field |
| `json_path` | 2025-09-29, `synode.py/WEB-INF` only | renamed `json_pat-delete` 2025-11-26, removed 2026-08-19 |
| `paths[].path`, `.resource`, `.allowDir` | 2025-04-03 | never renamed; type `io.oz.srv.ResPath` -> `StaticResource` 2026-11-12 |
| `dynaResource.help`, `.resource`, `.pollInterval_snd`, `.allowDir`, `.danyroot` | 2026-11-12 | |
| `dynaResource.handler` | 2026-11-12 | |
| `dynaResource.ziproot` | 2026-11-12, one commit | removed the same day, not released |
| `dynaResource.synode` | 2026-11-12 | renamed `syn_jserv` 2026-11-13 (Python; check html-service's Java) |

No released field was ever renamed: the renames found are all in the unreleased 0.2.1 work. An installed
file was written by the Python `WebConfig`, which before 0.6.20 knew `port`, `paths`, `startHandler` only.
`settings.json` (`AppSettings`, docsync.jserv and its Python twin) is not traced.

## Related, found on the way

- An installation without `bin/resource.json` is taken as the upgrader's own version (`running_resources()`).
- A failed configuration upgrade doesn't stop the upgrade: it's reported, and the services are started.
- Published html's shared templates: project doc `architecture-published-html-templates.md`.
