#!/usr/bin/env python3
"""
Feed an answer file into an interactive command (default: synode-cli).

    python feed_answers.py answers/hub.txt --cwd e2e/hub --log e2e/logs/hub-cli.log

Answer file, one answer per line, in the order the prompts appear:

    # a line starting with '#' is a comment; blank lines are skipped
    1                       # menus take the option number, 1-based
    <enter>                 # an empty answer: accept the prompt's default
    <clear>${CENTRAL_URL}   # replace the pre-filled default (sends Ctrl-U first)
    zsu                     # plain text; a ' #' starts a trailing comment

    ${NAME} is taken from the environment; a missing variable is an error.
    Write '\\#' for a literal '#' in an answer.

Why not just `synode-cli < file`: prompt_toolkit can't read a regular file as stdin (it
crashes), menus only accept CR as Enter, and typed text is appended to a pre-filled default
instead of replacing it. This script pipes the answers, ends each with CR and handles <clear>.

Exit status is the command's own. When the answers run out early, the next prompt gets
end-of-input and the command fails, so a misaligned answer file fails the run.
"""

import argparse
import os
import re
import subprocess
import sys
import threading

CTRL_U = '\x15'
ENTER = '\r'
_var = re.compile(r'\$\{([A-Za-z_][A-Za-z0-9_]*)\}')
_secret_name = re.compile(r'(TOKEN|PSWD|PASSWORD|SECRET)', re.I)


def parse(path: str):
    """
    :return: (answers, secrets): the strings to type, and the values to mask in the log
    """
    answers, secrets = [], set()
    with open(path, encoding='utf-8') as f:
        for no, raw in enumerate(f, 1):
            line = raw.rstrip('\r\n')
            if not line.strip() or line.lstrip().startswith('#'):
                continue

            # trailing comment: whitespace + '#', unless escaped
            value = re.split(r'(?<!\\)\s#', line, maxsplit=1)[0].strip().replace('\\#', '#')

            def expand(m):
                name = m.group(1)
                if name not in os.environ:
                    sys.exit(f'{path}:{no}: environment variable {name} is not set')
                v = os.environ[name]
                if _secret_name.search(name) and v:
                    secrets.add(v)
                return v

            clear = value.startswith('<clear>')
            if clear:
                value = value[len('<clear>'):]
            if value == '<enter>':
                value = ''
            value = _var.sub(expand, value)
            answers.append((CTRL_U if clear else '') + value)
    return answers, secrets


def show(answer: str, secrets: set) -> str:
    s = answer.replace(CTRL_U, '<clear>') or '<enter>'
    for v in secrets:
        s = s.replace(v, '***')
    return s


def main():
    ap = argparse.ArgumentParser(description='Feed an answer file into an interactive command.')
    ap.add_argument('answers', help='answer file')
    ap.add_argument('--cwd', default='.', help='working directory of the command')
    ap.add_argument('--log', help='also write the command output to this file')
    ap.add_argument('--timeout', type=int, default=1200, help='seconds, default 1200 (JRE download included)')
    ap.add_argument('--dry-run', action='store_true', help='print the parsed answers and quit')
    ap.add_argument('command', nargs='*', default=['synode-cli'], help='default: synode-cli')
    a = ap.parse_args()

    answers, secrets = parse(a.answers)

    print(f'[feed_answers] {len(answers)} answers from {a.answers}:')
    for i, ans in enumerate(answers, 1):
        print(f'  {i:2d}. {show(ans, secrets)}')
    if a.dry_run:
        return 0

    log = open(a.log, 'w', encoding='utf-8') if a.log else None

    def mask(text: str) -> str:
        for v in secrets:
            text = text.replace(v, '***')
        return text

    p = subprocess.Popen(a.command, cwd=a.cwd, stdin=subprocess.PIPE,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    def pump():
        # line by line, so a secret is never split between two writes
        for raw in iter(p.stdout.readline, b''):
            text = mask(raw.decode('utf-8', errors='replace'))
            sys.stdout.write(text)
            sys.stdout.flush()
            if log:
                log.write(text)
    reader = threading.Thread(target=pump, daemon=True)
    reader.start()

    try:
        # all at once: prompt_toolkit keeps type-ahead for the later prompts
        p.stdin.write(''.join(ans + ENTER for ans in answers).encode('utf-8'))
        p.stdin.close()
    except BrokenPipeError:
        pass  # the command quit before reading everything; its exit status tells why

    try:
        code = p.wait(timeout=a.timeout)
    except subprocess.TimeoutExpired:
        p.kill()
        code = 124
        print(f'\n[feed_answers] timeout after {a.timeout} s', file=sys.stderr)

    reader.join(timeout=5)
    if log:
        log.close()
    print(f'\n[feed_answers] {" ".join(a.command)} exited with {code}')
    return code


if __name__ == '__main__':
    sys.exit(main())
