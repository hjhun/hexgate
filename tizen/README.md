# Hexgate for Tizen NUI

Development port on branch `tizen`, with actual SSH/Herdr and isolated document previews.
SFTP framing/reassembly now has a verified 1 MiB backing-array bound. The current
package passed the remaining-goal emulator regression and documentation audit.
This is an emulator development artifact.
See [the requirement audit](docs/acceptance.md) for evidence and platform boundaries.
The approved completion contract is [the goal specification](../docs/tizen-goal-prompt.md).
See the incremental [validation report](docs/validation.md) for commands and evidence boundaries.
Both Tizen package and application ID are `org.tizen.hexgate`.

## Current verified state

- C# net8.0 native NUI app with a reused MIT XtermSharp VT core; no browser terminal wrapper.
- Portable tests cover: Unicode/CJK/box characters, cursor overwrite, alternate-screen switch and
  restore, resize, combining-mark preservation, blank cells, CJK width, zero-width marks and
  UTF8 split across byte packets. Run `dotnet run --project tizen/tests/Hexgate.Tests` from root.
- Signed emulator-only TPK built with `tizen/scripts/build-package.sh` from repository root.
- Current TPK installed and launched on emulator-26101, VM tizen-10-1-20261016,
  Tizen10.1 Unified x86_64, 1920x1080. Earlier emulator-26111 observations are historical.
- Tizen SecureRepository DataManager save/get and native key-manager alias removal round-trip
  succeed on that emulator; only success/unknown-alias deletion statuses are accepted. No
  plaintext storage fallback exists.
- Native Unicode grid screenshot: `/tmp/hexgate-tizen-evidence/native-vt-0738.png`.
- SSH.NET 2026.0.0 connected from the installed app to the isolated host fixture after explicit
  fingerprint approval. Native keyboard input ran a command and rendered its output.
- Native connection/profile editor, secure key import, theme preferences and last-profile
  selection are implemented. Password retention/replacement/deletion and changed-host rejection
  passed native emulator checks; lifetime and platform boundaries are documented.
- Credential replacement/deletion uses a durable alias-only cleanup journal, with startup/manual
  retries and explicit pending-cleanup status. See [credential recovery](docs/credentials.md).
- Android theme colors and branded/control icons are reused. Six presets include the public
  Dracula, Gruvbox and Solarized ANSI palettes; bundled notices retain their license attribution.

## Tooling findings

The SDK-skills build runner compiled the managed app, then reported `Package file not found`:
its `tz pack` invocation exited successfully without producing a TPK. The reproducible local
package script follows the Calendar reference's legacy Tizen build-cs plus flattened staging and
emulator signer. It verifies ZIP integrity, manifest and both signatures and writes a SHA256SUMS
file. The successful install was through the SDK-skills project-manager runner.

## Platform boundaries

The selected emulator passed the documented functional checks. The
SFTP response-reassembly allocation is bounded to 1 MiB by real-wire tests.
Its OS back/home bubble remains visible after fullscreen requests.
Physical keyboard/right-click, Korean IME composition, Samsung hardware and
public-network behavior remain unverified. No unlimited background or
process-death survival is promised. Automatic Herdr context needs Python3,
Linux `/proc` and the supported read-only Herdr snapshot API; ambiguity requires
explicit pane selection, and unsupported identities use manual files.

The `dist/` TPK is a transient emulator development build signed with the SDK test certificate.
It is not approved for a physical device or store. No commits, pushes or publication are automatic.

## Remote previews

Connect first, open the left drawer and choose **Preview**. With a named Herdr
profile, a stable unique client matching this SSH connection/TTY opens its pane
folder. Multiple clients require an explicit pane choice; manual files remain
available when context cannot be established. Enter an absolute remote document
path or browse a folder. Supported extensions are `.md`, `.markdown`, `.html`, `.htm`, `.mmd`,
`.mermaid`, `.drawio` and `.xml`. The document opens beside the live native terminal.
Markdown supports embedded Mermaid fences and relative draw.io images using the Android assets.
HTML is static: document scripts, external resources, frames and native bridges are prohibited.
Relative resources are read through SFTP within the canonical document directory.

