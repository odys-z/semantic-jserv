"""
Report available / occupied TCP port ranges on this host (Linux, Windows and macOS).

Usage:
    python3 get-avail-ports.py          # ranges only
    python3 get-avail-ports.py -v       # also list each occupied port with the owning program's path
    python3 get-avail-ports.py -vv      # ... and the program's full command line (e.g. which jar java is running)

A port held by a synode or syn-web service shows its service name (from WEB-INF/settings.json envars);
with -v, also its install folder. This needs psutil, anson.py3 and semantics.py3 (all synode.py3 dependencies);
without -v they are optional: without them only the ranges are shown.

Owners of other users' / system sockets are only visible with root (sudo) on Linux and macOS,
or from an Administrator prompt on Windows. On macOS without sudo, only your own programs' ports
are shown with owners.
"""
import argparse
import os
import socket
import sys

IS_WIN = os.name == 'nt'
IS_MAC = sys.platform == 'darwin'
ELEVATE = "run as Administrator" if IS_WIN else "try sudo"

WSAEACCES = 10013
'''
Windows: bind refused because the port is in an excluded (reserved) range, e.g. by Hyper-V / WSL / Docker.
'''


def check_port(port, host="127.0.0.1"):
    """
    :return: (free, error code of the failed bind or None)
    """
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        if IS_WIN:
            # On Windows SO_REUSEADDR lets a bind steal a port that is in use, so it would report
            # every port as free. SO_EXCLUSIVEADDRUSE makes the bind fail if anyone holds the port.
            s.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        else:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind((host, port))
            return True, None
        except OSError as e:
            return False, getattr(e, 'winerror', None) or e.errno


owners_partial = False
'''
True if the system-wide socket table was denied and only this user's processes' sockets were read (macOS without root).
'''


def _per_process_sockets():
    """(laddr, status, pid) of the TCP sockets of every process this user may inspect."""
    import psutil

    for p in psutil.process_iter():
        try:
            # Process.connections() was renamed net_connections() in psutil 6.0
            conns = p.net_connections(kind='tcp') if hasattr(p, 'net_connections') else p.connections(kind='tcp')
        except (psutil.AccessDenied, psutil.NoSuchProcess, psutil.ZombieProcess):
            continue
        for c in conns:
            yield c.laddr, c.status, p.pid


def tcp_port_owners():
    """
    Map port -> {pid: TCP status} for every TCP socket bound on this host.
    TIME_WAIT sockets are skipped: they have no owner (pid 0 on Windows), and don't block a new listener.
    """
    global owners_partial
    import psutil

    try:
        sockets = [(c.laddr, c.status, c.pid) for c in psutil.net_connections(kind='tcp')]
    except psutil.AccessDenied:
        # macOS: the system-wide table needs root.
        owners_partial = True
        sockets = _per_process_sockets()

    owners = {}
    for laddr, status, pid in sockets:
        if not laddr or status == psutil.CONN_TIME_WAIT:
            continue
        pids = owners.setdefault(laddr.port, {})
        # Keep LISTEN over any other status of the same process.
        if pids.get(pid) != psutil.CONN_LISTEN:
            pids[pid] = status
    return owners


def is_listening(owners, port):
    import psutil
    return any(st == psutil.CONN_LISTEN for st in owners.get(port, {}).values())


def describe_process(pid, cmdline=False):
    import psutil

    if pid is None:
        return f"(owner not visible, {ELEVATE})"
    try:
        p = psutil.Process(pid)
        try:
            path = p.exe() or p.name()
        except psutil.AccessDenied:
            # Windows services / other users' processes: the name is still readable.
            return f"{p.name()} (path hidden, {ELEVATE})"
        if cmdline:
            try:
                args = p.cmdline()[1:]
                if args:
                    path = f"{path} {' '.join(args)}"
            except psutil.AccessDenied:
                path = f"{path} (arguments hidden, {ELEVATE})"
        return path
    except psutil.AccessDenied:
        return f"(access denied, {ELEVATE})"
    except psutil.NoSuchProcess:
        return "(process exited)"


