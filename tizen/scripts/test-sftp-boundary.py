#!/usr/bin/env python3
"""Run the real-wire SFTP frame matrix with disposable loopback credentials."""
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time

repo = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='hexgate-sftp-boundary-') as temporary:
    root = Path(temporary)
    # Port 0 allocation is used only to choose an unused loopback test port.
    # If another process races the bind, the fixture fails without replacing it.
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1', 0))
        port = reservation.getsockname()[1]
    with tempfile.TemporaryFile() as server_log:
        server = subprocess.Popen([
            sys.executable, str(repo / 'tizen/tests/fixtures/sftp-frame-server.py'),
            '--directory', str(root), '--port', str(port)
        ], cwd=repo, stdout=server_log, stderr=server_log)
        try:
            deadline = time.monotonic() + 10
            while not (root / 'ready.json').exists():
                if server.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('SFTP fixture did not become ready; check pinned host dependencies')
                time.sleep(.05)
            subprocess.run([
                'dotnet', 'run', '--project', 'tizen/tests/Hexgate.Tests', '--',
                '--sftp-frame-fixture', str(root), str(port)
            ], cwd=repo, check=True, timeout=90)
            deadline = time.monotonic() + 5
            while json.loads((root / 'stats.json').read_text())['active'] != 0:
                if time.monotonic() > deadline:
                    raise RuntimeError('Fixture still has an active connection after disposal')
                time.sleep(.05)
            print('PASS SFTP fixture has zero active connections after the matrix')
        finally:
            server.terminate()
            try:
                server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)
