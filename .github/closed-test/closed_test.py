#!/usr/bin/env python3
"""
Checks of the closed tests, without the sqlite3 / jq command line tools (not on Windows runners).
Used by .github/workflows/synodes-windows-closed-test.yml.

    python closed_test.py central-pswd <node>/WEB-INF/settings.json <pswd>
    python closed_test.py installed    <node> <domain> <synid>
    python closed_test.py service-names <node>
    python closed_test.py admin-pswd   <node>
    python closed_test.py before       <central-db> <org> <domain> <others-file>
    python closed_test.py report       <central-db> <org> <domain> <hub-id> <others-file> [--summary <md>]
    python closed_test.py h-photos     <docname> <timeout-s> <node-vol> [<node-vol> ...] [--summary <md>]
    python closed_test.py pick-release <releases.json> <asset-regex> [<tag>]
    python closed_test.py upgraded     <node>

pick-release reads `gh api --paginate --slurp repos/<repo>/releases` and prints the newest matching
asset as "<updated>\t<tag>\t<name>" (nothing if none).

Exit status 1 with an ::error:: line when a check fails.
"""

import glob
import json
import os
import re
import sqlite3
import sys
import time


def fail(msg: str):
    print(f'::error::{msg}')
    sys.exit(1)


def ro(db: str) -> sqlite3.Connection:
    return sqlite3.connect(f'file:{db}?mode=ro', uri=True)


def markdown(con: sqlite3.Connection, sql: str, args=()) -> str:
    cur = con.execute(sql, args)
    cols = [c[0] for c in cur.description]
    rows = cur.fetchall()
    lines = ['| ' + ' | '.join(cols) + ' |', '|' + '---|' * len(cols)]
    lines += ['| ' + ' | '.join('' if v is None else str(v) for v in r) + ' |' for r in rows]
    return '\n'.join(lines) + '\n'


def summary(text: str, path: str = None):
    print(text)
    path = path or os.environ.get('GITHUB_STEP_SUMMARY')
    if path:
        with open(path, 'a', encoding='utf-8') as f:
            f.write(text + '\n')


def opt(args: list, name: str):
    if name in args:
        i = args.index(name)
        v = args[i + 1]
        del args[i:i + 2]
        return v
    return None


def others(con, org, domain) -> str:
    rows = con.execute('select domx, sid, mode, stat, jserv from c_synodes '
                       'where orgid = ? and domx <> ? order by domx, sid', (org, domain)).fetchall()
    return '\n'.join('|'.join('' if v is None else str(v) for v in r) for r in rows) + '\n'


def central_pswd(settings, pswd):
    with open(settings, encoding='utf-8') as f:
        s = json.load(f)
    s['centralPswd'] = pswd
    with open(settings, 'w', encoding='utf-8') as f:
        json.dump(s, f, indent=2)


def installed(node, domain, synid):
    """The answers select menu options by number: a central listing in another order selects others."""
    path = os.path.join(node, 'vol', 'dictionary.json')
    if not os.path.isfile(path):
        fail(f'{node}: no {path}, setup did not finish')
    with open(path, encoding='utf-8') as f:
        cfg = json.load(f)['config']
    got = f"{cfg.get('domain')} {cfg.get('synid')}"
    if got != f'{domain} {synid}':
        fail(f"{os.path.basename(node)}: installed as '{got}', expected '{domain} {synid}'. "
             f"Did the central's menus change order? See the cli log and the answer file.")
    print(f'{os.path.basename(node)}: {got}')


def service_names(node):
    with open(os.path.join(node, 'WEB-INF', 'settings.json'), encoding='utf-8') as f:
        env = json.load(f).get('envars') or {}
    print(env.get('winsrv.synode', ''), env.get('winsrv.web', ''))


def admin_pswd(node):
    with open(os.path.join(node, 'vol', 'dictionary.json'), encoding='utf-8') as f:
        users = json.load(f).get('synusers') or []
    for u in users:
        if u.get('userId') == 'admin':
            print(u.get('pswd'))
            return
    fail(f'{node}: no user admin in vol/dictionary.json')


def before(db, org, domain, others_file):
    with ro(db) as con:
        n = con.execute('select count(*) from c_syndomx where orgid = ? and domid = ?', (org, domain)).fetchone()[0]
        if n:
            fail(f"the central release already has domain {domain}: its creation can't be tested")
        with open(others_file, 'w', encoding='utf-8') as f:
            f.write(others(con, org, domain))


