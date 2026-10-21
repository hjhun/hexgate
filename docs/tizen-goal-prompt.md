# Goal prompt: Hexgate for Tizen NUI

Implement and validate a usable Tizen NUI port of Hexgate on the `tizen` branch. Finish the
application, signed emulator TPK, tests and emulator evidence; do not stop at a design, scaffold,
placeholder terminal or successful managed build.

## Workspace and identity

- Repository: `/home/hjhun/samba/workspace/hexgate` (use the actual Linux path if `/mnt/z/workspace/hexgate` is unavailable).
- Work only on branch `tizen`. It currently starts at `c472912`; inspect current state and preserve
  subsequent work. If the branch already exists, switch to it without resetting it. Preserve main
  and the existing Android application; put the new implementation under `tizen/`.
- Application label: Hexgate. Package ID AND UI application ID: `org.tizen.hexgate`.
- Read AGENTS.md, docs/design.md, docs/progress.md, docs/tizen-port-analysis.md and the Android
  implementation. Update repository instructions to distinguish Android reference code from
  the new C#/NUI target before implementing. Keep authored repository documentation in English.
- No automatic Git commits/pushes, store publishing, email, Drive upload or changes to external
  production services. This goal authorizes local builds, signing with an emulator test profile,
  installation and testing of this app on the selected emulator.

## Reference and environment discovery

Study `/home/hjhun/samba/workspace/tizen-action-examples/Calendar` and its repository instructions,
particularly Calendar.App.csproj, CalendarApplication.cs, ProportionalViewport.cs,
CalendarDisplayMetrics.cs, build.sh and package.sh. Also inspect Shared/Ui/UiChangeDispatcher.cs
and Browser/src/Browser.App/NuiWebViewRuntime.cs. Reference files/apps are read-only. Reuse the
patterns, not Calendar's whole Action/provider/alarm architecture. Action Framework integration
is outside this goal unless explicitly requested later.

The reference uses net8.0 and Tizen.NET 14.0.0.19326. Emulator `emulator-26111` was connected during
analysis. Rediscover the SDK, runner, certificate profile, actual target API/runtime, display,
input and WebView capabilities; do not assume a serial or version remains current. Use applicable
Tizen setup/build/package/install/device/log/screenshot skills. Do not stop or reset unrelated
emulators or reference apps. Do not substitute host DLL execution for running a signed TPK.

## Architecture and feasibility first

Use C# with a native NUI application shell, connection/settings UI and lifecycle ownership.
Keep portable behavior independently testable without Tizen assemblies. Start with few modules:
App/UI, portable core, and narrow SSH/storage/terminal/preview adapters where genuinely needed.

Before completing the UI, prove on the emulator: NUI package launch; real SSH with explicit host
trust; a mature VT terminal engine with rendering and input; secure credential storage; and the
local document-preview isolation mechanism. SSH.NET is a candidate for SSH/SFTP, subject to
actual target compatibility and license review. Reuse a mature VT engine; do not hand-write an
ANSI parser, print raw escape sequences, or use an append-only TextLabel as the terminal.
Prefer native NUI terminal rendering around an existing terminal core. If infeasible, document
the measured blocker and use a packaged local WebView terminal engine behind a narrow adapter,
keeping the NUI shell native. Benchmark the selected path before expanding it. Do not disguise
an entirely browser-hosted application as a native NUI port.

Record decisions, dependency licenses/versions, platform constraints and validation gates in
English development/progress documents. Routine choices already within this approved scope do
not need repeated approval. Ask only when genuinely blocked or a decision materially changes
scope or security, and continue independent work while waiting.

## Required behavior

1. Connections and credentials
   - Add/edit/delete profiles: name, host, port, user, key/password authentication, optional Herdr
     session and startup command. Show a masked SSH-password input in connection settings.
   - Import supported private keys; request encrypted-key passphrases separately. Store private
     keys and account passwords through verified Tizen secure storage/key-manager APIs.
     No plaintext credentials, embedded shared keys, secrets in JSON/logs or WebView bridges.
     Empty password edits retain the existing secret; replacements update it; profile deletion
     removes its credentials. If the target cannot provide secure persistence, fail closed and
     clearly report this unmet requirement rather than silently weakening storage.
   - Verify server SHA256 fingerprints explicitly on first use and change. Never accept all keys
     or silently fall back between authentication methods. Support cancellation, useful safe
     errors, timeouts, keepalive, reconnect and optional last-profile auto-connect. Never replace
     an already live connection just because the window/view is reconstructed.

