"""Read-only named Herdr snapshot; automatic focus requires this unique SSH TTY."""
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import subprocess
import sys
import time

LIMIT = 1024 * 1024


def read_small(path, limit):
    with path.open('rb') as stream:
        value = stream.read(limit + 1)
    if len(value) > limit:
        raise ValueError('Process metadata exceeds limit')
    return value


def terminal_clients(session):
    found = []
    for count, proc in enumerate(Path('/proc').iterdir()):
        if count > 65536:
            raise ValueError('Process enumeration exceeds limit')
        if not proc.name.isdigit():
            continue
        candidate = False
        try:
            if proc.stat().st_uid != os.getuid():
                continue
            args = [s.decode('utf-8', 'replace') for s in read_small(proc / 'cmdline', 65536).split(b'\0') if s]
            if not args or Path(args[0]).name != 'herdr':
                continue
            remaining = args[1:]
            if '--session' in remaining:
                index = remaining.index('--session')
                if index + 1 >= len(remaining):
                    raise ValueError('Incomplete client selector')
                if remaining[index + 1] != session:
                    continue
            elif remaining[:2] == ['session', 'attach']:
                if len(remaining) < 3 or remaining[2] != session:
                    continue
            if remaining[:1] == ['server']:
                continue
            candidate = True
            env = {}
            for field in read_small(proc / 'environ', 262144).split(b'\0'):
                key, _, value = field.partition(b'=')
                if key in (b'HERDR_SESSION', b'XDG_CONFIG_HOME', b'SSH_CONNECTION'):
                    env[key.decode()] = value.decode('utf-8')
            remaining = args[1:]
            selected = env.get('HERDR_SESSION')
            if '--session' in remaining:
                index = remaining.index('--session')
                selected = remaining[index + 1]
                remaining = remaining[:index] + remaining[index + 2:]
            elif remaining[:2] == ['session', 'attach']:
                selected = remaining[2]
                remaining = []
            if selected != session or remaining[:1] == ['server']:
                continue
            tty = os.readlink(proc / 'fd/0')
            if remaining or not re.fullmatch(r'/dev/pts/\d+', tty):
                tty = 'unidentified-client'
            config = env.get('XDG_CONFIG_HOME', '')
            if config and (not config.startswith('/') or len(config) > 4096):
                raise ValueError('Invalid client configuration directory')
            stat = read_small(proc / 'stat', 65536).decode().rsplit(') ', 1)[1].split()
            found.append(dict(pid=int(proc.name), tty=tty, config=config, start=stat[19], connection=env.get('SSH_CONNECTION', '')))
            if len(found) > 64:
                raise RuntimeError('Too many terminal clients')
        except FileNotFoundError:
            continue
        except (OSError, ValueError, UnicodeError, IndexError):
            if candidate:
                raise ValueError('A Herdr client could not be identified safely')
            continue
    return sorted(found, key=lambda client: client['pid'])


def automatic_focus(before, after, tty):
    return before == after and len(after) == 1 and after[0]['tty'] == tty


def bounded_snapshot(command, environment):
    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=environment) as process:
        try:
            output = bytearray()
            deadline = time.monotonic() + 10
            with selectors.DefaultSelector() as ready:
                ready.register(process.stdout, selectors.EVENT_READ)
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise TimeoutError('Snapshot timeout')
                    if not ready.select(remaining):
                        raise TimeoutError('Snapshot timeout')
                    data = os.read(process.stdout.fileno(), min(16384, LIMIT + 1 - len(output)))
                    if not data:
                        break
                    output.extend(data)
                    if len(output) > LIMIT:
                        raise ValueError('Snapshot exceeds limit')
            if process.wait(timeout=max(.01, deadline - time.monotonic())) != 0:
                raise ValueError('Snapshot failed')
            return json.loads(output)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()


def query(session, tty):
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', session) or not re.fullmatch(r'/dev/pts/\d{1,70}', tty):
        raise ValueError('Invalid client identity')
    before = terminal_clients(session)
    connection = os.environ.get('SSH_CONNECTION', '')
    if not connection or len(connection) > 256:
        raise ValueError('SSH connection identity unavailable')
    matched = [client for client in before if client['tty'] == tty and client['connection'] == connection]
    if len(matched) != 1:
        raise ValueError('Cannot identify this SSH client')
    config = matched[0]['config']
    # Never query another configuration's global focus, even for an equal name.
    if any(client['config'] != config for client in before):
        raise ValueError('Ambiguous named session configuration')
    environment = os.environ.copy()
    for key in ('HERDR_ENV', 'HERDR_SOCKET_PATH', 'HERDR_PANE_ID', 'XDG_CONFIG_HOME'):
        environment.pop(key, None)
    if config:
        environment['XDG_CONFIG_HOME'] = config
    binary = shutil.which('herdr') or str(Path.home() / '.local/bin/herdr')
    payload = bounded_snapshot([binary, '--session', session, 'api', 'snapshot'], environment)
    snapshot = payload.get('result', {}).get('snapshot')
    if not isinstance(snapshot, dict):
        raise ValueError('Unsupported snapshot')
    after = terminal_clients(session)
    automatic = automatic_focus(before, after, tty)
    panes = []
    rows = snapshot.get('panes', [])
    if not isinstance(rows, list) or len(rows) > 500:
        raise ValueError('Too many panes')
    for pane in rows:
        cwd = pane.get('foreground_cwd') or pane.get('cwd')
        identifier = pane.get('pane_id')
        if identifier and isinstance(cwd, str) and cwd.startswith('/'):
            panes.append(dict(id=identifier, cwd=cwd, name=pane.get('title') or identifier))
    return dict(session=session, automatic=automatic,
                focused=snapshot.get('focused_pane_id') if automatic else None, panes=panes)


if __name__ == '__main__':
    try:
        print(json.dumps(query(sys.argv[1], sys.argv[2]), ensure_ascii=False))
    except Exception:
        print(json.dumps(dict(error='Herdr context unavailable; choose a file explicitly.')))
        sys.exit(1)