Limits match Android: documents 2 MiB; resources 1 MiB each, 64 requests and 16 MiB total;
directory listings 500 entries. Remote reads and renderer requests run off the NUI thread.
The selected emulator passed four-format rendering and a hostile-HTML external-request probe.
Default host checks pass151 assertions. OpenSSH/SFTP/lifetime/context mode passes198 and authentication
mode passes161 (each includes the same151 default checks), covering bounded CSI/DCS/title
parsing, line-operation allocation limits, SFTP boundaries and origin authentication checks.
The disposable real-wire SFTP framing runner passes208 checks:151 shared portable,
56 framing and one zero-active-connection cleanup assertion. Run it using a Python
virtual environment with `tests/fixtures/requirements.txt` installed:
`/path/to/venv/bin/python tizen/scripts/test-sftp-boundary.py`. A frame is limited
to1MiB including its four-byte header; unusually large individual directory or
extension responses are rejected. This limit is independent of the2MiB document
limit: normal files span many frames.

Host tests, installed-emulator observations and unverified physical hardware
are separate evidence layers; see the acceptance report.

The Tizen10.1 Chromium backend aborts when private browsing is toggled after creation, and its
WebView custom headers were ineffective in the initial probe. The adapter therefore disables
caching, serves `no-store` content and intercepts a synthetic HTTPS document origin. Its bounded
native requests carry the ephemeral server capability; it never enters renderer content/URLs.

## Terminal selection and clipboard

When mouse tracking is active, ordinary drags go to the remote terminal application.
Shift during a drag authorizes a single OSC52 clipboard write for five seconds. Unsolicited
clipboard commands and remote clipboard reads are ignored. Ctrl+Shift drag always selects a
local rectangle; the Select accessory also enables local selection over a mouse-tracked app.
Ctrl+Shift+C/V copy/paste through the target system clipboard. Plain Ctrl+C/V remain remote
control keys. Paste is bounded to 1 MiB and uses bracketed paste when negotiated.

The native accessory row includes Ctrl, Alt, Esc, Tab, Enter, arrows, Copy, Paste, Select and
Keyboard; it wraps when space is constrained. The target virtual keyboard is opened through
the native input dialog. Own Herdr pane selection/Shift-authorized OSC52, local rectangle copy/paste and remote Ctrl+C
have earlier emulator evidence in the validation report. Current-package context/preview regression passed; physical keyboard/right-click
and Korean IME composition remain unverified.


## Session ownership and normal exit

The app owns SSH/VT state independently of native views. Input is limited to1MiB per command,
2MiB pending and256 commands; resize/refresh coalesce into a reserved slot. Status updates also
coalesce and reject retired-session work. Session disposal waits for handshake/reader/writer/SFTP
cleanup; the VT model explicitly releases parser handlers and both row stores. Details and the
bounded receive and retirement contract are in [session lifetime](docs/session-lifetime.md).

**Settings → Close Hexgate** closes the app normally and completes transport cleanup after the
NUI loop returns. Reinstall-free native tests verified a new process automatically reconnects the
saved password and host trust without entry/prompts. Pause/resume keeps its existing session;
process termination still requires reconnect and does not preserve remote process state itself.

## Bounded SSH receive path

The port builds the pinned SSH.NET2026.0.0 source with a documented consumer-credit adaptation.
A stopped terminal consumer retains at most1MiB in its fixed shell buffer, plus the active
16KiB adapter read. Other SSH channels/control messages remain responsive. An actual8MiB
flood was received in order after consumption resumed; repeated saturation and close while
full passed. Native2MiB output rendered on the emulator. See
[vendor provenance and patch](vendor/SSH.NET/README.md) and [lifetime contract](docs/session-lifetime.md).
