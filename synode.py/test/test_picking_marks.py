'''
The package's upgrade picking lists against the marks in the sources (jserv-album/picking.py), as
tasks.py package() checks them; here the source files of repositories not found beside semantic-jserv
are skipped, e.g. in a CI checking out semantic-jserv only.
'''
import importlib.util
import json
from pathlib import Path

jserv_album = Path(__file__).parent.parent.parent / 'jserv-album'
github = jserv_album.parent.parent

spec = importlib.util.spec_from_file_location('picking', jserv_album / 'picking.py')
picking = importlib.util.module_from_spec(spec)
spec.loader.exec_module(picking)


def test_picking_marks():
    checked = picking.check(str(github), strict=False)
    assert checked.errors == []
    for s in checked.skipped:
        print('skipped:', s)


def write(p: Path, obj) -> str:
    p.write_text(json.dumps(obj), encoding='utf-8')
    return str(p)


def test_picking_mismatch(tmp_path: Path):
    '''each way a list and its marks can differ'''
    src = tmp_path / 'repo' / 'Meta.java'
    src.parent.mkdir()
    src.write_text('a // DB-PICKING-0.8.2 t.a\n'
                   'b // DB-PICKING-0.8.2 t.b\n'
                   'b // DB-PICKING-0.8.2 t.b\n'
                   'f // JSON-PICKING-0.8.2 x.json#f\n'
                   'g // JSON-PICKING-0.8.2 x.json#ff\n', encoding='utf-8')

    db = write(tmp_path / 'db-picks.json', {'picks': [
        {'mark': 'DB-PICKING-0.8.2 t.a', 'tabl': 't', 'col': 'a'},
        {'mark': 'DB-PICKING-0.8.2 t.b', 'tabl': 't', 'col': 'b'},
        {'tabl': 't', 'col': 'c'},                               # no mark
        {'mark': 'DB-PICKING-0.8.2 t.d', 'tabl': 't', 'col': 'd'}]})  # not in the marks file
    res = write(tmp_path / 'resource.json', {'config_picks': {'x.json': {'f': ['f'], 'g': ['g']}}})
    mk = write(tmp_path / 'marks.json', {'marks': {
        'DB-PICKING-0.8.2 t.a': 'repo/Meta.java',
        'DB-PICKING-0.8.2 t.b': 'repo/Meta.java',               # marked twice in the source
        'DB-PICKING-0.8.2 t.e': 'repo/Meta.java',               # no item, not in the source
        'JSON-PICKING-0.8.2 x.json#f': 'repo/Meta.java',        # f, not its prefix of ff
        'JSON-PICKING-0.8.2 x.json#h': 'other/Gone.java'}})     # no such field, no such file

    checked = picking.check(str(tmp_path), strict=True, db_picks=db, resource=res, marks=mk)
    errors = '\n'.join(checked.errors)
    assert 'an item without a mark' in errors
    assert 'no source of DB-PICKING-0.8.2 t.d' in errors
    assert 'DB-PICKING-0.8.2 t.e is no item' in errors
    assert 'no mark of config_picks x.json#g' in errors
    assert 'JSON-PICKING-0.8.2 x.json#h is no field' in errors
    assert 'DB-PICKING-0.8.2 t.b is marked 2 times' in errors
    assert 'DB-PICKING-0.8.2 t.e is marked 0 times' in errors
    assert 'other/Gone.java' in errors
    assert 'JSON-PICKING-0.8.2 x.json#f is marked' not in errors
    assert 'DB-PICKING-0.8.2 t.a ' not in errors and not errors.endswith('t.a')

    # not strict: a missing file is skipped
    checked = picking.check(str(tmp_path), strict=False, db_picks=db, resource=res, marks=mk)
    assert any('other/Gone.java' in s for s in checked.skipped)
