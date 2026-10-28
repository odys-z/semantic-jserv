"""
The single source of the commands list, printed by `synode-cli --help` (prompt.cli_help),
and written into the release package's README.md by jserv-album/tasks.py (package).

Keep this module import-free: tasks.py loads it by file path, without the synodepy3 package.
"""

doc_link = 'https://odys-z.github.io/products/portfolio/synode/setup.html'

commands = [
    # (Windows exe, installed command, python module); '-': not available
    ('setup-gui.exe',     'synode-gui',           'python -m synodepy3'),
    ('setup-cli.exe',     'synode-cli',           'python -m synodepy3.prompt'),
    ('upgrade.exe',       'synode-upgrade-srv',   'python -m synodepy3.upgrade_cli <package.zip | .tar.gz>'),
    ('uninstall-srv.exe', 'synode-uninstall-srv', 'python -m synodepy3.uninstall_cli'),
    ('-',                 'synode-avail-ports',   'python -m synodepy3.get_avail_ports [-v | -vv]'),
    ('-',                 'synode-start-web',     '-'),
]


def _table(head, rows, indent: str = '    ', gap: str = '   ') -> str:
    widths = [max(len(r[i]) for r in [head, *rows]) for i in range(len(head))]
    line = lambda r: indent + gap.join(c.ljust(w) for c, w in zip(r, widths)).rstrip()
    return '\n'.join([line(head), line(['-' * w for w in widths]), *map(line, rows)])


commands_table = 'Related commands:\n' + _table(('Windows exe', 'Installed command', 'Python module'), commands)


def release_readme() -> str:
    """
    :return: the README.md text for the release package
    """
    return f'''# Portfolio Synode

Documentation: {doc_link}

## Commands

```
{commands_table}
```

The installed commands come with bin/synode_py3-*.whl (`pip install bin/synode_py3-*.whl`).
Run `synode-cli --help` for the command line setup's help.
'''
