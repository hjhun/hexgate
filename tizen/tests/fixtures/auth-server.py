"""Isolated loopback SSH auth/PTY fixture. Never executes host commands."""
import argparse
import asyncio
import json
import os
import posixpath
from pathlib import Path
import secrets

import asyncssh

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--directory', required=True, type=Path)
parser.add_argument('--port', type=int, default=22223)
parser.add_argument('--sftp-root', type=Path, help='Optional isolated fixture directory; never use production data')
args = parser.parse_args()
if not 1024 <= args.port <= 65535:
    parser.error('Choose an unprivileged port')
if args.sftp_root is not None and not args.sftp_root.is_dir():
    parser.error('SFTP fixture root must already be a directory')
root = args.directory.resolve()
if args.sftp_root is not None and root.is_relative_to(args.sftp_root.resolve()):
    parser.error('SFTP root must not contain the credential output directory')
root.mkdir(mode=0o700, parents=True, exist_ok=True)
if any(root.iterdir()):
    parser.error('Fixture directory must be empty; existing credentials are never overwritten')
root.chmod(0o700)
for name in ['password', 'passphrase']:
    (root / name).write_text(secrets.token_urlsafe(24))
key = asyncssh.generate_private_key('ssh-ed25519')
key.write_private_key(root / 'client')
key.write_public_key(root / 'client.pub')
key.write_private_key(root / 'client-encrypted', passphrase=(root / 'passphrase').read_text())
for name in ['host', 'wrong-client']:
    asyncssh.generate_private_key('ssh-ed25519').write_private_key(root / name)
for path in root.iterdir():
    path.chmod(0o600)

stats = dict(connections=0, active=0, passwordAccepted=0, passwordRejected=0,
             keyAccepted=0, keyRejected=0, shells=0, sftpOpens=0, sftpPrivateOpens=0)


def save():
    pending = root / 'stats.pending'
    pending.write_text(json.dumps(stats))
    pending.chmod(0o600)
    pending.replace(root / 'stats.json')


class Server(asyncssh.SSHServer):
    def connection_made(self, connection):
        stats['connections'] += 1
        stats['active'] += 1
        save()

    def connection_lost(self, error):
        stats['active'] -= 1
        save()

    def begin_auth(self, username):
        return True

    def password_auth_supported(self):
        return True

    def public_key_auth_supported(self):
        return True

    def validate_password(self, username, password):
        accepted = (username == 'fixture' and secrets.compare_digest(
            password, (root / 'password').read_text()))
        stats['passwordAccepted' if accepted else 'passwordRejected'] += 1
        save()
        return accepted

    def validate_public_key(self, username, public_key):
        accepted = username == 'fixture' and public_key == asyncssh.read_public_key(root / 'client.pub')
        stats['keyAccepted' if accepted else 'keyRejected'] += 1
        save()
        return accepted


class CountedSFTPServer(asyncssh.SFTPServer):
    """Observe fixture reads without logging filenames or content."""
    def open(self, path, pflags, attrs):
        stats['sftpOpens'] += 1
        if posixpath.normpath(path).startswith(b'/private/'):
            stats['sftpPrivateOpens'] += 1
        save()
        return super().open(path, pflags, attrs)


async def process(channel):
    stats['shells'] += 1
    save()
    channel.stdout.write('HEXGATE AUTH FIXTURE READY\r\n')
    try:
        while True:
            try:
                data = await channel.stdin.read(4096)
            except (asyncssh.TerminalSizeChanged, asyncssh.BreakReceived, asyncssh.SignalReceived):
                continue
            if not data:
                break
            if '\n' in data or '\r' in data:
                channel.stdout.write('FIXTURE INPUT ACCEPTED\r\n')
    finally:
        channel.exit(0)


async def main():
    host = asyncssh.read_private_key(root / 'host')
    async with asyncssh.create_server(Server, '127.0.0.1', args.port,
                                     server_host_keys=[host], process_factory=process,
                                     sftp_factory=(lambda channel: CountedSFTPServer(
                                         channel, chroot=os.fsencode(args.sftp_root.resolve())))
                                     if args.sftp_root is not None else None):
        save()
        (root / 'ready.json').write_text(json.dumps(dict(
            pid=os.getpid(), port=args.port, fingerprint=host.get_fingerprint('sha256'))))
        print('Authentication fixture ready: ' + str(root / 'ready.json'), flush=True)
        await asyncio.sleep(1800)


try:
    asyncio.run(main())
except KeyboardInterrupt:
    pass
