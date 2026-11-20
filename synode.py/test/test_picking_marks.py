'''
The package's upgrade picking lists against the marks in the sources, as jserv-album/tasks.py package()
checks them (semanticshare io.oz.syn.dbpicks.DbPicks.check_marks()); here the source files of
repositories not found beside semantic-jserv are skipped, e.g. in a CI checking out semantic-jserv only.

The ways a list and its marks can differ are tested with semantics.py3 (test/t_05_dbpicks.py).
'''
from pathlib import Path

from semanticshare.io.oz.syn.dbpicks import DbPicks
from semanticshare.io.oz.syn.pkgresource import PkgResources

main = Path(__file__).parent.parent.parent / 'jserv-album' / 'src' / 'main'
github = main.parent.parent.parent.parent


def test_picking_marks():
    checked = DbPicks.load(str(main / 'doctree-picks.json')).check_marks(
            str(github), str(main / 'picking-marks.json'),
            PkgResources.load(str(main / 'resource.json')).config_picks, strict=False)
    assert checked.errors == []
    for s in checked.skipped:
        print('skipped:', s)


def test_doctree_picks():
    '''the doc-tree db's picking list, typed as Java's io.oz.syn.dbpicks (doctree.jserv) reads it'''
    picks = DbPicks.load(str(main / 'doctree-picks.json'))
    assert type(picks).__name__ == 'DbPicks'
    t = picks.pick('s_docs', 'template')
    assert t.when is not None and t.when.op == 'isNotnull' and t.when.l == 'builtAt'