def report(db, org, domain, hub, others_file, md=None):
    failed = []
    with ro(db) as con:
        q = 'select count(*) from c_synodes where orgid = ? and domx = ? and stat = ?'
        for _ in range(30):   # a synode reports its state shortly after its jserv starts
            if con.execute(q, (org, domain, 'c')).fetchone()[0] == 0:
                break
            time.sleep(2)

        rows = con.execute('select sid, stat from c_synodes where orgid = ? and domx = ? order by sid',
                           (org, domain)).fetchall()
        stats = ' '.join(f'{sid}={stat}' for sid, stat in rows)
        expect = f'{domain}-1=p {domain}-2=p {hub}=h'
        if stats != expect:
            failed.append(f"synodes of {domain}: '{stats}', expected '{expect}'")

        with open(others_file, encoding='utf-8') as f:
            if f.read() != others(con, org, domain):
                failed.append('the run changed synodes of other domains')

        # only the test org: the snapshot also holds other orgs, and this repository is public
        summary(f'### Central c_syndomx (domains of {org})\n'
                + markdown(con, 'select * from c_syndomx where orgid = ?', (org,))
                + f'\n### Central c_synodes ({org})\n'
                + markdown(con, 'select market, orgid, domx, sid, mode, stat, jserv, optime '
                                'from c_synodes where orgid = ?', (org,)), md)
    for m in failed:
        print(f'::error::{m}')
    sys.exit(1 if failed else 0)


def h_photos(docname, timeout, vols, md=None):
    """Wait for the pushed document in h_photos of every node (peers synchronize every few seconds)."""
    q = 'select pid, docname, device, filesize, shareflag from h_photos where docname = ?'
    missing = []
    deadline = time.monotonic() + timeout
    text = f'### h_photos ({docname})\n'
    for vol in vols:
        db = os.path.join(vol, 'doc-jserv.db')
        n = 0
        while True:
            try:
                with ro(db) as con:
                    n = con.execute('select count(*) from h_photos where docname = ?', (docname,)).fetchone()[0]
            except sqlite3.Error as e:
                print(f'{db}: {e}')
            if n or time.monotonic() > deadline:
                break
            time.sleep(5)
        print(f'{vol}: {n} row(s)')
        if not n:
            missing.append(vol)
        with ro(db) as con:
            text += f'\n#### {vol}\n' + markdown(con, q, (docname,))
    summary(text, md)
    for v in missing:
        print(f'::error::{v}: the pushed document is not in h_photos')
    sys.exit(1 if missing else 0)


def pick_release(releases, regex, tag=''):
    with open(releases, encoding='utf-8') as f:
        pages = json.load(f)
    assets = [(a['updated_at'], r['tag_name'], a['name'])
              for page in pages for r in page if not tag or r['tag_name'] == tag
              for a in r['assets'] if re.match(regex, a['name'])]
    if assets:
        print('\t'.join(max(assets)))


def upgraded(node):
    """
    After upgrade.exe (synodepy3 commands.update_srv()): a backup-YYYYMMDD folder with the node's data
    and settings, which are restored (the same as their backups).
    """
    backups = sorted(glob.glob(os.path.join(node, 'backup-*')))
    if not backups:
        fail(f'{os.path.basename(node)}: no backup-* folder, the upgrade did not run')
    b = backups[-1]
    with open(os.path.join(node, 'WEB-INF', 'settings.json'), encoding='utf-8') as f:
        vol = json.load(f).get('volume') or 'vol'
    if not os.path.isabs(vol):
        vol = os.path.join(node, vol)   # relative to the installation folder, upgrade.exe's working folder
    pairs = [(os.path.join(node, 'WEB-INF', 'settings.json'), os.path.join(b, 'WEB-INF', 'settings.json')),
             (os.path.join(vol, 'dictionary.json'), os.path.join(b, os.path.basename(os.path.normpath(vol)), 'dictionary.json'))]
    for cur, bak in pairs:
        if not os.path.isfile(bak):
            fail(f'{os.path.basename(node)}: {bak} is not backed up')
        with open(cur, 'rb') as c, open(bak, 'rb') as k:
            if c.read() != k.read():
                fail(f'{os.path.basename(node)}: {cur} is not restored from {bak}')
    print(f'{os.path.basename(node)}: upgraded, backup {b}')


def main():
    args = sys.argv[1:]
    md = opt(args, '--summary')
    if not args:
        sys.exit(__doc__)
    cmd, a = args[0], args[1:]
    if cmd == 'central-pswd':
        central_pswd(*a)
    elif cmd == 'installed':
        installed(*a)
    elif cmd == 'service-names':
        service_names(*a)
    elif cmd == 'admin-pswd':
        admin_pswd(*a)
    elif cmd == 'before':
        before(*a)
    elif cmd == 'report':
        report(*a, md=md)
    elif cmd == 'h-photos':
        h_photos(a[0], float(a[1]), a[2:], md=md)
    elif cmd == 'pick-release':
        pick_release(*a)
    elif cmd == 'upgraded':
        upgraded(a[0])
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
