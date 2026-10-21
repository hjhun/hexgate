# SSH.NET receive-window adaptation

Upstream: https://github.com/sshnet/SSH.NET, tag `2026.0.0`, commit
`7b2fd3dbf2c86a80a7b06cea020aa5f821c9902e`. This matches the repository commit
recorded by the original SSH.NET 2026.0.0 NuGet package. MIT license: LICENSE.

The library is built locally for net8.0; it replaces the NuGet SSH.NET assembly.
Cryptography and authentication retain upstream implementations. SFTP frame admission is bounded as documented below.
Dependencies remain BouncyCastle.Cryptography 2.7.0 and Logging.Abstractions 8.0.3.
PolySharp 1.16.0 is a build-only upstream dependency. No SSH signing key is imported.
The assembly version is 2026.0.0.2, with an explicit Hexgate informational version.

## Receive credit

Upstream ShellStream grows its read array and automatically restores the channel window
when packets arrive. A stalled consumer therefore has no hard unread-data bound.
The adaptation advertises a 1 MiB receive window for ShellStream channels only and uses
a fixed 1 MiB read array. Each read/expect/line discard returns consumed credit, coalesced
at the 64 KiB packet size or an exhausted window. The SSH listener never waits for the
terminal consumer, so unrelated channels and close/control messages remain live.

Normal and extended output share this budget. Invalid over-window/over-packet messages
are rejected before copying. Other channel types keep upstream automatic adjustment.
No protocol bytes are discarded to enforce the normal bound. A regex/line larger than
the receive window cannot be accumulated without consumption; Hexgate uses byte reads.

## Build compatibility and provenance

Nine empty net8 compatibility extension shims use newer C# syntax and are excluded by
Hexgate.SshNet.csproj. Their bodies only target older frameworks. One null-conditional
event unsubscription is expressed as an equivalent explicit null check for C#12.
BuildVersion.cs replaces upstream Git-version code generation. Upstream source files,
including excluded shims, are retained. Only the upstream project file is replaced.

`upstream-sha256.json` records original files; `patched-sha256.json` records imported
source with the adaptation. `receive-window.patch` documents every source change, including the removal of UTF-8 BOMs in the six original adaptation files.
Verify with `python3 tizen/scripts/verify-sshnet-source.py` from the repository root.
Do not edit cryptographic implementations to repair an application bug.

## Validation

`ReceiveFlowChecks` uses the real isolated OpenSSH fixture. It stops consumption,
checks the unread bound, opens another command on the same SSH transport, then verifies
all 8 MiB of synthetic payload in order. It also exercises repeated saturation,
disposal while full and SSH extended output. Default portable tests do not execute
this network test; use the documented --ssh-fixture mode.

## SFTP frame admission (adaptation 2)

The used SFTP subsystem has its own reassembly buffer, independent of ShellStream.
A real-wire fixture reproduced 2,424,836 buffered bytes for a malicious declared
length before the change. The adaptation validates every four-byte length header
(including fragmented headers), rejecting zero, negative and oversized lengths
before body admission. Each complete frame is at most 1 MiB including its header
(payload at most 1,048,572 bytes). Only the current frame is buffered; consecutive
valid frames can carry an arbitrarily larger total stream. The existing 32 KiB
ArrayBuffer doubles only up to 1 MiB. This is a reassembly/backing-array bound,
not a quota on all decoded objects or process memory.

Incomplete admissible responses still use the operation timeout/cancellation.
A server sending a single larger NAME/extension response is rejected explicitly;
ordinary multi-packet files remain supported. This local compatibility limit
does not change authentication or cryptographic algorithms.

Run the loopback-only `sftp-frame-server.py` fixture with a fresh private directory
and invoke the test harness with `--sftp-frame-fixture DIRECTORY PORT`. Its matrix
covers valid/split/maximum frames, 64 consecutive valid responses exceeding 2 MiB
in total, oversized/split/zero/negative/just-over-limit headers, incomplete header
and body timeouts, cancellation, actual backing allocation and joined disposal.
Fixture dependencies are pinned in `tests/fixtures/requirements.txt`.
