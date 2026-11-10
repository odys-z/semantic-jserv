'''
synode-upgrade-srv --sandbox: a sandbox (settings.json's "sandbox" set) gets the package's files,
e.g. a new WEB-INF/semantics-doctree.xml, keeping its settings.json, dbs and host.json; a real
synode's upgrade still refuses a sandbox, and a sandbox refresh refuses a real synode.

Linux only (the package is a .tar.gz). Run in synode.py:  pytest test/test_sandbox_refresh.py
'''
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

from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings, sys_db, syn_db  # noqa: E402
from synodepy3.commands import update_srv, doctree_db  # noqa: E402

pytestmark = pytest.mark.skipif(os.name == 'nt', reason='the package is a .tar.gz, Linux only')

stale = '<semantics><s><smtc>fullpath</smtc><tabl>s_docnodes</tabl></s></semantics>'
fresh = '<semantics><s><smtc>fullpath2</smtc><tabl>s_docnodes</tabl></s></semantics>'


def teardown_module():
    if made_json:
        synode_json.unlink(missing_ok=True)


def write(p: Path, text: str):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def install(root: Path, sandbox: str = None) -> Path:
    '''
    An installed synode in root: WEB-INF (settings.json, a stale semantics-doctree.xml,
    html-service.json), its volume root/vol (dbs; no dictionary.json, a typed registry the loader
    parses, which the backup skips if missing), web-dist/private/host.json.
    '''
    vol = root / 'vol'
    st = AppSettings()
    st.Volume(str(vol.resolve()))
    st.rootkey = '0123456789abcdef'
    st.installkey = None
    st.sandbox = sandbox
    (root / 'WEB-INF').mkdir(parents=True)
    st.toFile(str(root / 'WEB-INF' / 'settings.json'))

    write(root / 'WEB-INF' / 'semantics-doctree.xml', stale)
    write(root / 'WEB-INF' / 'html-service.json', '{"local": true}')
    for db in (sys_db, syn_db, doctree_db):
        write(vol / db, f'{db} of the sandbox')
    write(root / 'web-dist' / 'private' / 'host.json', '{"local": "host"}')
    # an install's registry (registry/dictionary.json), loaded with the settings
    shutil.copytree(Path(__file__).parent.parent / 'registry', root / 'registry')
    shutil.copy(root / 'registry' / 'dictionary.github.json', root / 'registry' / 'dictionary.json')
    return root


def package(path: Path) -> Path:
    '''
    A package: WEB-INF with a template settings.json and the fresh semantics, a vol db template,
    a new web-dist page.
    '''
    pkg = path.parent / 'pkg'
    write(pkg / 'WEB-INF' / 'settings.json', '{"type": "template, never kept"}')
    write(pkg / 'WEB-INF' / 'semantics-doctree.xml', fresh)
    write(pkg / 'web-dist' / 'index.html', 'new page')
    with tarfile.open(path, 'w:gz') as t:
        for f in ('WEB-INF', 'web-dist'):
            t.add(pkg / f, arcname=f)
    return path


def test_sandbox_refresh(tmp_path: Path, monkeypatch):
    root = install(tmp_path / 'sandbox', sandbox='isolated')
    settings0 = (root / 'WEB-INF' / 'settings.json').read_text()
    pkg = package(tmp_path / 'synode-0.8.2.tar.gz')
    monkeypatch.chdir(root)

    update_srv(str(pkg), sandbox=True)

    # the package's files, e.g. the semantics
    assert (root / 'WEB-INF' / 'semantics-doctree.xml').read_text() == fresh
    assert (root / 'web-dist' / 'index.html').read_text() == 'new page'
    # the sandbox's own files: settings.json (sandbox kept), html-service.json, host.json, dbs
    assert (root / 'WEB-INF' / 'settings.json').read_text() == settings0
    assert (root / 'WEB-INF' / 'html-service.json').read_text() == '{"local": true}'
    assert (root / 'web-dist' / 'private' / 'host.json').read_text() == '{"local": "host"}'
    for db in (sys_db, syn_db, doctree_db):
        assert (root / 'vol' / db).read_text() == f'{db} of the sandbox'
    # backed up, doctree.db too
    backups = list(root.glob('backup-*'))
    assert len(backups) == 1 and (backups[0] / 'vol' / doctree_db).is_file()


def test_sandbox_not_upgraded_as_service(tmp_path: Path, monkeypatch, capsys):
    ''' without --sandbox, a sandbox is refused, nothing changed '''
    root = install(tmp_path / 'sandbox', sandbox='no-central')
    pkg = package(tmp_path / 'synode-0.8.2.tar.gz')
    monkeypatch.chdir(root)

    update_srv(str(pkg))

    assert 'refresh it with --sandbox' in capsys.readouterr().err
    assert (root / 'WEB-INF' / 'semantics-doctree.xml').read_text() == stale
    assert not list(root.glob('backup-*'))


def test_real_synode_not_refreshed_as_sandbox(tmp_path: Path, monkeypatch, capsys):
    ''' with --sandbox, a real synode is refused, nothing changed '''
    root = install(tmp_path / 'real')
    pkg = package(tmp_path / 'synode-0.8.2.tar.gz')
    monkeypatch.chdir(root)

    update_srv(str(pkg), sandbox=True)

    assert 'sandbox is not set' in capsys.readouterr().err
    assert (root / 'WEB-INF' / 'semantics-doctree.xml').read_text() == stale
    assert not list(root.glob('backup-*'))
