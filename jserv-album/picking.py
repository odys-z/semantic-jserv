'''
Check the package's upgrade picking lists against the marks in the sources.

The picking lists, generated and merged at each upgrade (by Claude), never edited by hand:
  src/main/db-picks.json      the db picks, packed as bin/db-picks.json (semantic-doc DoctreeDBUpgrade);
                              each item has its "mark", e.g. "DB-PICKING-0.8.2 s_docs.template"
  src/main/resource.json      config_picks {file: {field: [names]}}, packed as bin/resource.json
                              (semanticshare ConfigPicks); a field's mark is
                              "JSON-PICKING-<version> <file>#<field>", at its configuration type's field
  src/main/picking-marks.json {mark: source file, relative to the github folder}, generated with them

Checked, both ways:
  every db pick and every config_picks field has a mark in picking-marks.json, and every mark there has one;
  every mark is in its source file, once (a comment of the line needing the item).

Used by tasks.py package() (all the source files must be found), and by
synode.py/test/test_picking_marks.py (the files of repositories not found beside this one are skipped).
'''
import json
import os
import re
from dataclasses import dataclass, field
from typing import List

here = os.path.dirname(os.path.abspath(__file__))
db_picks_src = os.path.join(here, 'src', 'main', 'db-picks.json')
resource_src = os.path.join(here, 'src', 'main', 'resource.json')
marks_src = os.path.join(here, 'src', 'main', 'picking-marks.json')

db_mark = re.compile(r'^DB-PICKING-\d+(\.\d+)*\s+\S+$')
json_mark = re.compile(r'^JSON-PICKING-\d+(\.\d+)*\s+(\S+#\S+)$')


@dataclass
class Checked:
    errors: List[str] = field(default_factory=list)
    '''what's wrong'''
    skipped: List[str] = field(default_factory=list)
    '''source files not found, when not strict'''


def check(github: str, strict: bool = True,
          db_picks: str = db_picks_src, resource: str = resource_src, marks: str = marks_src) -> Checked:
    '''
    :param github: the folder of the repositories, e.g. ~/github
    :param strict: a source file not found is an error, else it's skipped
    :return: the errors, and the files skipped
    '''
    r = Checked()

    with open(db_picks, encoding='utf-8') as f:
        db = json.load(f)
    with open(resource, encoding='utf-8') as f:
        res = json.load(f)
    with open(marks, encoding='utf-8') as f:
        mk = json.load(f).get('marks', {})

    # the items' marks
    items = set()
    for it in (db.get('picks') or []) + (db.get('inits') or []):
        m = it.get('mark')
        if not m or not db_mark.match(m):
            r.errors.append(f'db-picks.json: an item without a mark DB-PICKING-<version> ...: {it}')
        elif m in items:
            r.errors.append(f'db-picks.json: duplicated mark: {m}')
        else:
            items.add(m)

    fields = {f'{file}#{fld}' for file, flds in (res.get('config_picks') or {}).items() for fld in flds}

    # the marks file
    marked_db, marked_json = set(), {}
    for m in mk:
        jm = json_mark.match(m)
        if db_mark.match(m) and not m.startswith('JSON-'):
            marked_db.add(m)
        elif jm:
            if jm.group(2) in marked_json:
                r.errors.append(f'picking-marks.json: {jm.group(2)} is marked twice: {marked_json[jm.group(2)]}, {m}')
            marked_json[jm.group(2)] = m
        else:
            r.errors.append(f'picking-marks.json: not a picking mark: {m}')

    for m in sorted(items - marked_db):
        r.errors.append(f'picking-marks.json: no source of {m}')
    for m in sorted(marked_db - items):
        r.errors.append(f'picking-marks.json: {m} is no item of db-picks.json')
    for k in sorted(fields - set(marked_json)):
        r.errors.append(f'picking-marks.json: no mark of config_picks {k}')
    for k in sorted(set(marked_json) - fields):
        r.errors.append(f'picking-marks.json: {marked_json[k]} is no field of config_picks')

    # the marks in the sources
    texts = {}
    for m, src in mk.items():
        path = os.path.join(os.path.expanduser(github), src)
        if not os.path.isfile(path):
            (r.errors if strict else r.skipped).append(f'{src} (of {m}) not found in {github}')
            continue
        if path not in texts:
            with open(path, encoding='utf-8') as f:
                texts[path] = f.read()
        n = len(re.findall(re.escape(m) + r'(?![\w.#:-])', texts[path]))
        if n != 1:
            r.errors.append(f'{src}: {m} is marked {n} times, expecting once')

    return r
