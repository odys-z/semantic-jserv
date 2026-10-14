#!/usr/bin/env python3
"""
Feed an answer file into an interactive command (default: synode-cli).

    python feed_answers.py answers/linux/hub.txt --cwd e2e/hub --log e2e/logs/hub-cli.log

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

Windows (--console, the default there): prompt_toolkit ignores a piped stdin and reads the
console itself (CONIN$), so the command runs in a pseudo console (ConPTY, package pywinpty)
and the answers are typed into it, one at a time (on POSIX, --console uses a pty, package
ptyprocess, e.g. for trying the answers):
- each answer waits for the command's output to settle (--settle seconds without output), so
  that it's typed into its own prompt;
- the text and its Enter are sent separately: prompt_toolkit on Windows takes text and Enter
  read together as a paste, and inserts the Enter instead of accepting the input.

Exit status is the command's own. When the answers run out early, the next prompt gets
end-of-input and the command fails, so a misaligned answer file fails the run.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import threading
import time

CTRL_U = '\x15'
ENTER = '\r'
_var = re.compile(r'\$\{([A-Za-z_][A-Za-z0-9_]*)\}')
_secret_name = re.compile(r'(TOKEN|PSWD|PASSWORD|SECRET)', re.I)
_ansi = re.compile(r'\x1b(\[[0-9;?]*[ -/]*[@-~]|\][^\x07\x1b]*(\x07|\x1b\\)|[@-Z\\-_])')


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


class Output:
    """
    The command's output: echoed to stdout and the log, with secrets masked, and timed for --console.
    """
    def __init__(self, secrets: set, logpath: str = None, strip_ansi: bool = False):
        self.secrets = secrets
        self.log = open(logpath, 'w', encoding='utf-8') if logpath else None
        self.strip_ansi = strip_ansi
        self.last = time.monotonic()   # time of the last output
        self.count = 0                 # number of outputs so far

    def write(self, text: str):
        # counted first: the output arrived, whatever happens to its echo
        self.last = time.monotonic()
        self.count += 1
        if self.strip_ansi:
            text = _ansi.sub('', text)
        for v in self.secrets:
            text = text.replace(v, '***')
        try:
            sys.stdout.write(text)
            sys.stdout.flush()
        except (UnicodeError, OSError) as e:   # never let the echo stop the reader
            sys.stdout.write(f'\n[feed_answers] (output not shown: {e})\n')
        if self.log:
            self.log.write(text)
            self.log.flush()

    def close(self):
        if self.log:
            self.log.close()


def run_pipe(command, cwd, answers, out: Output, timeout: int) -> int:
    p = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    def pump():
        # line by line, so a secret is never split between two writes
        for raw in iter(p.stdout.readline, b''):
            out.write(raw.decode('utf-8', errors='replace'))
    reader = threading.Thread(target=pump, daemon=True)
    reader.start()

    try:
        # all at once: prompt_toolkit keeps type-ahead for the later prompts
        p.stdin.write(''.join(ans + ENTER for ans in answers).encode('utf-8'))
        p.stdin.close()
    except BrokenPipeError:
        pass  # the command quit before reading everything; its exit status tells why

    try:
        code = p.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        p.kill()
        code = 124
        print(f'\n[feed_answers] timeout after {timeout} s', file=sys.stderr)

    reader.join(timeout=5)
    return code


def run_console(command, cwd, answers, out: Output, timeout: int, settle: float, secrets: set) -> int:
    try:
        from winpty import PtyProcess   # Windows: pip install pywinpty
    except ImportError:
        from ptyprocess import PtyProcessUnicode as PtyProcess   # POSIX: pip install ptyprocess

    # CreateProcess doesn't look for the program in the child's working directory
    exe = command[0]
    if not os.path.isabs(exe) and os.path.isfile(os.path.join(cwd, exe)):
        exe = os.path.abspath(os.path.join(cwd, exe))
    else:
        exe = shutil.which(exe) or exe
    p = PtyProcess.spawn([exe] + list(command[1:]), cwd=cwd, dimensions=(50, 250))

    def pump():
        while True:
            try:
                text = p.read(4096)
            except EOFError:
                break
            if text:
                try:
                    out.write(text)
                except Exception as e:   # keep reading: a dead reader stalls the command and the answers
                    print(f'\n[feed_answers] output error: {e}', file=sys.stderr)
    reader = threading.Thread(target=pump, daemon=True)
    reader.start()

    deadline = time.monotonic() + timeout

    def wait_prompt(since: int) -> bool:
        """
        Wait for new output after the previous answer, then for no output for `settle` seconds.
        :return: False if the command has quit or the time is up
        """
        while time.monotonic() < deadline and p.isalive():
            if out.count > since and time.monotonic() - out.last >= settle:
                return True
            time.sleep(0.1)
        return False

    since = 0
    for i, ans in enumerate(answers, 1):
        if not wait_prompt(since):
            print(f'\n[feed_answers] the command quit or timed out before answer {i}', file=sys.stderr)
            break
        print(f'\n[feed_answers] typing {i}: {show(ans, secrets)}', flush=True)
        since = out.count
        if ans.startswith(CTRL_U):
            p.write(CTRL_U)
            ans = ans[1:]
            time.sleep(0.3)
        if ans:
            p.write(ans)
            time.sleep(0.5)   # not read together with the Enter, which would be a paste
        p.write(ENTER)

    while p.isalive() and time.monotonic() < deadline:
        time.sleep(0.2)
    if p.isalive():
        p.terminate(force=True)
        print(f'\n[feed_answers] timeout after {timeout} s', file=sys.stderr)
        code = 124
    else:
        code = p.exitstatus if p.exitstatus is not None else 1

    reader.join(timeout=5)
    return code


def main():
    # Windows runners' stdout is cp1252: synode-cli prints e.g. '✅' (U+2705) in the synode menu
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding='utf-8', errors='replace')
        except (AttributeError, ValueError):
            pass

    ap = argparse.ArgumentParser(description='Feed an answer file into an interactive command.')
    ap.add_argument('answers', help='answer file')
    ap.add_argument('--cwd', default='.', help='working directory of the command')
    ap.add_argument('--log', help='also write the command output to this file')
    ap.add_argument('--timeout', type=int, default=1200, help='seconds, default 1200 (JRE download included)')
    ap.add_argument('--console', action=argparse.BooleanOptionalAction, default=sys.platform == 'win32',
                    help='type the answers into a pseudo console (default on Windows), instead of piping them')
    ap.add_argument('--settle', type=float, default=3.0,
                    help='--console: seconds without output before typing the next answer, default 3')
    ap.add_argument('--dry-run', action='store_true', help='print the parsed answers and quit')
    ap.add_argument('command', nargs='*', default=['synode-cli'], help='default: synode-cli')
    a = ap.parse_args()

    answers, secrets = parse(a.answers)

    print(f'[feed_answers] {len(answers)} answers from {a.answers}:')
    for i, ans in enumerate(answers, 1):
        print(f'  {i:2d}. {show(ans, secrets)}')
    if a.dry_run:
        return 0

    out = Output(secrets, a.log, strip_ansi=a.console)
    try:
        if a.console:
            code = run_console(a.command, a.cwd, answers, out, a.timeout, a.settle, secrets)
        else:
            code = run_pipe(a.command, a.cwd, answers, out, a.timeout)
    finally:
        out.close()

    print(f'\n[feed_answers] {" ".join(a.command)} exited with {code}')
    # Windows reports e.g. 4294967295 (-1); an exit status must be 0..255, and 0 only for success
    return code if 0 <= code <= 255 else 1


if __name__ == '__main__':
    sys.exit(main())
