#!/usr/bin/env python3
"""
Checks of the closed tests, without the sqlite3 / jq command line tools (not on Windows runners).
Used by the synodes-{windows,linux}-{closed,upgrade}-test.yml workflows.

    python closed_test.py central-pswd <node>/WEB-INF/settings.json <pswd>
    python closed_test.py installed    <node> <domain> <synid>
    python closed_test.py service-names <node>
    python closed_test.py admin-pswd   <node>
    python closed_test.py before       <central-db> <org> <domain> <others-file>
    python closed_test.py report       <central-db> <org> <domain> <hub-id> <others-file> [--summary <md>]
    python closed_test.py h-photos     <docname> <timeout-s> <node-vol> [<node-vol> ...] [--summary <md>] [--min <n>]
    python closed_test.py pick-release <releases.json> <asset-regex> [<tag>]
    python closed_test.py upgraded     <node>
    python closed_test.py upgrade-tool <package.tar.gz | .zip>

service-names prints the Windows (winsrv.*) or Linux (linusrv.*) service names saved in settings.json.
h-photos: a docname with '%' is a LIKE pattern; each node needs at least --min rows (default 1).
upgrade-tool: the installed synode_py3 is the version of the package's bin/synode_py3-*.whl
(Linux: synode-upgrade-srv installs it, synodepy3 installer_api.promote_upgrade_whl()).

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
    print(env.get('winsrv.synode') or env.get('linusrv.synode') or '',
          env.get('winsrv.web') or env.get('linusrv.web') or '')


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


def h_photos(docname, timeout, vols, md=None, least=1):
    """
    Wait for the pushed document in h_photos of every node (peers synchronize every few seconds).
    :param docname: the name, or a LIKE pattern if it has '%'
    :param least: rows each node must have
    """
    op = 'like' if '%' in docname else '='
    q = f'select pid, docname, device, filesize, shareflag from h_photos where docname {op} ?'
    missing = []
    deadline = time.monotonic() + timeout
    text = f'### h_photos ({docname})\n'
    for vol in vols:
        db = os.path.join(vol, 'doc-jserv.db')
        n = 0
        while True:
            try:
                with ro(db) as con:
                    n = con.execute(f'select count(*) from h_photos where docname {op} ?', (docname,)).fetchone()[0]
            except sqlite3.Error as e:
                print(f'{db}: {e}')
            if n >= least or time.monotonic() > deadline:
                break
            time.sleep(5)
        print(f'{vol}: {n} row(s)')
        if n < least:
            missing.append(vol)
        with ro(db) as con:
            text += f'\n#### {vol}\n' + markdown(con, q, (docname,))
    summary(text, md)
    for v in missing:
        print(f'::error::{v}: the pushed document is not in h_photos' + (f' ({least} rows expected)' if least > 1 else ''))
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
        # as json: on Linux, reinstalling the services saves their names into settings.json again
        with open(cur, encoding='utf-8') as c, open(bak, encoding='utf-8') as k:
            if json.load(c) != json.load(k):
                fail(f'{os.path.basename(node)}: {cur} is not restored from {bak}')
    print(f'{os.path.basename(node)}: upgraded, backup {b}')


def upgrade_tool(package):
    """The installed synode_py3 is the version of the package's wheel (its file name, as pip reads it)."""
    import tarfile
    import zipfile
    from importlib.metadata import version, PackageNotFoundError
    if package.endswith('.zip'):
        with zipfile.ZipFile(package) as z:
            names = z.namelist()
    else:
        with tarfile.open(package) as t:
            names = t.getnames()
    whls = [os.path.basename(n) for n in names
            if re.match(r'^(\./)?bin/synode_py3-[^/]+\.whl$', n)]
    if len(whls) != 1:
        fail(f'{os.path.basename(package)}: expected one bin/synode_py3-*.whl, found {whls}')
    expect = whls[0].split('-')[1]
    try:
        got = version('synode_py3')
    except PackageNotFoundError:
        fail('synode_py3 is not installed')
    if got != expect:
        fail(f'the installed synode_py3 is {got}, the package has {whls[0]} (the upgrade did not install it?)')
    print(f'synode_py3 {got}, the same as {whls[0]}')


def main():
    args = sys.argv[1:]
    md = opt(args, '--summary')
    least = int(opt(args, '--min') or 1)
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
        h_photos(a[0], float(a[1]), a[2:], md=md, least=least)
    elif cmd == 'pick-release':
        pick_release(*a)
    elif cmd == 'upgraded':
        upgraded(a[0])
    elif cmd == 'upgrade-tool':
        upgrade_tool(a[0])
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
