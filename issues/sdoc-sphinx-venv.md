# ISSUE sdoc-sphinx-venv: synodes build docs' html with the Sphinx of the installer's venv

- Found: 2026-11-13, Portfolio 0.8.2 (in development)
- Status: implemented 2026-11-13 (patches on antson a1e7613, semantic-jserv 6ca2126, html-service 2ef5aef)
- Tags: `ISSUE sdoc-sphinx-venv` in
  - `synode.py/pyproject.toml`, the Sphinx dependencies
  - `synode.py/src/synodepy3/sdoc.py`, `sphinx_of_installer()`, `build_pages()`
  - `synode.py/src/synodepy3/installer_api.py` `install()`, `commands.py` `update_srv()`
  - `docsync.jserv` `AppSettings.sdocSphinx`; `jserv-album` `SynotierJettyApp`, where the builder is wired

## What changes for an installation

Before 0.8.2, a Linux synode needed its Python venv only to install and upgrade: synode.py (setup-cli,
synode-upgrade-srv) runs in it, the services are Java (`java -jar`) and don't use it.

From 0.8.2, on Linux (and macOS, once there is a package), the synode also uses the venv at run time:
a committed doc's html is built by the venv's `sphinx-build` (doctree.jserv `HtmlBuilder`, rst -> html).
So the venv must be kept, where it was installed. Without it, the synode still runs, but docs are not
built to html, nor published (a warning at start).

Windows is not changed: Sphinx will be packed in the Windows distribution.

## The decision

1. Sphinx comes with synode.py, as its dependencies on non-Windows systems, without an upper pin:

       "sphinx>=7; sys_platform != 'win32'",
       "sphinx_rtd_theme>=2; sys_platform != 'win32'",
       "docutils; sys_platform != 'win32'",

   pip installs the newest Sphinx the venv's Python supports. Any version that builds is accepted, also
   one older than the template's pinned `sphinx==9.2.0` (Sphinx 9.1 needs Python 3.12; 9.0.4 is the last
   for 3.11). The browser preview always uses the pinned versions (Pyodide), so a published page can
   differ a little from the preview.

   An upgrade installs the new `synode_py3` wheel in the venv with `pip install --upgrade`, which brings
   these dependencies too.

2. The synode runs the venv's executable, `<venv>/bin/sphinx-build`, as a child process. No activation:
   a venv's script runs with the venv's Python (its first line). A service never has the venv, nor the
   user's shell, in its PATH (systemd, launchd), so the path is configured:

       settings.json
         "sdocSphinx":       "<venv>/bin/sphinx-build",   default "sphinx-build", found in PATH
         "sdocTemplateRoot": "web-dist/sdoc-template",    templates, one folder per id
         "sdocTemplateId":   "rtd-0.8"

3. `sdocSphinx` is a value the installer computes, as html-service's port and `dynaResource.syn_jserv`:
   - a new installation: the installer (running in the venv) writes `<sys.prefix>/bin/sphinx-build`;
   - an upgrade, a sandbox's too: the upgrade tool (running in the venv) writes it again, in the kept
     `settings.json`, changing only this value;
   - no sphinx-build in the environment (Windows until Sphinx is packed): the value is kept, or the default.

4. Usable, checked twice:
   - synode.py, installing and upgrading: a build with the installed template, which also makes
     html-service's two pages in the web-dist root, `status.html` and `404.html` (Portfolio version,
     synode id, OS, date, Sphinx version, template id). It checks Sphinx, the theme and docutils together,
     in about two seconds. The pages link the template's files, `/sdoc-template/<id>/_static/`. If it
     fails: a warning, the pages already there are kept.
   - the synode, at start: `<sdocSphinx> --version`. If it fails: a warning, and the builder is not wired
     (commits still work).
   The warnings tell how to install: `<venv>/bin/pip install sphinx sphinx_rtd_theme docutils`.

5. html-service (html-web 0.2.1):
   - `"notfound": "/404.html"` in html-service.json: the page of every 404, the status kept 404. A
     release value, in the template, not picked.
   - The published docs' folder is no longer emptied at start: the docs stay online when the synode
     can't be reached; when it can, the folders of docs it no longer publishes are removed.

## Not taken

- `pip install --user`, or a system package: `~/.local/bin` and the like are not in a service's PATH.
- Homebrew's `sphinx-doc` (macOS): keg-only (not in PATH), meant for other formulae, and without
  `sphinx_rtd_theme`; Homebrew advises pip.
- Activating the venv for the service, or adding it to the unit's PATH: the path in settings.json is
  explicit, and the same for a sandbox and, later, Windows' packed executable.

## Related

- Upgrading a sandbox (`synode-upgrade-srv --sandbox`) is the same as a real upgrade, the services aside:
  the configuration files are picked and the computed values written, so an upgrade can be tried on a
  sandbox first. See [upgrade-config-picks.md](upgrade-config-picks.md).
- Published html, shared templates: project doc `architecture-published-html-templates.md`.
