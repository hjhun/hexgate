"""Isolated malformed SFTP framing fixture; executes no host commands."""
import argparse
import asyncio
import json
import os
from pathlib import Path
import secrets
import struct

import asyncssh

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--directory', required=True, type=Path)
parser.add_argument('--port', type=int, default=22225)
args = parser.parse_args()
if not 1024 <= args.port <= 65535:
    parser.error('Use an unprivileged port')
root = args.directory.resolve()
root.mkdir(mode=0o700, parents=True, exist_ok=True)
if any(root.iterdir()):
    parser.error('Use a fresh empty directory')
root.chmod(0o700)
(root / 'password').write_text(secrets.token_urlsafe(24))
host = asyncssh.generate_private_key('ssh-ed25519')
host.write_private_key(root / 'host')
for path in root.iterdir():
    path.chmod(0o600)
stats = dict(active=0, subsystems=0, payloadBytes=0, responses=0)


def save():
    temp = root / 'stats.pending'
    temp.write_text(json.dumps(stats))
    temp.replace(root / 'stats.json')


class Server(asyncssh.SSHServer):
    def session_requested(self):
        return FramingSession()

    def connection_made(self, connection):
        stats['active'] += 1
        save()

    def connection_lost(self, error):
        stats['active'] -= 1
        save()

    def begin_auth(self, username):
        return True

    def password_auth_supported(self):
        return True

    def validate_password(self, username, password):
        return username == 'fixture' and secrets.compare_digest(password, (root / 'password').read_text())


class FramingSession(asyncssh.SSHServerSession):
    """Accept SFTP explicitly, bypassing AsyncSSH's normal SFTP codec."""
    def __init__(self):
        self.stdin = asyncio.StreamReader(limit=65536)
        self.stdout = self
        self.writable = asyncio.Event()
        self.writable.set()

    def connection_made(self, channel):
        self.channel = channel

    def subsystem_requested(self, name):
        return name == 'sftp'

    def session_started(self):
        self.task = asyncio.create_task(process(self))

    def data_received(self, data, datatype):
        self.stdin.feed_data(data)

    def eof_received(self):
        self.stdin.feed_eof()

    def connection_lost(self, error):
        self.stdin.feed_eof()
        self.writable.set()

    def pause_writing(self):
        self.writable.clear()

    def resume_writing(self):
        self.writable.set()

    def write(self, data):
        self.channel.write(data)

    async def drain(self):
        await self.writable.wait()

    def exit(self, code):
        self.channel.exit(code)


def string(value):
    return struct.pack('>I', len(value)) + value


def packet(body):
    return struct.pack('>I', len(body)) + body


async def process(channel):
    mode = (root / 'mode').read_text() if (root / 'mode').exists() else 'oversized'
    replies = []

    async def send(data, split=False):
        if split:
            for byte in data[:4]:
                channel.stdout.write(bytes([byte]))
                await channel.stdout.drain()
                await asyncio.sleep(.01)
            data = data[4:]
        for offset in range(0, len(data), 16384):
            part = data[offset:offset + 16384]
            channel.stdout.write(part)
            await channel.stdout.drain()
            stats['payloadBytes'] += len(part)
        save()

    def attributes(identifier, padding=0):
        body = struct.pack('>BII', 105, identifier, 0x80000000 if padding else 0)
        if padding:
            body += struct.pack('>I', 1) + string(b'x') + string(b'X' * padding)
        return packet(body)

    try:
        stats['subsystems'] += 1
        save()
        while True:
            size = struct.unpack('>I', await channel.stdin.readexactly(4))[0]
            if size > 65536:
                raise ValueError('Client request exceeds fixture limit')
            request = await channel.stdin.readexactly(size)
            kind = request[0]
            if kind == 1:
                channel.stdout.write(packet(struct.pack('>BI', 2, 3)))
            elif kind == 16:
                identifier = struct.unpack('>I', request[1:5])[0]
                channel.stdout.write(packet(struct.pack('>BII', 104, identifier, 1) + string(b'/') + string(b'/') + struct.pack('>I', 0)))
            else:
                identifier = struct.unpack('>I', request[1:5])[0]
                if mode in ('valid', 'valid-split', 'maximum', 'consecutive'):
                    padding = 1024 * 1024 - 26 if mode == 'maximum' else 0
                    if mode == 'consecutive':
                        replies.append(attributes(identifier, 32768))
                        if len(replies) < 64:
                            continue
                        response = b''.join(replies)
                        replies.clear()
                    else:
                        response = attributes(identifier, padding)
                    await send(response, split=mode in ('valid-split', 'maximum'))
                    stats['responses'] += 64 if mode == 'consecutive' else 1
                    save()
                elif mode == 'incomplete-header':
                    await send(b'\x00\x10')
                    await asyncio.sleep(4)
                    return
                elif mode in ('incomplete-body', 'cancel'):
                    await send(struct.pack('>I', 1024 * 1024 - 4) + b'X' * (512 * 1024), split=True)
                    await asyncio.sleep(4)
                    return
                else:
                    lengths = {'zero': 0, 'negative': 0xffffffff,
                               'over-limit': 1024 * 1024 - 3}
                    length = lengths.get(mode, 0x7ffffff0)
                    await send(struct.pack('>I', length), split=mode == 'oversized-split')
                    # Keep the source active: rejection must precede an ordinary
                    # operation timeout, not rely on fixture EOF.
                    if mode == 'oversized':
                        await send(b'X' * (6 * 1024 * 1024))
                    await asyncio.sleep(4)
                    return
            await channel.stdout.drain()
    except (asyncio.IncompleteReadError, asyncssh.Error, BrokenPipeError):
        pass
    finally:
        channel.exit(0)


async def main():
    async with asyncssh.create_server(Server, '127.0.0.1', args.port,
                                     server_host_keys=[host], encoding=None):
        save()
        (root / 'ready.json').write_text(json.dumps(dict(pid=os.getpid(), port=args.port, fingerprint=host.get_fingerprint('sha256'))))
        print('Malformed SFTP fixture ready', flush=True)
        await asyncio.sleep(900)


try:
    asyncio.run(main())
except KeyboardInterrupt:
    pass