# ---------------------------------------------------------------------------
# Synode services: <install>/WEB-INF/settings.json, with
#   "port" / "webport"   -> which service owns the port (synode / web)
#   envars[winsrv.synode | winsrv.web | linusrv.synode | linusrv.web] -> the service names
# ---------------------------------------------------------------------------
SETTINGS_REL = os.path.join("WEB-INF", "settings.json")
ROLE_KEYS = {"synode": ("winsrv.synode", "linusrv.synode"),
             "web": ("winsrv.web", "linusrv.web")}

_os_services = None
_synode_cache = {}


def os_service_of(pid):
    """Name of the OS service running as pid: the Windows service, the launchd job on macOS, or the systemd unit on Linux."""
    global _os_services
    if pid is None:
        return None
    if IS_WIN:
        import psutil
        if _os_services is None:
            _os_services = {}
            try:
                for srv in psutil.win_service_iter():
                    try:
                        spid = srv.pid()
                        if spid:
                            _os_services[spid] = srv.name()
                    except Exception:
                        pass
            except Exception:
                pass
        return _os_services.get(pid)
    if IS_MAC:
        if _os_services is None:
            import subprocess
            try:
                # Without root, only this user's jobs (LaunchAgents); with sudo, the system's (LaunchDaemons).
                out = subprocess.run(["launchctl", "list"], capture_output=True, text=True, timeout=10).stdout
            except (OSError, subprocess.SubprocessError):
                out = ""
            _os_services = parse_launchctl_list(out)
        return _os_services.get(pid)
    try:
        with open(f"/proc/{pid}/cgroup") as f:
            for line in f:
                for part in line.strip().split("/"):
                    if part.endswith(".service"):
                        return part
    except OSError:
        pass
    return None


def parse_launchctl_list(out):
    """
    'launchctl list' output -> {pid: label}. Lines are "PID<tab>Status<tab>Label"; PID is '-' for jobs not running.
    """
    services = {}
    for line in out.splitlines()[1:]:
        cols = line.split(None, 2)
        if len(cols) == 3 and cols[0].isdigit():
            services[int(cols[0])] = cols[2].strip()
    return services


def find_install_root(p):
    """The folder holding WEB-INF/settings.json, searched from the process's cwd, exe and any *.jar argument."""
    import psutil

    starts = []
    try:
        starts.append(p.cwd())
    except (psutil.AccessDenied, psutil.NoSuchProcess, OSError):
        pass
    try:
        starts.append(os.path.dirname(p.exe()))      # <install>/winsrv/portfolio-ia64.exe, <install>/jre17/bin/java
    except (psutil.AccessDenied, psutil.NoSuchProcess, OSError):
        pass
    try:
        starts += [os.path.dirname(a) for a in p.cmdline() if a.lower().endswith(".jar")]  # <install>/bin/x.jar
    except (psutil.AccessDenied, psutil.NoSuchProcess, OSError):
        pass

    for d in starts:
        if not d:
            continue
        d = os.path.abspath(d)
        for _ in range(4):
            if os.path.isfile(os.path.join(d, SETTINGS_REL)):
                return d
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return None


_anson_missing = False


def _load_anson(path, cls):
    """
    Load a synode config file as its Anson type, resolved by the file's "type" field, e.g.
    settings.json -> AppSettings, html-service.json -> WebConfig.
    :return: the object, or None if the file is missing, unreadable, or not of type cls.
    """
    import contextlib
    import io
    from anson.io.odysz.anson import Anson

    if not os.path.isfile(path):
        return None
    try:
        # Anson warns on stderr about fields the class doesn't declare, e.g. html-service.json "comments".
        with contextlib.redirect_stderr(io.StringIO()):
            obj = Anson.from_file(path)
    except Exception:
        return None
    return obj if isinstance(obj, cls) else None


