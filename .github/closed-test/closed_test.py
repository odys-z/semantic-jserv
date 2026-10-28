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
    python closed_test.py upgraded     <node> <upgrade-log>
    python closed_test.py resources-applied <node>
    python closed_test.py packed-bats  <node>
    python closed_test.py repack       <package> <out-package> <json>

repack: a copy of a package whose bin/resource.json is updated with <json> (top-level keys replaced),
e.g. '{"upgrade_from": {"min": "99.0.0"}}', for testing that the upgrade refuses it.
    python closed_test.py upgrade-tool <package.tar.gz | .zip>

service-names prints the Windows (winsrv.*) or Linux (linusrv.*) service names saved in settings.json.
h-photos: a docname with '%' is a LIKE pattern; each node needs at least --min rows (default 1).
upgrade-tool: the installed synode_py3 is the version of the package's bin/synode_py3-*.whl
(Linux: synode-upgrade-srv installs it, synodepy3 installer_api.promote_upgrade_whl()).

packed-bats: the package's winsrv/install-*.bat are stamped at build (jserv-album/tasks.py config,
the BEGIN line's mark), with the jar versions of its bin/resource.json; Windows packages since installing
stopped stamping them (synodepy3 winsrv.py), so a package built before fails here.

pick-release reads `gh api --paginate --slurp repos/<repo>/releases` and prints the newest matching
asset as "<updated>\t<tag>\t<name>" (nothing if none). The release it is in must hold at most one asset
of each kind (RELEASE_KINDS: synode Windows zip, synode Linux tar.gz, desktop zip, apk): with two of a
kind (versions), which one goes with the others is not known, so it fails, listing them.

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


RELEASE_KINDS = {
    'synode Windows zip': r'^synode-.*x64_windows.*\.zip$',
    'synode Linux tar.gz': r'^synode-.*x64_linux.*\.tar\.gz$',
    'desktop zip': r'^desktop-.*\.zip$',
    'apk': r'\.apk$',
}


def pick_release(releases, regex, tag=''):
    with open(releases, encoding='utf-8') as f:
        pages = json.load(f)
    assets = [(a['updated_at'], r['tag_name'], a['name'])
              for page in pages for r in page if not tag or r['tag_name'] == tag
              for a in r['assets'] if re.match(regex, a['name'])]
    if not assets:
        return
    pick = max(assets)
    # stderr: stdout is the pick, read by the caller ($(...)); the runner takes ::error:: from both
    names = [a['name'] for page in pages for r in page if r['tag_name'] == pick[1] for a in r['assets']]
    for kind, rx in RELEASE_KINDS.items():
        same = sorted(n for n in names if re.search(rx, n))
        if len(same) > 1:
            print(f'::error::release {pick[1]} has {len(same)} {kind}s: {", ".join(same)}. '
                  f'Keep only one of each kind (the same version) in a release.', file=sys.stderr)
            sys.exit(1)
    print('\t'.join(pick))


def upgraded(node, log):
    """
    After upgrade.exe / synode-upgrade-srv (synodepy3 commands.update_srv()): a backup-YYYYMMDD folder
    holds the node's data and settings, and the upgrade's log says each of them was copied back and
    verified ("Restored: <file>", no "is not the same as its backup"). The files themselves are not
    compared here: the restarted jserv rewrites settings.json (AppSettings.save_rt()), and an upgrade
    to new versions saves the new Windows service names into it.
    bin/resource.json in the backup is the installed package's manifest, kept as a reference only.
    """
    name = os.path.basename(node)
    backups = sorted(glob.glob(os.path.join(node, 'backup-*')))
    if not backups:
        fail(f'{name}: no backup-* folder, the upgrade did not run')
    b = backups[-1]
    backed = sorted(os.path.relpath(p, b) for p in glob.glob(os.path.join(b, '**', '*'), recursive=True)
                    if os.path.isfile(p))
    backed = [p.replace(os.sep, '/') for p in backed if p.replace(os.sep, '/') != 'bin/resource.json']
    for must in ('WEB-INF/settings.json', 'dictionary.json'):
        if not any(p.endswith(must) for p in backed):
            fail(f'{name}: {must} is not backed up in {b}')
    with open(log, encoding='utf-8', errors='replace') as f:
        lines = f.read().splitlines()
    if any('is not the same as its backup' in l for l in lines):
        fail(f'{name}: a file is not restored, see {os.path.basename(log)}')
    restored = [l.split('Restored:', 1)[1].strip().replace('\\', '/') for l in lines if 'Restored:' in l]
    for p in backed:
        if not any(r.endswith('/' + os.path.basename(p)) or r == os.path.basename(p) for r in restored):
            fail(f'{name}: {p} is backed up but not restored, see {os.path.basename(log)}')
    print(f'{name}: upgraded, backup {b}: {", ".join(backed)} restored')


