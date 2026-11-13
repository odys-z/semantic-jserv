'''
Upgrading an installed synode 0.8.0 with the package 0.8.2, by update_srv(): the package brings
bin/resource.json, the manifest, and WEB-INF/html-service.json, its template; the installed
html-service.json is made of the template and the installed values its config_picks tell, then the
installer's values are filled (ISSUE upgrade-config-picks). The manifest and the template are the
real ones, of jserv-album. The services are not real: stopping and restarting them is skipped.

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
from synodepy3 import commands  # noqa: E402

pytestmark = pytest.mark.skipif(os.name == 'nt', reason='the package is a .tar.gz, Linux only')

jserv_album = Path(__file__).parent.parent.parent / 'jserv-album'
resource_src = jserv_album / 'src' / 'main' / 'resource.json'
template_src = jserv_album / 'src' / 'main' / 'webapp' / 'WEB-INF-0.8' / 'html-service.json'

# html-service.json as 0.8.0's installer wrote it, with a value the user changed
html_080 = '''{
  "type": "io.oz.srv.WebConfig",
  "port": 8900,
  "paths": [{
    "type": "io.oz.srv.ResPath",
    "path": "/",
    "resource": "web-dist",
    "allowDir": false
  }],
  "startHandler": null,
  "welcomepages": ["home.html"]
}'''


def teardown_module():
    if made_json:
        synode_json.unlink(missing_ok=True)


def write(p: Path, text: str):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def test_upgrade_080_to_082(tmp_path: Path, monkeypatch):
    # an installed synode 0.8.0, its jserv at port 8964, web pages at 8901: no bin/resource.json yet
    root = tmp_path / 'synode'
    st = AppSettings()
    st.Volume(str((root / 'vol').resolve()))
    st.rootkey = '0123456789abcdef'
    st.installkey = None
    st.port, st.webport = 8964, 8901
    (root / 'WEB-INF').mkdir(parents=True)
    st.toFile(str(root / 'WEB-INF' / 'settings.json'))
    settings_080 = (root / 'WEB-INF' / 'settings.json').read_text()
    write(root / 'WEB-INF' / 'html-service.json', html_080)
    write(root / 'web-dist' / 'index.html', 'page 0.8.0')
    shutil.copytree(Path(__file__).parent.parent / 'registry', root / 'registry')
    shutil.copy(root / 'registry' / 'dictionary.github.json', root / 'registry' / 'dictionary.json')
    assert not (root / 'bin' / 'resource.json').exists()

    # the package 0.8.2: the manifest, the template of html-service.json and settings.json, a new page
    pkg = tmp_path / 'pkg'
    write(pkg / 'bin' / 'resource.json', resource_src.read_text(encoding='utf-8'))
    write(pkg / 'WEB-INF' / 'html-service.json', template_src.read_text(encoding='utf-8'))
    write(pkg / 'WEB-INF' / 'settings.json', '{"type": "template, never kept"}')
    write(pkg / 'web-dist' / 'index.html', 'page 0.8.2')
    tar = tmp_path / 'synode-0.8.2.tar.gz'
    with tarfile.open(tar, 'w:gz') as t:
        for f in ('bin', 'WEB-INF', 'web-dist'):
            t.add(pkg / f, arcname=f)

    # no real services here
    monkeypatch.setattr(commands, 'Sudo', lambda: None)
    monkeypatch.setattr(commands, 'stop_sysunits', lambda cli, sudo: ['synode.service', 'html-web.service'])
    monkeypatch.setattr(commands, 'apply_unit_resources', lambda sudo, units, old, new: True)
    monkeypatch.setattr(commands, 'restart_sysunits', lambda sudo, units, ask=False: {u: 'active' for u in units})
    monkeypatch.setattr(commands, 'promote_upgrade_whl', lambda: True)
    monkeypatch.chdir(root)

    commands.update_srv(str(tar))

    # the package's files, with bin/resource.json
    assert (root / 'web-dist' / 'index.html').read_text() == 'page 0.8.2'
    assert json.loads((root / 'bin' / 'resource.json').read_text())['versions']['jar_ver'] == '0.8.2'

    # settings.json, not picked, is kept as installed
    assert (root / 'WEB-INF' / 'settings.json').read_text() == settings_080

    html = json.loads((root / 'WEB-INF' / 'html-service.json').read_text())
    # the user's value, picked
    assert html['welcomepages'] == ['home.html']
    # the new version's: paths, with the new type and the root path, and the start handler
    assert [p['path'] for p in html['paths']] == ['/*']
    assert html['paths'][0]['type'] == 'io.oz.srv.StaticResource'
    assert html['startHandler'][0] == 'io.oz.srv.JservLocalHandler'
    # a missing field, with the template's default values
    assert html['dynaResource']['handler'] == 'io.oz.srv.ResPathsTier'
    assert html['dynaResource']['pollInterval_snd'] == 3600
    assert html['dynaResource']['danyroot'] == 'pub'
    # the installer's values, of settings.json
    assert html['port'] == 8901
    assert html['dynaResource']['syn_jserv'] == 'http://127.0.0.1:8964/jserv-album'

    # the file before the upgrade is in the backup
    backup = next(root.glob('backup-*'))
    assert (backup / 'WEB-INF' / 'html-service.json').read_text() == html_080