def synode_lookup(pid, port, status="LISTEN"):
    """
    :return: dict if pid is a synode's process, else None, with
        root, cwd: install folder, process working folder;
        role: 'synode' | 'web' | None, from the running service's name, else from the configured ports;
        name: the service name (running, else configured in settings.json envars);
        running: the OS service actually running as pid, None if it was started by hand;
        notes: disagreements between the service, settings.json and html-service.json;
        status: the socket's TCP status. Only a listening socket's port is compared with the configured ports:
        any other is a connection (usually outgoing, from a temporary port), not where the service listens.
    """
    global _anson_missing
    import psutil

    listening = status == "LISTEN"
    key = (pid, port, status)
    if key in _synode_cache:
        return _synode_cache[key]
    if _anson_missing:
        return None

    try:
        from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings
        from semanticshare.io.oz.srv import WebConfig
    except ImportError:
        _anson_missing = True
        print("(anson.py3 / semantics.py3 not installed: synode services are not identified.)", file=sys.stderr)
        return None

    found = None
    try:
        p = psutil.Process(pid)
        root = find_install_root(p)
        settings = _load_anson(os.path.join(root, SETTINGS_REL), AppSettings) if root is not None else None
        if settings is not None:
            envars = settings.envars or {}
            configured = {role: next((envars[k] for k in keys if envars.get(k)), None)
                          for role, keys in ROLE_KEYS.items()}
            # The web jar listens on WEB-INF/html-service.json's port, written from settings.json webport at install.
            htmlsrv = _load_anson(os.path.join(root, "WEB-INF", "html-service.json"), WebConfig)
            web_ports = {"settings.json webport": settings.webport,
                         "html-service.json port": htmlsrv.port if htmlsrv is not None else None}

            running = os_service_of(pid)
            role = next((r for r, n in configured.items() if running and n == running), None)
            if role is None and listening:
                role = "synode" if settings.port == port \
                       else "web" if port in web_ports.values() else None

            notes = []
            if not listening:
                pass
            elif role == "synode" and settings.port not in (None, port):
                notes.append(f"settings.json port is {settings.port}")
            elif role == "web":
                notes += [f"{src} is {v}" for src, v in web_ports.items() if v is not None and v != port]
            if role and running and configured[role] and running != configured[role]:
                notes.append(f"settings.json says {configured[role]}")

            try:
                cwd = p.cwd()
            except (psutil.AccessDenied, OSError):
                cwd = None
            found = dict(root=root, cwd=cwd, role=role, running=running, notes=notes, status=status,
                         name=running or (configured[role] if role else None))
    except (psutil.Error, OSError):
        pass

    _synode_cache[key] = found
    return found


def _service_text(s):
    """e.g. 'Synode.web-0.4.5-reddish-2-2 (web; settings.json webport is 8960)'"""
    name = s["name"] or "(service name not in settings.json)"
    if s["status"] == "LISTEN":
        tags = [s["role"] or "port not in settings.json"]
    else:
        tags = [f"{s['role']}, " if s["role"] else ""]
        tags[0] += f"connection [{s['status']}], not listening"
    if s["running"] is None:
        tags[0] += ", not running as a service"
    return f"{name} ({'; '.join(tags + s['notes'])})"


def synode_label(pid, port, status="LISTEN"):
    """Short form, without -v: the service name and role, or None if the port isn't held by a synode's process."""
    s = synode_lookup(pid, port, status) if pid is not None else None
    return None if s is None else _service_text(s)


def synode_info(pid, port, status="LISTEN"):
    """
    :return: lines describing the synode service holding the port, or [] if the process isn't a synode's.
    """
    s = synode_lookup(pid, port, status)
    if s is None:
        return []

    lines = [f"service: {_service_text(s)}"]
    root, cwd = s["root"], s["cwd"]
    if cwd and os.path.normcase(os.path.abspath(cwd)) != os.path.normcase(root):
        lines.append(f"folder : {root}  (process cwd: {cwd})")
    else:
        lines.append(f"folder : {root}")
    return lines


def unowned_note(err):
    if IS_WIN and err == WSAEACCES:
        return "(reserved by Windows, see: netsh int ipv4 show excludedportrange protocol=tcp)"
    return "(owner not found)"


