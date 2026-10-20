#!/usr/bin/env python3
"""Isolated loopback password SSH fixture; never changes OS users or system sshd."""
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import threading
import time

import paramiko

root = Path(__file__).resolve().parent.parent / '.tools/password-fixture'
root.mkdir(mode=0o700, parents=True, exist_ok=True)
os.chmod(root, 0o700)
key_path = root / 'host'
if not key_path.exists():
    paramiko.RSAKey.generate(2048).write_private_key_file(str(key_path))
    os.chmod(key_path, 0o600)
key = paramiko.RSAKey.from_private_key_file(str(key_path))
password = secrets.token_urlsafe(32)
fingerprint = 'SHA256:' + base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip('=')
listener = socket.socket()
listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(('127.0.0.1', 22223))
listener.listen(8)
credentials = root / 'credentials.json'
credentials.write_text(json.dumps(dict(user='hexgate-fixture', password=password, fingerprint=fingerprint)))
os.chmod(credentials, 0o600)


class Server(paramiko.ServerInterface):
    def get_allowed_auths(self, username):
        return 'password'

    def check_auth_password(self, username, candidate):
        return (paramiko.AUTH_SUCCESSFUL if username == 'hexgate-fixture' and
                secrets.compare_digest(candidate, password) else paramiko.AUTH_FAILED)

    def check_channel_request(self, kind, channel_id):
        return paramiko.OPEN_SUCCEEDED if kind == 'session' else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def check_channel_pty_request(self, *args):
        return True

    def check_channel_shell_request(self, channel):
        def shell():
            try:
                channel.sendall(b'PASSWORD_SHELL_READY\r\n')
                while channel.recv(4096):
                    pass
            except (EOFError, OSError):
                pass
        threading.Thread(target=shell, daemon=True).start()
        return True

    def check_channel_exec_request(self, channel, command):
        def execute():
            try:
                channel.sendall(b'PASSWORD_EXEC_OK\n')
                channel.send_exit_status(0)
                channel.close()
            except (EOFError, OSError):
                pass
        threading.Thread(target=execute, daemon=True).start()
        return True


def serve(client):
    transport = paramiko.Transport(client)
    transport.add_server_key(key)
    try:
        transport.start_server(server=Server())
        channels = []
        while transport.is_active():
            channel = transport.accept(1)
            if channel is not None:
                channels.append(channel)
    except (paramiko.SSHException, EOFError, OSError):
        pass
    finally:
        transport.close()


print('Password SSH fixture listening only on loopback port 22223.', flush=True)
try:
    while True:
        client, _ = listener.accept()
        threading.Thread(target=serve, args=(client,), daemon=True).start()
finally:
    listener.close()
    credentials.unlink(missing_ok=True)
