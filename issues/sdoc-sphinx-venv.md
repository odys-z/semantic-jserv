# ISSUE sdoc-sphinx-venv: synodes build docs' html with the Sphinx of the installer's venv

- Found: 2026-11-13, Portfolio 0.8.2 (in development)
- Status: decided, to be implemented
- Tags: `ISSUE sdoc-sphinx-venv` to be put in
  - `synode.py/pyproject.toml`, the Sphinx dependencies
  - `synode.py/src/synodepy3/prerequisites.py`, the Sphinx check
  - `synode.py/src/synodepy3/installer_api.py` and `commands.py`, where `sdocSphinx` is written
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
   - an upgrade: the upgrade tool (running in the venv) writes it again, as `settings.json` is kept;
   - a developer's sandbox: the default, `sphinx-build` in PATH, or the path set by hand.

4. Usable means: a tiny build, a one-line `index.rst` with the installed template, succeeds. It checks
   Sphinx, the theme and docutils together, in the environment of `sdocSphinx` (about a second).
   - synode.py `check_prerequisites()`, at installing and upgrading: a warning, not an error;
   - the synode, at start: if it fails, a warning, and the builder is not wired (commits still work).
   The warning tells how to install: `<venv>/bin/pip install sphinx sphinx_rtd_theme docutils`.

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