def resources_applied(node):
    """
    After an upgrade: the services run the files of the new package, named in its bin/resource.json.
    Linux: the units (settings.json envars linusrv.*) start its jars. Windows: the services are named
    after its versions (Synode-<jar_ver>-<synid>, Synode.web-<web_ver>-<synid>) and the install bats
    have its jar versions.
    """
    import subprocess
    name = os.path.basename(node)
    js = os.path.join(node, 'bin', 'resource.json')
    if not os.path.isfile(js):
        fail(f'{name}: no bin/resource.json, the upgrade package was built before it')
    with open(js, encoding='utf-8') as f:
        res = json.load(f)
    with open(os.path.join(node, 'WEB-INF', 'settings.json'), encoding='utf-8') as f:
        env = json.load(f).get('envars') or {}
    with open(os.path.join(node, 'vol', 'dictionary.json'), encoding='utf-8') as f:
        synid = json.load(f)['config']['synid']
    # semanticshare PkgResources: {"type", "files": {item: path}, "versions": {...}, "upgrade_from": {...}}
    files = res.get('files') or {}
    v = res.get('versions') or {}
    if env.get('linusrv.synode'):
        for key, item in (('linusrv.synode', 'synode'), ('linusrv.web', 'html-web')):
            unit = subprocess.run(['systemctl', 'cat', env[key]], capture_output=True, text=True).stdout
            if not files.get(item):
                fail(f'{name}: bin/resource.json has no files.{item}')
            if files[item] not in unit:
                fail(f'{name}: {env[key]} does not start {files[item]}')
            print(f'{name}: {env[key]} starts {files[item]}')
    else:
        expect = {'winsrv.synode': f"Synode-{v.get('jar_ver')}-{synid}",
                  'winsrv.web': f"Synode.web-{v.get('web_ver')}-{synid}"}
        for k, n in expect.items():
            if env.get(k) != n:
                fail(f'{name}: envars {k} is {env.get(k)!r}, expected {n!r} (bin/resource.json versions)')
        for bat, ver in (('install-jserv-w.bat', v.get('jar_ver')), ('install-html-w.bat', v.get('html_srver'))):
            with open(os.path.join(node, 'winsrv', bat), encoding='utf-8', errors='replace') as f:
                if f'@set jar_ver={ver}' not in f.read():
                    fail(f'{name}: winsrv/{bat} is not stamped with jar_ver={ver}')
        print(f"{name}: services {expect['winsrv.synode']}, {expect['winsrv.web']}")


BUILD_MARK = 'stamped at build by jserv-album/tasks.py config'


def packed_bats(node):
    """
    The extracted package's winsrv/install-jserv-w.bat and install-html-w.bat: the BEGIN line has the
    build mark, and the jar versions are those of the package's bin/resource.json. Since installing no
    longer stamps the bats, a package without the mark would install services of the wrong jars.
    """
    name = os.path.basename(node)
    js = os.path.join(node, 'bin', 'resource.json')
    if not os.path.isfile(js):
        fail(f'{name}: no bin/resource.json in the package')
    with open(js, encoding='utf-8') as f:
        v = json.load(f).get('versions') or {}
    stamped = []
    for bat, ver in (('install-jserv-w.bat', v.get('jar_ver')), ('install-html-w.bat', v.get('html_srver'))):
        with open(os.path.join(node, 'winsrv', bat), encoding='utf-8', errors='replace') as f:
            text = f.read()
        if BUILD_MARK not in text:
            fail(f'{name}: winsrv/{bat} has no build mark ("{BUILD_MARK}"): the package was built '
                 f'before the bats were stamped at build. Rebuild it (inv build / make).')
        got = re.search(r'^@set jar_ver=([0-9.]+)', text, re.M)
        got = got.group(1) if got else None
        if got != ver:
            fail(f'{name}: winsrv/{bat} has jar_ver={got}, bin/resource.json {ver}: built without '
                 f'`inv config` for this version?')
        stamped.append(f'{bat} jar_ver={got}')
    summary(f'### Build mark: winsrv bats {BUILD_MARK} ({", ".join(stamped)})')


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


def repack(package, out, patch):
    """A copy of a package (zip or tar.gz) with its bin/resource.json updated by the json patch."""
    import io
    import tarfile
    import zipfile
    upd = json.loads(patch)

    def patched(data: bytes) -> bytes:
        m = json.loads(data)
        m.update(upd)
        return (json.dumps(m, indent=2) + '\n').encode('utf-8')

    found = False
    if zipfile.is_zipfile(package):
        with zipfile.ZipFile(package) as zi, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
            for i in zi.infolist():
                data = zi.read(i)
                if i.filename.removeprefix('./') == 'bin/resource.json':
                    data, found = patched(data), True
                zo.writestr(i, data)
    else:
        with tarfile.open(package) as ti, tarfile.open(out, 'w:gz') as to:
            for m in ti.getmembers():
                f = ti.extractfile(m) if m.isfile() else None
                if f and m.name.removeprefix('./') == 'bin/resource.json':
                    data = patched(f.read())
                    m.size, f, found = len(data), io.BytesIO(data), True
                to.addfile(m, f)
    if not found:
        os.remove(out)
        fail(f'{os.path.basename(package)} has no bin/resource.json')
    print(f'{out}: bin/resource.json updated with {patch}')


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
        upgraded(a[0], a[1])
    elif cmd == 'resources-applied':
        resources_applied(a[0])
    elif cmd == 'packed-bats':
        packed_bats(a[0])
    elif cmd == 'repack':
        repack(*a)
    elif cmd == 'upgrade-tool':
        upgrade_tool(a[0])
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