2. Real terminal and input
   - UTF-8, CJK, combining characters, box drawing, Nerd Font glyphs, ANSI colors, cursor movement,
     alternate screen, scrollback and mouse reporting; Herdr must render and accept real commands.
   - Hardware keyboard, pointer and available target IME; NUI focus/back behavior and remote/D-pad
     navigation for the shell. Include Ctrl, Alt, Esc, Tab, Enter immediately after Tab, arrow keys,
     copy/paste and keyboard access where the target supports it. Do not claim an unavailable
     virtual keyboard was verified.
   - Ctrl+Shift+C/V perform local copy/paste; plain Ctrl+C/V retain remote terminal semantics.
     Right-click context copy/paste and Ctrl+Shift rectangular selection remain available.
   - When mouse tracking is active, ordinary drag reaches Herdr so it selects within its own pane.
     Shift during a drag explicitly authorizes one bounded session-scoped OSC52 copy reply.
     Preserve the local selection fallback. Unsolicited OSC52 must not alter the clipboard.
     Expire/revoke grants on cancellation, reconnect and view retirement.

3. NUI layout and session lifetime
   - Collapsible left drawer: connections near the top, Preview and Settings near the bottom.
     A terminal Refresh button and adjacent full-screen/windowed toggle; accurately report the
     system-UI controls the selected Tizen target actually supports.
   - Keep themes, readable licensed fonts and persistent preferences. Adapt Calendar's validated
     window/inset scaling to NUI chrome without double scaling. Compute terminal columns/rows
     from the real drawable viewport and measured cell metrics; serialize/coalesce PTY resize
     updates. Resize, pause/resume, reconnect and font changes must not leave corrupted frames.
   - Refresh requests a real redraw/PTY refresh without sending Ctrl+L, Enter or shell commands.
   - Own the SSH session outside disposable visual components. Pause does not deliberately close
     a live session; resume redraws it safely. Dispose events, cancellation, connections, engines
     and timers exactly once at termination. Do not claim unlimited background execution or
     process-death survival; verify the actual Tizen lifecycle and required privileges.
   - Bound queues, scrollback, remote reads and clipboard data. Keep networking off the NUI UI
     thread; batch/coalesce rendering and guard callbacks from retired sessions/views.

4. Remote file previews
   - Support Markdown, static HTML, Mermaid and draw.io, including diagrams embedded in Markdown
     as supported by the Android version. Bundle approved renderer assets locally.
   - Read files and relative resources through bounded SFTP, with canonical-path/symlink boundary
     checks. Preserve the existing Android document/resource/count/aggregate size limits unless
     a measured target constraint requires stricter documented limits.
   - Untrusted HTML cannot run arbitrary scripts, fetch arbitrary internet resources, read local
     files outside its permitted tree or call native APIs. Separate trusted diagram-rendering
     scripts from untrusted documents. Verify capabilities on the selected WebView engine.
   - Identify this client's Herdr session/TTY context; do not use another client's global focus.
     When ambiguous or unsupported, provide explicit pane/file selection. Never inspect/control
     the developer's real focused Herdr session; use only isolated test-created sessions.

## Execution and acceptance

Proceed in runnable increments: environment/TPK baseline; transport + terminal feasibility;
secure profiles; NUI navigation/input/lifecycle; selection/refresh/full-screen; previews; final
regression, packaging and evidence. Update progress after each verified increment.

Use isolated fixtures, test-only secrets and a reachable emulator-to-host address discovered
from the actual network. Do not change real account passwords, production sshd, routers or VPN.
Never record secrets or private remote content in screenshots, logs, source or artifacts.

Completion requires:
- A clean reproducible build and a signed, structurally valid TPK whose manifest declares both
  IDs exactly `org.tizen.hexgate`, with all runtime libraries/assets/licenses packaged.
- Installation and launch on a selected Tizen emulator, verified running process and bounded
  crash/error log review. Record actual API, runtime, serial, resolution and input modes tested.
- Tests of correct/incorrect key/password authentication, changed-host rejection, secret storage
  retention/replacement/deletion, SSH/SFTP, cancellation, disconnect/reconnect and stale callbacks.
- Actual Herdr rendering and command input, CJK/box-drawing/font checks, alternate-screen and
  viewport redraw, pane-scoped drag/Shift-copy, clipboard shortcuts, refresh and input handling.
- Pause/resume and repeated resize/font-change checks with live-session continuity where allowed.
- Working previews for all four formats plus hostile HTML/resource-boundary rejection tests.
- Screenshots of the installed app's connection editor, drawer/settings, live terminal and previews;
  a test report separating host tests, package checks, emulator runtime and unverified hardware.
- English README/build/run instructions, dependency notices and progress/known-limitations docs.

Do not mark the goal complete while required behavior is stubbed, tests are skipped silently,
only a scaffold builds, or the emulator cannot actually run it. Report a genuine blocking target
limitation clearly instead of fabricating success. Deliver the TPK path, checksum, reproducible
commands, test results, screenshot paths and precise remaining physical-device limitations.
