#!/usr/bin/env python3
"""Verify the pinned source import and intentional local source modifications."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'vendor/SSH.NET'
original=json.loads((root/'upstream-sha256.json').read_text())
expected=json.loads((root/'patched-sha256.json').read_text())
actual={str(p.relative_to(root/'src')):hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (root/'src').rglob('*') if p.is_file()}
assert actual==expected,'Vendored source differs from the recorded adaptation'
changed={name for name,digest in actual.items() if original.get(name)!=digest}
assert changed=={'Channels/Channel.cs','Channels/IChannel.cs','ISession.cs',
                 'Session.cs','ShellStream.cs','ForwardedPort.cs','Sftp/SftpSession.cs'},changed
assert set(original)-set(actual)=={'Renci.SshNet.csproj'}
print(f'PASS SSH.NET pinned source: {len(actual)-len(changed)} unchanged files, '
      f'{len(changed)} documented modifications; upstream project replaced')