def print_formatted_range(status, start, end, note=""):
    icon = "✅" if status == "available" else "❌"
    note = f"  {note}" if note else ""
    if start == end:
        print(f"{icon}  [{start}]{note}")
    else:
        print(f"{icon}  [{start} - {end}]{note}")


def print_used_ports(start, end, owners, errors, cmdline=False):
    """
    Verbose form of a used range: one line per port with the program(s) holding it.
    Consecutive ports without an owner (e.g. Windows reserved ranges) are folded into one line.
    """
    import psutil

    run = None  # [first, last, note]

    def flush_run():
        nonlocal run
        if run is not None:
            print_formatted_range("used", run[0], run[1], run[2])
            run = None

    for port in range(start, end + 1):
        pids = owners.get(port)
        if pids:
            flush_run()
            for pid, status in pids.items():
                state = "" if status == psutil.CONN_LISTEN else f" [{status}]"
                pid_s = f"pid {pid}" if pid is not None else "pid ?"
                print(f"❌  [{port}]  {pid_s}{state}  {describe_process(pid, cmdline)}")
                if pid is not None:
                    for line in synode_info(pid, port, status):
                        print(f"        {line}")
        else:
            note = unowned_note(errors.get(port))
            if run is not None and run[2] == note and run[1] == port - 1:
                run[1] = port
            else:
                flush_run()
                run = [port, port, note]
    flush_run()


def print_used_range_short(start, end, owners):
    """
    Without -v: the used range as before, plus the service name of ports held by a synode or syn-web service.
    A single port gets the name on the same line; a longer range lists those ports beneath it.
    """
    labels = []
    for port in range(start, end + 1):
        for pid, status in owners.get(port, {}).items():
            label = synode_label(pid, port, status)
            if label:
                labels.append((port, label))

    if start == end and len(labels) == 1:
        print_formatted_range("used", start, end, labels[0][1])
        return
    print_formatted_range("used", start, end)
    for port, label in labels:
        print(f"        [{port}]  {label}")


def report_port_ranges(start_port, end_port, host="127.0.0.1", verbose=0):
    """Scans ports and prints blocks of available/used ranges."""
    # Owners are needed for -v, for naming synode services without -v, and on Windows / macOS for the listening
    # cross-check. Without -v, psutil is optional: the ranges are still reported without it.
    owners = None
    if verbose:
        owners = tcp_port_owners()
    else:
        try:
            owners = tcp_port_owners()
        except ImportError:
            pass

    errors = {}

    def flush(status, start, end):
        if verbose and status == "used":
            print_used_ports(start, end, owners, errors, cmdline=verbose > 1)
        elif status == "used" and owners is not None:
            print_used_range_short(start, end, owners)
        else:
            print_formatted_range(status, start, end)

    if owners_partial:
        print(f"(Not root: only your own programs' sockets are listed, {ELEVATE} to see all.)")
    print("Available ports:")

    current_status = None
    range_start = start_port

    for port in range(start_port, end_port + 1):
        free, err = check_port(port, host)
        # Windows and macOS (BSD) can let a bind on 127.0.0.1 succeed while another program listens on
        # 0.0.0.0 or [::] of the same port - Java listens on [::] by default.
        if free and (IS_WIN or IS_MAC) and owners is not None and is_listening(owners, port):
            free = False
        if not free:
            errors[port] = err
        status = "available" if free else "used"

        if current_status is None:
            current_status = status

        if status != current_status:
            flush(current_status, range_start, port - 1)
            range_start = port
            current_status = status

    flush(current_status, range_start, end_port)


if __name__ == "__main__":
    if IS_WIN:
        # Git Bash / redirected output uses the ANSI code page (e.g. cp936), which can't encode ✅ ❌.
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    ap = argparse.ArgumentParser(description="Show available and occupied TCP ports (1024 - 65535).")
    ap.add_argument("-v", "--verbose", action="count", default=0,
                    help="-v: print the program path holding each occupied port; -vv: also its arguments")
    args = ap.parse_args()

    if args.verbose:
        try:
            import psutil  # noqa: F401
        except ImportError:
            sys.exit("-v needs psutil: pip install psutil")

    report_port_ranges(1024, 65535, verbose=args.verbose)
