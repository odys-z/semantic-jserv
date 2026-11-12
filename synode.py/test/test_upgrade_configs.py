'''
An upgrade keeps the installed configuration files, and upgrades them as the new package's manifest
tells: bin/resource.json's upgrades (semanticshare ConfigUpgrades), and upgrader, the oldest upgrade
tool installing the package. The manifest is the real one, jserv-album/src/main/resource.json.

Linux only (the package is a .tar.gz). Run in synode.py:  pytest test/test_upgrade_configs.py
'''
import json
import os
import shutil
import sys
import tarfile
from pathlib import Path

import pytest

src = Path(__file__).parent.parent / 'src'
sys.path.insert(0, str(src))

# installer_api loads synodepy3/synode.json, written by tasks.py at build: the github one will do
synode_json = src / 'synodepy3' / 'synode.json'
made_json = not synode_json.exists()
if made_json:
    shutil.copy(src / 'synodepy3' / 'synode.github.json', synode_json)

from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings  # noqa: E402
from semanticshare.io.oz.syn.pkgresource import PkgResources  # noqa: E402
from synodepy3.commands import update_srv, upgrade_configs  # noqa: E402
from synodepy3.installer_api import InstallerCli, load_resources  # noqa: E402

pytestmark = pytest.mark.skipif(os.name == 'nt', reason='the package is a .tar.gz, Linux only')

# the package's manifest
resource_src = Path(__file__).parent.parent.parent / 'jserv-album' / 'src' / 'main' / 'resource.json'

# html-service.json as 0.8.0 and 0.8.1 installed it
html_081 = '''{
  "type": "io.oz.srv.WebConfig",
  "port": 8900,
  "paths": [{
    "type": "io.oz.srv.ResPath",
    "path": "/*",
    "resource": "web-dist",
    "allowDir": false
  }],
  "startHandler": null
}'''


def teardown_module():
    if made_json:
        synode_json.unlink(missing_ok=True)


def write(p: Path, text: str):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def install(root: Path, sandbox: str = None) -> Path:
    ''' An installed synode 0.8.1 in root, its jserv at port 8964. '''
    st = AppSettings()
    st.Volume(str((root / 'vol').resolve()))
    st.rootkey = '0123456789abcdef'
    st.installkey = None
    st.sandbox = sandbox
    st.port = 8964
    (root / 'WEB-INF').mkdir(parents=True)
    st.toFile(str(root / 'WEB-INF' / 'settings.json'))
    write(root / 'WEB-INF' / 'html-service.json', html_081)
    shutil.copytree(Path(__file__).parent.parent / 'registry', root / 'registry')
    shutil.copy(root / 'registry' / 'dictionary.github.json', root / 'registry' / 'dictionary.json')
    return root


def package(path: Path, upgrader_min: str = None) -> Path:
    ''' A package with the manifest, bin/resource.json. '''
    pkg = path.parent / 'pkg'
    res = json.loads(resource_src.read_text(encoding='utf-8'))
    if upgrader_min:
        res['upgrader'] = {'min': upgrader_min}
    write(pkg / 'bin' / 'resource.json', json.dumps(res))
    write(pkg / 'web-dist' / 'index.html', 'new page')
    with tarfile.open(path, 'w:gz') as t:
        for f in ('bin', 'web-dist'):
            t.add(pkg / f, arcname=f)
    return path


def test_upgrade_configs(tmp_path: Path, monkeypatch):
    ''' the kept html-service.json of a 0.8.1, upgraded for the new package '''
    root = install(tmp_path / 'synode')
    monkeypatch.chdir(root)
    cli = InstallerCli()
    cli.load_settings()

    assert upgrade_configs(cli, PkgResources.load(str(resource_src)), '0.8.1')

    html = json.loads((root / 'WEB-INF' / 'html-service.json').read_text())
    # html-web 0.2.1 renamed ResPath
    assert html['paths'] == [{'type': 'io.oz.srv.StaticResource', 'path': '/*', 'resource': 'web-dist', 'allowDir': False}]
    # the docs published by the synode of this installation
    assert html['dynaResource']['handler'] == 'io.oz.srv.ResPathsTier'
    assert html['dynaResource']['synode'] == 'http://127.0.0.1:8964/jserv-album'
    # the user's port
    assert html['port'] == 8900


def test_sandbox_configs_kept(tmp_path: Path, monkeypatch):
    ''' a sandbox refresh gets the package's files, its configuration files are not upgraded '''
    root = install(tmp_path / 'sandbox', sandbox='isolated')
    pkg = package(tmp_path / 'synode-0.8.2.tar.gz')
    monkeypatch.chdir(root)

    update_srv(str(pkg), sandbox=True)

    assert (root / 'web-dist' / 'index.html').read_text() == 'new page'
    assert len(load_resources().upgrades) > 0
    assert (root / 'WEB-INF' / 'html-service.json').read_text() == html_081


def test_old_upgrader_refused(tmp_path: Path, monkeypatch, capsys):
    ''' a package needing a newer upgrade tool is refused, nothing changed '''
    root = install(tmp_path / 'synode')
    pkg = package(tmp_path / 'synode-9.0.0.tar.gz', upgrader_min='99.0.0')
    monkeypatch.chdir(root)

    update_srv(str(pkg))

    assert 'this upgrade tool' in capsys.readouterr().err
    assert not (root / 'web-dist').exists()
    assert not list(root.glob('backup-*'))
    assert (root / 'WEB-INF' / 'html-service.json').read_text() == html_081


def test_new_installation_htmlsrv(tmp_path: Path, monkeypatch):
    ''' a new installation's html-service.json: the packed template, with the ports and the synode '''
    template = Path(__file__).parent.parent.parent / 'jserv-album' / 'src' / 'main' / 'webapp' / 'WEB-INF-0.8' / 'html-service.json'
    write(tmp_path / 'WEB-INF' / 'html-service.json', template.read_text(encoding='utf-8'))
    monkeypatch.chdir(tmp_path)
    st = AppSettings()
    st.port, st.webport = 8964, 8901

    InstallerCli.update_htmlsrv(None, st)

    html = json.loads((tmp_path / 'WEB-INF' / 'html-service.json').read_text())
    assert html['port'] == 8901
    assert html['paths'][0]['type'] == 'io.oz.srv.StaticResource' and html['paths'][0]['path'] == '/*'
    assert html['dynaResource']['type'] == 'io.oz.srv.DynaResource'
    assert html['dynaResource']['synode'] == 'http://127.0.0.1:8964/jserv-album'
