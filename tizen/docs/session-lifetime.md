# Session lifetime and delivery bounds

The NUI application owns one SSH session and its terminal model. Native views may be rebuilt
without replacing either owner. A new connection retires its old view before disposing its old
terminal model. Explicit disconnect keeps the final screen readable; reconnect replaces it.
Pause only revokes gestures/modifiers. It does not dispose the transport.

## Input and viewport work

An input command is at most1MiB. Pending data, including the active write, is limited to2MiB
and256 commands. Empty writes are no-ops. The bounded channel has one additional reserved slot
for coalesced geometry, so a full data queue cannot lose its next viewport update. Queue-full
input fails explicitly instead of allocating indefinitely or discarding existing commands.

One worker serializes writes, resize and refresh. Refresh temporarily changes the PTY columns
then restores the latest desired dimensions; it sends no terminal keystrokes. Dimensions changed
while host verification is waiting remain the desired dimensions after shell creation. A real
OpenSSH regression reproduced the prior overwrite and now checks the server's actual stty size.

## Retirement

Every DisposeAsync caller receives completion of the same session cleanup. Retirement marks the
session unavailable before scheduling cleanup off the UI thread, cancels the lifetime, closes
transport/channel operations, waits for handshake and reader/writer completion, closes the owned
SFTP adapter, drains queued input, removes callbacks and disposes authentication/key resources.
A shell created concurrently cannot be adopted after retirement. EOF and writer failure share one
closed notification; explicit retirement suppresses it. An already executing output callback must
return before disposal completes. Callbacks must not synchronously wait for their own retirement.

The VT wrapper explicitly disposes its reused parser, handlers and normal/alternate row storage.
Late network feed becomes inert; a retired model cannot produce another frame. Views retire
before their models, and retired interaction handlers reject further gestures/paste. These are
managed buffers and delegates, not a claim that immutable managed strings can be zeroized.

The application's normal Close Hexgate action ends the NUI loop and joins transport/frame/preview cleanup
after that loop returns. No network shutdown is run synchronously inside a native button callback.
Native button actions are posted out of the active signal before rebuilding a scene; this avoids
the reproduced recursive Emit warning for the tested chrome operations.

## UI delivery

Terminal painting already coalesces a burst into one delayed frame. Status delivery now also
keeps one pending callback and the latest text, scoped to a connection generation. Old producers
cannot overwrite the current pending status. Queued work becomes inert on disposal. Async failure
paths recheck ownership after awaiting cleanup, so an old failure cannot clear a new connection.
This follows the small owning-context dispatcher pattern in the read-only Calendar reference.

## Evidence and remaining boundaries

Portable tests exercise256 tiny writes,2MiB aggregate credits,10,000 empty writes, explicit engine
retirement and10,000 coalesced status updates. Live SSH tests delay output/host callbacks, race
SFTP opening against disposal, and verify actual PTY dimensions changed during host verification.
Native tests prove normal process exit, closed SSH connections, subsequent new PIDs and automatic
password reconnect without credential entry or host-trust prompts. See validation.md for artifacts.

The locally built SSH.NET adaptation now bounds each ShellStream read allocation and unread
window to1MiB. It advertises that window before opening the shell and returns receive credit
only when reads/expect/line operations consume bytes. The SSH listener keeps processing other
channels and control messages while terminal consumption is paused. One16KiB read callback may
also be active outside that window. No terminal bytes are discarded to enforce the normal bound.
Normal and extended output share credit. Protocol-over-window or oversized channel data is
rejected before copying; the connection-wide packet decoder remains upstream.

The prior unmodified2026.0.0 package failed the real receive-window regression. The adaptation
passes an8MiB exact-byte flood, another command on the same saturated SSH connection, repeat
saturation, close while full and extended-output delivery. Native2MiB rendering is separately
verified. This proves the bounded terminal path, not a universal quota for every unrelated
SSH.NET forwarding/SFTP channel. See [the pinned source adaptation](../vendor/SSH.NET/README.md),
its documented patch and source hash verifier. Cryptography is unchanged.

Remaining native preview/interceptor races, wider hostile-resource tests and final exact-package
regressions remain separate acceptance gates. No process-death recovery or unlimited background
execution is claimed.


## Preview work ownership

A bounded work group admits at most32 pending/running preview producers, file reads and
intercept workers. Once shutdown seals it, no later worker can enter; all admitted work is
joined after the NUI loop returns. Worker faults remain observable by the shutdown owner.
Each native intercept additionally holds one of8 credits until its response is delivered
or discarded on the UI thread, bounding both pending HTTP work and queued response bodies.

A remotely read document's server is owned by its producer until the UI explicitly adopts it.
Cancellation, stale generation, changed overlay or termination rejects the transfer and
disposes the candidate server. Closing/replacing an adopted preview records server cleanup
for shutdown to join. Repeated PreviewServer disposal callers receive the same task; stopping
its listener and waiting for requests precedes disposal of synchronization resources.

Native four-format and normal-exit results are recorded in validation.md. These contracts do
not by themselves prove every WebView callback race or the incoming SSH buffer bound.


## Passphrase interaction

Profile selection starts authentication off the UI thread with an empty passphrase. An
unencrypted key therefore connects directly, including last-profile auto-connect. Only the
parser's SshPassPhraseNullOrEmptyException triggers a separate masked passphrase dialog,
after that failed attempt's cleanup completes and its generation still owns the UI. No
password/key authentication fallback occurs. Wrong passphrases produce the safe failure
status; retrying requests a new memory-only passphrase. The final emulator increment verifies
unencrypted normal-restart auto-connect and wrong/correct encrypted-key passphrases.

## SFTP reassembly boundary — 2026-10-21

The separate SftpSession receive path now validates headers before body
admission, including split headers, and assembles one frame at a time. Frame
and backing-array limits are1MiB including the four-byte length header. This
is independent of ShellStream consumer credit and application document/resource
limits. Incomplete admissible frames use SFTP operation timeout/cancellation.
See vendor/SSH.NET/README.md and the current validation section for real-wire
red/green evidence and the explicit large-single-response compatibility limit.
