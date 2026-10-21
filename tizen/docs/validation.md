# Tizen emulator validation

## Current accepted SFTP-boundary artifact — 2026-10-21

Current TPK SHA-256: `be86d3f3f51d02b065c4d0ac17bc7116785afd1519aca0cc45d8b32b1b524ff2`.
The SFTP reassembly gate, final native regression and documentation reconciliation
passed. See acceptance.md for the completed remaining-goal audit and separate
physical-device follow-up.
Earlier sections are historical observations on their stated packages.

The original malformed fixture failed at subsystem creation: AsyncSSH's generic
process factory rejects `sftp` without its SFTP factory, which would consume the
malformed protocol itself. An explicit synthetic server session now handles it.
The working fixture completed version/realpath and reproduced 2,424,836 buffered
bytes with an oversized declared length (`.tools/sftp-boundary-red.log`).

The adapter now validates every length header before body admission and assembles
one frame at a time. A frame including its four-byte header is at most 1 MiB.
The 32 KiB backing array doubles only up to 1 MiB; legitimate consecutive frames
are not subject to a cumulative transfer limit. Larger single NAME/extension
responses are rejected; this is a local compatibility boundary, not a claim of
a total process-memory quota. Authentication and cryptography are unchanged.

Reproducible isolated regression (from the repository root):

```sh
python3 -m venv .tools/sftp-test-env
.tools/sftp-test-env/bin/pip install -r tizen/tests/fixtures/requirements.txt
.tools/sftp-test-env/bin/python tizen/scripts/test-sftp-boundary.py
```

The runner generates private credentials, chooses a free loopback port, runs
twelve cases and removes its own fixture and credentials. It passes 208 checks:
151 shared portable assertions, 56 SFTP assertions and one zero-active-peer
cleanup assertion. Do not add shared checks across modes. Maximum valid frame
allocation measured 1,048,576 bytes; consecutive valid replies exceeded 2 MiB in
total. Oversized, split oversized, zero, negative and just-over-limit headers
failed before the normal operation timeout. Incomplete header/body timed out
near 2 seconds; cancellation completed near 250 ms. Logs:
`.tools/sftp-boundary-{red,first-green,matrix,runner}.log`.

Fresh portable/OpenSSH/authentication results are 151/198/161 respectively, each
network mode includes the same 151. Python Herdr helper tests remain a separate
eight-test suite. Source provenance verifies 336 unchanged and seven documented
modified upstream files; assembly adaptation is 2026.0.0.2.

SDK build job `20261021T011220-c5fb6f` compiled with zero errors but failed to
produce a TPK. The documented `tizen/scripts/build-package.sh` staging/signing
path succeeded. Both RSA signatures and 70/71 reference digests passed; built
App/Core/SSH DLLs equal the archive. ZIP, exact IDs, licenses, fixture-secret and
host-cache/private-bundle exclusions passed.

Initially no device was connected. The SDK default VM name did not exist; listing
actual VMs identified `tizen-10-1-20261016`, which was launched successfully.
Current serial is `emulator-26101`, Tizen10.1 Unified x86_64, 1920x1080. Full install
job `20261021T011311-c9f5d0` reports installation completed, launched and running.
This is distinct from the historical emulator-26111 results.


Final SFTP response-buffer acceptance remains open; this file retains the incremental
record, including superseded builds. See [the final audit](acceptance.md). The full
completion contract remains [the approved goal](../../docs/tizen-goal-prompt.md). Historical
experiments, failures and superseded builds are recorded in [progress](../../docs/progress.md).
No physical Samsung device or public-network result is inferred from emulator evidence.

## Reproducible commands

From the repository root:

```sh
dotnet run --project tizen/tests/Hexgate.Tests
PATH="$HOME/tizen-studio/tools/ide/bin:$PATH" tizen/scripts/build-package.sh
(cd tizen/dist && sha256sum -c SHA256SUMS)
```

The portable test executable runs47 additional integration/lifetime/context assertions only when passed
`--ssh-fixture HOST PORT USER PRIVATE_KEY EXPECTED_SHA256_FINGERPRINT`. Those assertions require
an isolated reachable SSH server with SFTP enabled; the default run is not evidence of
network testing. Never supply production credentials. Current default checks pass **151 assertions**;
OpenSSH/SFTP/context mode passes **198** (151 shared +47 integration/lifetime). Authentication mode passes **161**
(151 shared +10 protocol checks), using `--auth-fixture DIRECTORY PORT`. Its private fixture directory
must contain `ready.json`, `stats.json`, password, key and passphrase inputs; it is not a production
server integration mode. Current evidence: `.tools/sftp-boundary-ssh.log` and
`.tools/sftp-boundary-auth.log`. Do not sum the shared assertions twice.

To reproduce the authentication matrix without touching OS accounts, start the host-only fixture
in one terminal using a fresh empty directory (the fixture refuses to overwrite existing files):

```sh
python3 -m venv .tools/auth-fixture-env
.tools/auth-fixture-env/bin/pip install -r tizen/tests/fixtures/requirements.txt
.tools/auth-fixture-env/bin/python tizen/tests/fixtures/auth-server.py --directory .tools/auth-run --port 22223
```

In a second terminal:

```sh
dotnet run --project tizen/tests/Hexgate.Tests -- --auth-fixture .tools/auth-run 22223
```

Stop that fixture with Ctrl+C afterward; it also expires after30 minutes. It binds loopback,
generates only test credentials in the chosen private directory, and executes no host commands.
It supplies no SFTP service by default; optional `--sftp-root` enables a test-only jailed SFTP root.
The root must already exist and must exclude the credential-output directory. The full
OpenSSH/SFTP integration mode remains separate. The checked-in
fixture historically reproduced102 passing assertions in `.tools/tizen-auth-repro-tests.log`.

Package installation uses the installed Tizen SDK-skills `project-manager-cli.js install`
runner with `--device-serial emulator-26101` for the current VM; use its background receipt and `job-cli.js wait`
for the authoritative terminal result. The build script follows the reference Calendar staging
and emulator signing flow because the installed modern .NET package command does not emit a
TPK. The package contains the runtime dependencies, renderer assets and license notices.

## Initial environment and historical evidence boundaries

- Target: `emulator-26111`, Tizen 10.1 Unified, x86_64, 1920 x1080.
- Managed build: .NET SDK8.0.131; Tizen.NET14.0.0.19326; native NUI shell and terminal actors.
- Application and package ID: `org.tizen.hexgate`. Default development signer is emulator-only.
- SSH/SFTP: isolated loopback fixture on host port22222, emulator address10.0.2.2. Production
  sshd, account passwords, router configuration and VPN remain untouched.
- Herdr: private named fixture session/config/socket; existing developer sessions are preserved.
- Native input: pointer, injected key events and actual target virtual-keyboard touches tested.
  Injected keys have intermittent repeat/missing-byte behavior; physical keyboard and Korean
  IME composition are not yet verified. Native input text is not logged.
- Full SDK redeploy clears app data/secure aliases on this image; it is not a credential
  retention or normal app-launch test. Test key-import sources are removed after successful
  secure import; fixture secrets remain outside the repository/package in ignored host tools.

## Verified increments

| Scope | Evidence | Limit |
| --- | --- | --- |
| Vertical input centering, six Android palettes, exact portal and control icons | Installed editor/settings screenshots; source parity comparison | Not full pixel identity across different platforms |
| SSH/Herdr command input, Unicode/CJK/combining/box/Nerd glyphs | Installed native terminal and own two-pane fixture | Physical font rasterization/input still unverified |
| CSI/DCS/title bounds and recovery | Host regression assertions, actual hostile-byte PTY fixture and valid SGR reply | Not full VT conformance |
| Region insert/delete/scroll and normal/alternate buffers | Host allocation/margin assertions and native blue erase-row/normal-buffer restoration | More resize/font stress remains |
| Secure key/password save/get, replacement, blank edits and deletion | Native password reconnect/failure/changed-host tests and host fault matrix; durable cleanup journal | Target encrypted-key/cancellation and storage-fault UI matrix remain |
| Pane drag, Shift-authorized OSC52, rectangular local copy/paste | Own Herdr pane and actual clipboard/terminal result | Injected secondary mouse and F10 context path not proven |
| Markdown/HTML/Mermaid/draw.io and hostile static HTML | Installed four-format views; external probe and script mutation controls | Broader target symlink/resource/lifecycle matrix remains |
| Background and resume | Same app PID and established SSH tuple after about94 seconds; WM confirms obscured/visible | No unlimited execution/process-death survival claim |

## Acceptance status and platform boundaries

The functional emulator gates below pass, but final SFTP response-reassembly
bounds still require real-wire verification. Dated entries retain both historical
gaps and their later resolutions. Final evidence is indexed in
[acceptance.md](acceptance.md). Physical hardware/right-click/Korean composition,
public-network use and unlimited background/process-death survival are not claimed.
The emulator retains its OS back/home bubble in fullscreen. Herdr auto-context
requires supported Linux/Python/API capabilities and a unique connection/TTY;
ambiguous or unavailable context uses explicit pane/file choice.

No commit, push, store publishing, email or Drive upload is performed by this goal.

## Resource snapshots and appearance

A document owns at most64 attempted SFTP resource reads, each at most1MiB, with at most16MiB
retained in memory. Successfully read resources form a snapshot for that document's lifetime;
reopening the document starts a new snapshot. Cached content cannot expand the approved
canonical directory boundary. Failed reads consume a read slot. Each local light/dark diagram
rerender has its own64-response/16MiB delivery allowance, including cached responses. Only a
native appearance preference change can reset that delivery allowance; remote content cannot.
HTTP request concurrency and native interceptor concurrency remain separately bounded.

The initial theme implementation reloaded the page on each selection. Actual repeated native
runs crashed Chromium's URL-loader IO thread while canceling an intercepted draw.io response.
Removing StopLoading alone also crashed. The current implementation keeps the same page,
updates generated application CSS through native script evaluation and rerenders trusted
local diagrams only when light/dark changes. Static HTML keeps JavaScript disabled and never
contributes document text to native evaluation. Runtime acceptance of this path is recorded
below after the exact-package regression; it is not implied by host assertions alone.

### Historical preview regression — 2026-10-20

Package SHA256: `f78c06045e3786b7031910fe331f80eedeb1d60a76482336dfbc807493a290dd`.
The signed development TPK installed and launched as PID1000422. It completed60 native theme
selections with Markdown, Mermaid and embedded draw.io loaded. The process and SSH socket
127.0.0.1:38322/server child PIDs2687366/2687364 remained unchanged. Scene ownership stayed
bounded (settings48, no retired scenes left queued). This repairs the reproduced theme-reload
crash in this test; it is not a blanket claim about every WebView lifetime transition.

Static hostile HTML retained its SCRIPT BLOCKED heading after native CSS updates. Pixel samples
confirmed Light RGB245/247/250 and Amber RGB21/17/8 in the document background. Its attempted
script mutation and external image/frame/style/script requests did not reach the own loopback
probe (0 requests). Separate host and actual emulator curl positive controls each reached it.
The source file:// and external placeholders stayed blocked; broader boundary tests remain.

After the repeated scene changes, actual virtual-keyboard touches entered exactly `pwd`, and
Send+Enter executed it in the own Herdr pane. Three native Menu/Close cycles returned ownership
to24 controls, no retired scenes. This verifies target touch IME/accessory input, not physical
keyboard behavior or Korean composition. The collector recorded0 crashes during the monitored
stress interval; ordinary EFL input events are logged at E level on this image and should not be
misreported as application exceptions. A separate recursive Dali Emit warning remains open.

Screenshots under `/tmp/hexgate-tizen-evidence/`:

- `preview-inplace-import-1613.png`: current native editor and secure import confirmation.
- `preview-inplace-sixty-themes-1632.png`: document/terminal after60 selections.
- `preview-inplace-html-light-1648.png`: hostile static HTML with native light appearance.
- `preview-inplace-ime-pwd-1654.png`: actual target keyboard and exact input.
- `preview-inplace-amber-ime-result-1657.png`: terminal command result and Amber static HTML.

Receipts and scripts are ignored under `.tools/tizen-preview-inplace-*` and `.tools/tizen-aurum/`.
Accessibility field-setting is used for fixture paths to separate file-preview tests from raw
key-injection jitter; the actual touch-keyboard test remains separate. Raw injection still has
repeat/missing-byte issues under some conditions. No secret text is included in diagnostics.

Direct-format screenshots for the same package: `preview-inplace-drawio-1713.png` and
`preview-inplace-mermaid-1716.png`. Earlier similarly named1703/1706 captures were made after a
helper error and are not format evidence. During later narrow-width transitions, residual
wrapped prompt fragments appeared inside Herdr panes. Refresh comparison is recorded in
`preview-inplace-refresh-1719.png`; Refresh did not visibly remove those fragments in that historical comparison. A later guarded
read of the own remote Herdr pane found the same fragments in its source screen; they were not
invented by local label rendering. Independent resize bugs and full-grid comparisons are recorded
below. Do not infer full terminal fidelity from successful diagram rendering or process survival.


## Native resize and background comparison

Package `37e52ce0afc43df0f780f7de13a5cb230970142e9c5c188e67ed5973caaf752d` repaired alternate-screen
backing cells returning after shrink/grow and half-wide glyphs at the clipping edge, while retaining
completed normal-buffer reflow and explicit margins. A raw PTY fixture generated every row/cell
from the actual server dimensions. All18 native actor-row comparisons matched through drawer,
font20/22/24, split preview, refresh, fullscreen/windowed requests and background transitions.
The PTY recorded zero input bytes during those requests. This is stronger evidence than merely
observing resize calls or screenshots; small physical-window and inset transitions remain open.

A separate fixture painted one CJK-containing row once and never repainted on SIGWINCH. Shrink
cleared the half glyph; growth did not resurrect clipped cells. All3 whole-row comparisons
matched with paintCount1 and stdin0. The completed-output fixture intentionally does not claim
that the upstream normal-buffer active cursor line is reflowed.

Settings was positively focused for32.25 seconds before returning to the same app PID1015732,
SSH peer59736 and fixture PID2753484. Every native grid row still matched. An omitted launcher
listing or a changed accessibility bus alone is not evidence of process death on this image.
The fullscreen screenshot still has the OS back/home bubble, a genuine target limitation.
Receipts: `.tools/tizen-grid-native-results.json`, `tizen-native-wide-clip-results.json` and
`tizen-grid-background-result.json`. Screenshots: `grid-after-resize-2053.png`,
`grid-fullscreen-2058.png` under the evidence directory.

## Historical credential increment — 2026-10-20

That increment TPK SHA256: `7ea92b2a043c5caf4ef2d6c410f570e448fb06f04a51ba908622a4dc72fb7745`.
Built with0 errors/7 existing vendored VT warnings, installed through the SDK runner, launched
as PID1038856. Both manifest IDs are exact. Both RSA/SHA512 signatures and all65 author/66
distributor reference digests were checked independently; certificate trust remains emulator-only.
A full archive scan excluded the exact own private keys/password/passphrase fixture bytes.

The native app passed password save/reconnect, blank-edit retention, replacement, rejection of
an incorrect password and correction/reconnect. Changing the own server's host key produced the
explicit warning. Rejecting it yielded0 password authentications/0 shells; explicit approval
then connected. Deletion completed with0 pending native key-manager revocations. The app does
not preload stored passwords into the editor. See [credential recovery](credentials.md) for the
write-ahead cleanup protocol and the distinction between host injected failures and native tests.
The app now shows a fail-closed native error page if startup storage/profile initialization fails;
that exceptional page still needs a target fault-injection UI check.

Screenshots: `secure-password-editor.png`, `secure-password-connected-verified.png` and
`secure-changed-host-warning.png` under `/tmp/hexgate-tizen-evidence/`. The earlier
`secure-password-connected.png` caught a fixture-only resize exception and is not connected-state
evidence. The corrected fixture catches terminal-size notifications; subsequent checks stayed
connected. It is an SSH authentication/PTY fixture and intentionally executes no host commands.

The bounded trailing100-entry error excerpt contains30 entries for PID1038856, including14 Dali
recursive Emit warnings and ordinary EFL input logs, with0 own crash markers in that excerpt.
This is not a whole-run crash-free claim. Recursive signal warnings remain an investigation item.
No Android/reference changes, commits, pushes, publication, email or Drive upload occurred.


## Current lifetime increment — 2026-10-20

Current signed TPK SHA256:
`f6148416a4abe0814b7520f83d235a570db43731910a7c3ad67f855651ee23af`.
Both IDs are unchanged. Build:0 errors/7 existing VT warnings. SDK install job
20261020T123419-d9eee3 completed; the initial actual app PID was1050307. Both RSA/SHA512
signatures and all65/66 reference digests passed independent verification. Archive checks
excluded test assemblies/metadata and the exact own password/passphrase/private-key bytes.

Input now has byte AND command credits, plus a reserved coalesced geometry slot. The prior
512 one-byte writes reproduced the missing command-count limit. A separate live test reproduced
viewport updates during host verification being overwritten after authentication; the actual
server now reports29 rows/113 columns as requested. An intermediate parser-disposal edit also
broke normal Reset; the existing DCS-reset regression caught it, and the corrected final suite
passes. No failing/intermediate log is used as current acceptance evidence.

Portable108, OpenSSH/SFTP/lifetime134 and authentication118 assertions pass. The live tests hold
an output callback/host-verification callback while multiple disposal callers wait, verify no
late output after completion, and race SFTP opening with retirement. Status bursts10,000 messages
occupy one pending callback, and retired producers cannot overwrite a current pending message.
See [session ownership](session-lifetime.md) for the exact contract and the still-open dependency
read-buffer boundary; this increment does not claim every queue in SSH.NET is now bounded.

Native normal-exit evidence is independent of launcher-list omissions. Close Hexgate removed
/proc/1050307/stat and reduced the own server's active connections to0. Ordinary SDK launch,
without reinstalling, created PID1050546 and reconnected the saved password. A second normal
exit removed/proc/1050546/stat; launch created PID1051501. The second verification performed no
clicks or credential entry and rejected any host-verification prompt, proving both the saved
password and host trust survived. Receipt: tizen-normal-relaunch-native-run2.json. The initial
fixture automation found no editable fields after its first click; that helper attempt was
discarded. Subsequent steps waited for actual editable fields and connected-state labels. The
initial focus/scene timing was not independently established as a product defect or root cause.

Twenty native Light/Dark chrome changes retained the same app/SSH connection and server shell
counts. Deferring button work out of its active native signal eliminated recursive Emit warnings
in this tested interval (0 for PID1050546; the older credential increment had14 in its excerpt).
This is not proof for every pointer/WebView/window callback. The saved final bounded error ring
contained2107 entries,43 for PID1051501, with0 own recursive Emit/crash markers. Framework startup
VCONF/RPC/EGL warnings remain; the trailing100 lines alone contained no current-PID entries and
were not used to support the broader review. Evidence: tizen-lifetime-final-log-review.json.

Screenshot: `/tmp/hexgate-tizen-evidence/lifetime-retained-password-no-prompt.png`; also
lifetime-normal-relaunch-connected.png. The final test profile/credential was deleted with no
pending cleanup, the app closed normally, and only the own loopback auth server was stopped
after it had0 active SSH connections. The pre-existing OpenSSH/Herdr/developer sessions remain.
Android/reference source trees are unchanged. The full goal is active; no commit/push/publication.


## Preview lifetime and adaptive runtime follow-up — 2026-10-20

- Added a 32-work-item background owner for preview SFTP reads, candidate-server adoption
  and native HTTP interception. Shutdown rejects later work and joins all admitted workers.
  Native interception holds one of eight credits through UI delivery/discard, bounding queued
  response bodies as well as HTTP work. Retired/terminated candidate servers cannot be orphaned
  by DispatchUi dropping an adoption callback; ownership is explicitly accepted or disposed.
- PreviewServer now exposes one shared cleanup task for all disposal callers. Its listener,
  accepted requests and synchronization resources retire in order. Response-close failures still
  release request slots. The original idle-disposal probe happened to pass before this change;
  it did not reproduce an in-flight premature-return race. The shared-task/work-group tests
  establish the new contract without claiming that idle probe was red evidence.
- Final portable assertions141; OpenSSH/SFTP/lifetime167; independent authentication151. Each
  integration count includes the same141 portable assertions. The new eight assertions cover
  work capacity, join semantics, rejected late work, fault observation and listener disposal.
  Exact logs: tizen-preview-lifetime-final-ssh-tests.log and -final-auth-tests.log under .tools.
- Built zero errors/seven existing vendored warnings. Final signed TPK SHA256:
  dbfea15bd1c016de68961c76002fa391bde3dc5c8ea0e328f982340f0accb6c5.
  Installed through SDK job20261020T133317-5a3b9d, launched native PID1068891 on emulator-26111.
  Both RSA/SHA512 signatures and65/66 reference digests verified. ZIP/manifest IDs checked and
  exact own private-key/password/passphrase bytes plus test assemblies excluded.
- A fresh jailed SFTP fixture supplied only synthetic documents. Actual native Markdown with
  embedded Mermaid and draw.io, static HTML, standalone Mermaid and draw.io were rendered and
  screenshots inspected. HTML retained STATIC HTML FIXTURE instead of the injected script's
  replacement text. Light/Dark changes retained the draw.io preview; normal Close Hexgate with
  that preview active removed /proc/1068891/stat and left fixture active SSH connections0.
  Screenshots: /tmp/hexgate-tizen-evidence/preview-lifetime-final-{markdown,html,mermaid,drawio}.png.
- Saved bounded error ring1052 lines contained117 entries for PID1068891, with0 own crash markers
  and0 own recursive-signal markers. Framework/RPC/WebView warnings remain. This proves only the
  captured ring, not an entirely warning-free lifecycle. Review JSON is ignored under .tools.
- Earlier adaptive native evidence was re-read: real IME reduced roots1080 to719; a native form
  pan moved Save from742 to592. Viewport math covers narrow/4K/insets, but native narrow-width
  validation and the latest explicit D-pad change still need verification. No fake compositor
  resize or emulation-only arithmetic is presented as native narrow-window evidence.
- Full goal remains active: incoming SSH ShellStream backpressure, remaining native auth/fault
  matrix, additional WebView retirement races, native narrow-width/D-pad regression, debug
  instrumentation removal and complete final acceptance remain. Android and reference source
  were preserved. No commit, push, publication, email or Drive upload was performed.


### Bounded SSH receive credit and native key reconnect — 2026-10-20

- Previous goal turn was progress: preview work ownership, signed package and four-format target
  verification. This increment reproduced an unbounded ShellStream receive buffer using the real
  isolated OpenSSH fixture: an unread terminal exceeded the intended1MiB bound. The old NuGet
  binary fails ReceiveFlowChecks. A later exact-byte probe initially included a leftover shell
  prompt; adding a bounded start marker fixed that fixture without relaxing the payload assertion.
- Imported SSH.NET2026.0.0 at7b2fd3dbf2c86a80a7b06cea020aa5f821c9902e, matching the NuGet package's
  recorded commit.337 source files remain unchanged; six documented source modifications cover
  bounded shell/channel credit and one equivalent C#12 null-check compatibility edit. The old
  project is replaced by a net8-only project, with nine empty net8 compatibility shims excluded.
  Cryptography remains upstream. MIT license, adaptation notice, patch, original/patched hashes
  and a source-verification command are retained. No upstream private signing key is imported.
- Shell channels advertise1MiB before opening and use a fixed1MiB read allocation. Every read/
  expect/line discard returns coalesced consumed credit. Normal/extended output share that budget.
  The SSH message listener never waits for the terminal consumer. Other channel types retain
  automatic adjustment; this is not a universal forwarding/SFTP-channel memory quota. The adapter
  may additionally hold its active16KiB output callback outside the shell window.
- Actual OpenSSH checks now173 assertions (141 portable +32 live). New checks prove unread bound,
  another command on the same saturated SSH transport, exact8MiB payload delivery, repeat flood,
  disposal while full and extended output. Independent correct/incorrect password/key/encrypted-
  key/changed-host/cancellation checks151 (141 shared +10 auth). Shared counts are not additive.
  Logs: .tools/tizen-receive-flow-final-tests.log and tizen-receive-flow-auth-tests.log.
- Native normal-restart checking found key selection always displayed an unnecessary passphrase
  dialog, preventing direct last-profile connection for unencrypted keys. The target regression
  failed before the fix. Selection now attempts authentication off UI and only the typed encrypted-
  key exception opens a masked passphrase dialog, after cleanup and ownership recheck. No silent
  authentication-method fallback was added. Ordinary key auto-connect now survives normal exit/
  launch without credential or trust entry. Wrong encrypted-key passphrase fails safely; correct
  separate passphrase connects and executes NATIVE_ENCRYPTED_OK on the target.
- Final TPK a124e5065866fbc44184be3b3e4d204823e9dfc435ca01a905ad04e18cafedb7, zero build errors/
  seven existing VT warnings, installed via SDK job20261020T134902-1804c6. Emulator-26111 remained
  connected; final app PID1074057, ordinary relaunch PID1074228. Actual2MiB terminal output reached
  its completion marker and survived Light/Dark scene changes. Native Down moved focus from the
  menu to the connection drawer; broader D-pad/small-width acceptance remains open.
- Both RSA/SHA512 signatures and68/69 reference digests verified. ZIP/IDs, exact adapted assembly
  bytes and all discovered own fixture private-key/password/passphrase exclusion checks passed.
  No test/build-only assemblies were packaged. Final screenshot:
  /tmp/hexgate-tizen-evidence/receive-flow-final-clean-terminal.png. The older receive-flow-native.png
  belongs to intermediate9181aaa6... and is not final-package screenshot evidence.
- Deleted only the own native test profile and its secure credential with no pending cleanup,
  removed both native import files and closed normally; /proc/1074228/stat no longer exists.
  Saved bounded error ring1390 lines contained9 entries for1074057 and35 for1074228, with0 own
  crash/recursive markers for each. Framework warnings remain; no full-lifetime clean-log claim.
  Earlier native test automation omitted Connect in the deliberately shown key-unlock dialog;
  that attempt was corrected and is not evidence of a product authentication failure.
- Full goal remains active: remaining native storage/cancellation/fault checks, broader WebView
  retirement/hostile-boundary and small-width/D-pad/input regressions, debug instrumentation
  removal and full final acceptance remain. Android/reference sources and existing developer
  sessions were preserved. No commits, pushes, publication, email or Drive actions.

- Final screenshot handling: the first encrypted-login capture included the SSH server's prior-
  login banner. Its untracked image/encoded receipt were discarded. A fresh actual viewport was
  captured after the fixture command cleared it to NATIVE_FINAL_OK; no image editing was used.
  The temporary screenshot-only profile/secure key was then removed and the app closed normally.
  The final signed package was unchanged by this documentation/evidence-only step.

- Final cleanup receipt confirms /proc/1074578/stat is absent. The last bounded error-ring
  review covered1573 entries:0 for1074057 (already aged out),9 for1074228 and35 for1074578;
  no own crash/recursive markers were present in those captured entries. Only own-PID entries
  are retained in the local ring excerpt; prior reviewed rings and this rolling snapshot are
  distinct scopes. OpenSSH fixture PID2481334 remains untouched; the temporary auth fixture
  reached its configured30-minute lifetime and is no longer listening. No restart was inferred
  from a polling timeout or performed unnecessarily.


### Native preview boundaries and stalled-peer shutdown — 2026-10-20

- Previous goal turn made concrete progress on bounded SSH receive credit and key reconnect.
  This increment leaves the production application/TPK unchanged and strengthens acceptance
  evidence for the exact signed a124e5065866fbc44184be3b3e4d204823e9dfc435ca01a905ad04e18cafedb7.
  Installed package info resolves org.tizen.hexgate under /opt/usr/globalapps; App/Core/SSH DLLs
  pulled through the dedicated SDK file-transfer runner match the TPK byte for byte.
- Added9 real HTTP-origin regression assertions: POST/PUT/HEAD/OPTIONS rejection, wrong capability,
  forged Host and three encoded/traversal routes. OpenSSH/SFTP/lifetime/receive/origin mode now182
  (141 shared +41 integration assertions); independent authentication151. Exact logs are
  .tools/tizen-preview-boundary-host-tests.log and tizen-preview-boundary-atomic-auth-tests.log.
- Added checked-in synthetic HTML/CSS/SVG boundary fixtures and filename-free SFTP open counters.
  The actual native static-HTML page retained STATIC_BOUNDARY_OK and rendered its normal green
  stylesheet/image. Screenshot preview region contained12752 green pixels and0 forbidden red
  pixels. Exactly3 SFTP opens occurred (document/CSS/allowed SVG), with0 private-sibling opens.
  Symlink, parent resource and a real synthetic app-local file were blocked. No production
  files were served. Screenshot: /tmp/hexgate-tizen-evidence/preview-boundary-static.png.
- A positive emulator HTTP control reached the own loopback probe. During the hostile page's
  observation interval its count remained control1/unexpected0, covering external image/CSS,
  iframe/object, script/event-handler fetch/window and meta-refresh attempts in this fixture.
  The hostile heading stayed unchanged. This is targeted static-HTML evidence, not a claim
  about every possible diagram-renderer input, race or browser vulnerability.
- After recording native counters, a separately pinned host SFTP control opened the synthetic
  private SVG and increased the private-open counter, proving the native0 was meaningful. Its
  initial statistics read raced a truncate/write; the observer reread/retested successfully and
  future fixture stats now use atomic replacement (new fixture authentication151 passes).
  Early generic control connections also attempted default public-key identities, which the
  fixture rejected. The final control explicitly disables implicit keys/agent and restricts
  auth to the fixture password; no additional key attempts occur. Native SSH auth remains explicit.
  The private-open count after host controls is intentionally no longer0; native results are
  saved before those positive controls and must not be confused with them.
- Native PID1075796 opened a file while only the own fixture process was SIGSTOPed. Its sockets
  held160 queued SSH request bytes. Picker dismissal/Settings/Close Hexgate stayed responsive;
  the app PID disappeared in4.38 seconds while the peer was still stopped. A finally block
  SIGCONTed the exact same validated fixture PID. No timeout-driven restart, developer session
  change or global network manipulation was used. This exercises pending SFTP/preview retirement
  and the normal Main cleanup join on the actual target.
- Ordinary relaunch PID1077649 retained secure password/host trust without entry prompts. Deleted
  the own profile/credential with no pending cleanup, removed the synthetic native file and
  closed normally. A hash-check request was misrouted by the SDK natural-language matcher to an
  extra own app launch; that receipt was not used as hash evidence. Dedicated file transfer
  supplied the actual DLL comparison, and the extra Ready-state process was closed normally.
- The bounded error ring2273 entries included15 entries for1075796 and29 for1077649, with0 own
  crash/recursive markers each. Framework warnings remain. Own-PID excerpts and review JSON are
  ignored under .tools. Both own SSH fixtures were stopped at0 active connections and the own
  HTTP probe was stopped; only the pre-existing OpenSSH fixture on22222 remains listening.
- Full goal remains active. Native small-width/adaptive and broader D-pad/input/background/final
  exact-package regression, remaining cancellation/storage fault cases, renderer-format hostile
  tests, lifecycle edge cases and debug-instrumentation cleanup still need completion. Android,
  read-only reference apps and existing developer sessions were preserved. No commit/push/store,
  email or Drive operation was performed.


### Native input focus and adaptive UI verification (2026-10-21)

- Input fields use centered vertical alignment, horizontal padding, and Android-style
  rounded surfaces. Chrome uses the Android theme colors and icon assets.
- Fixed the focus-search root to include the physical terminal sibling after the
  software keyboard closes. Native red/green verification showed Ctrl+C delivery
  restored without an additional terminal tap. Modal focus remains scoped.
- Current emulator TPK SHA-256:
  `11df69036d0abe3921d52b8609af3e3606b42151dd27f54fadc19f73fb55b9be`.
  Build succeeded. Author/distributor RSA signatures and all 68/69 reference
  digests were verified against this package using SDK test certificates.
- Actual own-window compositor sizes 800x600 and 480x900 were tested without
  changing the desktop resolution or another emulator. Connection editor and
  settings footers were reachable by native scrolling; Light/Dark selection
  worked, portrait host/port fields stacked, and remote input remained unchanged.
  Fullscreen changed 480x900 to 1920x1080 and returned to 480x900 on exit.
- Exact native input checks covered rectangular ALPHA/DELTA copy/paste,
  unsolicited OSC52 rejection, remote Ctrl+C/Ctrl+V, Tab, Enter, Up, native
  keyboard Menu context copy/paste, and software-keyboard pwd plus Enter.
  The raw observer reported zero unknown bytes. Physical mouse right-click
  and F10 injection are not established by these checks.
- After 32 seconds iconified, the same app PID and SSH shell accepted a new
  synthetic command. Refresh preserved its marker. This verifies iconified
  background operation, not OS suspension or process-death recovery.
- Native accessibility checks were scoped to the live window provider because
  a second provider exposed stale dimensions. No separate emulator was needed.
- Broader goal regression and temporary debug-instrumentation cleanup remain
  open. This UI follow-up does not mark the full port goal complete.


### Instrumentation cleanup and exact-package regression (2026-10-21)

- Removed all temporary input, pointer, clipboard-menu, scene and paint timing
  instrumentation from the NUI application, including InputDiagnostics.cs.
  Session cleanup still reports a generic failure without terminal content.
- Rebuilt and installed the signed emulator TPK. Current SHA-256 is
  `8e80346188d5712ee3e8f75bbe44d4f07b231a8ba12eaa40ebe759dfd91ee48c`.
  ZIP integrity, exact manifest IDs, author/distributor signatures and their
  68/69 reference digests passed. Fixture filenames and certificate private
  bundles are excluded. SSH.NET pinned-source verification passed.
- Portable tests: 141 PASS assertions. SSH/SFTP/receive/origin integration:
  182 PASS assertions, including ordered lossless 8MiB receive and bounded
  receive/disposal cases. Logs: .tools/tizen-final-clean-tests.log and
  .tools/tizen-clean-ssh-tests.log. Initial concurrent builds collided on a
  shared deps.json; the failed build was terminal, then rerun sequentially.
- Exact installed native PID1096270 passed rectangle clipboard shortcuts,
  unsolicited OSC52 rejection, remote Ctrl+C/V, Tab/Enter, input-dialog pwd
  commit and focus restoration without a terminal tap. The observer counted
  zero unknown bytes. This final dialog check used accessibility text entry;
  physical software-keyboard touches were verified on the preceding build.
- Menu context buttons were present. An initial automation assertion named a
  nonexistent heading; inspecting and asserting the real Copy selection/Close
  buttons corrected the selector. No product change was needed.
- Refresh preserved the synthetic terminal frame. Screenshot:
  /tmp/hexgate-tizen-evidence/clean-terminal.png. Visually inspected.
  The OS back/home bubble remains a measured fullscreen limitation.
- Bounded error-ring review:1267 entries,16 matching this PID,zero crash,
  recursive or unhandled markers. Native framework warnings remain.
- Own fixture profile/secure credentials were deleted; the observer was
  released, its imported staging key removed, and normal app exit requested.
  Existing OpenSSH master and developer sessions were preserved.
- The full goal remains active: complete the final all-format/Herdr regression
  and remaining acceptance audit. Historical package checks are not treated
  as evidence for unchanged final-package UI paths without revalidation.


### Final-package previews and authentication follow-up (2026-10-21)

- Current package remains `8e80346188d5712ee3e8f75bbe44d4f07b231a8ba12eaa40ebe759dfd91ee48c`.
  Actual PID1096373 opened Markdown, static HTML, standalone Mermaid and draw.io
  over password-authenticated jailed SFTP. All four screenshots were visually
  inspected; Markdown visibly includes both Mermaid and embedded draw.io, plus
  Korean text. Native Light/Dark changes retained the active draw.io preview.
- Screenshots in /tmp/hexgate-tizen-evidence/:
  preview-clean-final-markdown.png, preview-clean-final-html.png,
  preview-clean-final-mermaid.png, preview-clean-final-drawio.png.
  The script's inherited final log line incorrectly said exit requested; the
  actual action was Settings/Done. The helper text was corrected. This is not
  shutdown evidence; the same live app continued encrypted-key testing.
- Fresh loopback authentication protocol checks passed151 assertions, including
  correct/incorrect passwords and keys, separate encrypted-key passphrases,
  changed-host rejection and cancellation. Log:.tools/tizen-final-auth-tests.log.
- Native encrypted-key import and unlock cancellation produced no additional
  key authentication or shell. A subsequent helper tried to choose a profile
  while the drawer was closed; that failed selector is not a product failure.
  Remaining native retry checks continue from the observed current UI state.

- Continuation result: the native encrypted-key matrix passed cancellation,
  wrong-passphrase rejection without key authentication, and correct-passphrase
  connection. Counters were compared against a fresh baseline after the host
  protocol suite ended, so host authentications are not credited to the UI.
  Evidence:.tools/tizen-final-encrypted-results.json and
  .tools/tizen-final-encrypted-resume.log. No passphrase value was printed.

- Normal exit removed PID1096373; ordinary relaunch created PID1097335 and
  requested the encrypted key's separate unlock passphrase without reimport.
  The first20-second observer expired before the prompt became observable;
  inspection of the same live process found it. No restart followed the timeout.
  An attempted empty-field check used accessibility display text (which includes
  placeholder/label metadata), so it is not evidence about stored input values.
  Source initializes the passphrase to an empty local value; no passphrase
  retention claim is inferred from that failed accessibility check.
- Final archive scan excluded exact own fixture key/password/passphrase bytes.
  Bounded error-ring entries for1096373/1097335 were42/53, with0 crash,
  recursive or unhandled markers. Generic platform warnings remain.

- Own profile/secure credentials were deleted, native import source removed,
  PID1097335 exited normally, and the exact own auth/SFTP fixture was stopped
  after its active connection counter reached0. Existing developer sessions
  and the pre-existing OpenSSH fixture on22222 were preserved.


### Final native Herdr regression and acceptance gap (2026-10-21)

- Current signed package8e803461... launched PID1098589 against a fresh private
  Herdr config and named hexgate-final-test session. Only commands inside that
  own pane passed HERDR_ENV/socket-prefix guards; no developer session controls
  were issued. Native input executed a real own-pane split command.
- Visually inspected herdr-final-two-pane.png and herdr-final-verified.png under
  /tmp/hexgate-tizen-evidence/. CJK, combining acute, box drawing and Nerd glyphs
  rendered. Refresh and Smaller/Larger restored the live frame. CJK text uses
  separate wide-cell actors; the initial contiguous-label assertion was wrong,
  so checks were corrected to examine the actual actors plus rendered image.
- Ordinary tracked drag did not authorize clipboard output. Shift drag copied
  exactly ALPHA BRAVO CHARLIE/newline/DELTA from the own left pane. Pasting into
  its raw observer matched37 bracketed-paste bytes with0 unknown bytes and no
  adjacent-pane prompt. Evidence:.tools/tizen-final-herdr-selection.json/log.
- Initial input helper geometry lookup failed after IME inset changes; selecting
  the sole actual text field resolved it. The first command targeted Herdr's
  own sidebar/settings focus; Escape and a native pane click focused its shell,
  after which the guarded setup command executed. Neither attempt is credited
  as command-execution success.
- Own Herdr session was stopped by its guarded in-pane script. Its redirected
  stop receipt was empty because stopping the session terminated its own pane;
  it is not valid JSON success evidence. Independent /proc environment checks
  found0 processes belonging to the private XDG config. Own credentials/profile
  and staging key were removed; /proc/1098589 disappeared after normal exit.
  Final error ring1465 entries contained10 matching PID entries and0 crash,
  recursive or unhandled markers. Existing developer sessions were preserved.
- Final audit found a required integration gap: TerminalScreen captures this
  client's SSH TTY, but RemotePreview never uses it or the named Herdr profile
  to resolve pane context. Android's read-only herdr_context.py does implement
  the unique before/after-client/TTY guard and explicit pane fallback. The
  current Tizen manual file browser works, but this missing wiring must not be
  described as an unsupported target capability or completed context feature.
- Next implementation: reuse the read-only context protocol, retain explicit
  pane/file choice when ambiguous, and execute it through SSH.NET's existing
  CreateShellStreamNoTerminal bounded receive path. Do not introduce the
  unbounded SshCommand PipeStream for an untrusted snapshot. Add timeout/output
  limits and session-retirement ownership, then test unique/multiple clients
  and verify the resulting signed package. Goal remains active.


### Guarded Herdr context implementation (2026-10-21)

- Added portable pane/context parsing and literal command construction. Session,
  SSH TTY, pane paths/counts and1MiB UTF-8 response limits are validated. The
  packaged read-only helper accepts automatic focus only for stable unique
  clients matching both this TTY and SSH_CONNECTION. It derives the named
  client's private XDG configuration and does not query unrelated configurations.
  Multiple clients produce explicit pane choices; unavailable identity offers
  the existing manual file browser. Environment contents are never returned.
- Added a bounded no-PTY shell query, serialized independently of terminal input.
  It uses the existing fixed SSH receive window,20-second query cancellation,
  output limit and joined read/write cleanup. Synchronous channel creation stays
  off the UI thread and retains the underlying connection's open timeout.
  Session disposal closes and joins an active context channel before retirement.
- ShowPreview captures profile/TTY before background work and rejects stale
  generation/overlay callbacks. A verified unique pane browses its cwd; ambiguous
  snapshots show a paginated native pane picker plus explicit home/file fallback.
- Real SSH tests initially reproduced an unflushed probe command timing out.
  Adding Flush fixed the actual protocol loop. Current OpenSSH/SFTP/receive/query
  suite passes198 assertions (151 shared plus47 integration). Authentication
  suite passes161 (151 shared plus10 protocol). Eight helper tests pass, including
  real subprocess output-limit/timeout and wrong-TTY/SSH-connection/config guards.
- Managed NUI build passed. Initial compile used an incorrect field name; it was
  corrected to terminal before packaging. An initial signed package included
  host-generated Python caches. Source Content and staging now exclude caches,
  helper tests suppress bytecode writes, and the corrected archive contains none.
- Current signed TPK SHA256:
  751f1d7998f54e614bce25145860e11b4d767e071878818455d3c8c731830d35.
  Author/distributor signatures and70/71 reference digests passed; ZIP, exact IDs
  and own fixture-key exclusion passed. Installed job20261020T152955-c87270,
  actual PID1104998 on emulator-26111. Earlier80d18e2f... is superseded.
- A separate own OpenSSH fixture on22224 uses a private XDG directory; the
  pre-existing22222 master and developer sessions remain unchanged. Native
  unique-client verification opened the actual changed own pane cwd and showed
  NATIVE_CONTEXT.md, not the SFTP home fallback. Initial setup input arrived
  while Herdr sidebar/settings held focus; after Escape and a native pane click,
  the same process accepted the command. No timeout-driven restart was used.
- Evidence:.tools/tizen-context-native-unique-result.json. Multiple-client native
  picker verification is running; final affected preview/retirement checks and
  cleanup remain before completion. The goal remains active.

- Native multiple-client result passed: a second host SSH client attached only to
  the private hexgate-context-test session. The installed app showed Choose
  Herdr pane instead of auto-selecting global focus. Explicit pane selection
  opened its cwd and rendered NATIVE_CONTEXT.md. Screenshot
  /tmp/hexgate-tizen-evidence/context-multiple-pane-choice.png was visually
  inspected. Result:.tools/tizen-context-native-multiple-result.json.
- Fresh authentication fixture passed161 assertions and was stopped at0 active
  connections. Native four-format regression on the current751f1d... package
  is running through the new multiple-client pane-picker path.

- Current751f1d... package completed all four document formats through the new
  multiple-client picker, including embedded Mermaid/draw.io and theme changes.
  Screenshots context-final-{markdown,html,mermaid,drawio}.png were inspected.
  Deleting the own secure profile retained the active draw.io view; normal Close
  Hexgate removed PID1104998. A helper initially asserted the old status text
  after deletion; checking the actual preview path corrected that selector.
- Native corrupt-metadata startup displayed the fail-closed explanation and a
  sole Close control with no authentication fields/actions. The original own
  empty metadata was restored byte for byte, and normal Ready startup recovered.
  Early helpers incorrectly rejected the legitimate Close button; those failed
  zero-button assertions are not product failures. The final native Close path
  exited normally. Screenshot:storage-fail-closed.png. Result and restoration
  receipts are under .tools/tizen-storage-*. No plaintext fallback was used.
- Final bounded error review found0 crash/recursive/unhandled markers for
  PID1104998 and storage fault/recovery PIDs1105241,1108160,1108451,1108772;
  matching entry counts12/12/14/23/60. All own target PIDs exited. Own Herdr
  server/peer and private22224 listener were stopped; authentication22223 fixture
  stopped at0 connections. Imported key source removed. Original master2481334
  remains alive and Android remains identical to main. No commit/publication.
- Final archive recheck passed SHA256, signature coverage of every payload,
  current App/Core/SSH DLL equality, and all checked own fixture-secret/cache
  exclusions. Temporary input/scene/paint diagnostics remain removed.
- Completion audit is NOT passed yet. Source inspection identified an unverified
  used-SFTP receive path: SftpSession.OnDataReceived appends fragments to an
  ArrayBuffer without a demonstrated SFTP packet/reassembly cap. Application
  document/resource limits apply later. This is separate from the proven fixed
  terminal/no-PTY ShellStream receive bound. Next: a real-wire malformed SFTP
  length/flood regression and measured dependency behavior, then a bounded fix
  if red. Do not infer failure/success solely from source or mark the goal done.
  README/acceptance status was corrected before any goal-complete claim.


### Final candidate native authentication, previews and stalled shutdown

- Current be86d3f3... package, native PID3525: four preview formats rendered and
  were visually inspected, including Markdown's embedded Mermaid/draw.io. Native
  Light/Dark changes retained the draw.io view. Initial copied fixtures omitted
  their pixel.png, yielding a broken-image control; copying that fixture asset
  and reopening Markdown/HTML corrected it without a product change. Screenshots:
  /tmp/hexgate-tizen-evidence/sftp-boundary-{mermaid,drawio,resource-markdown,resource-html}.png.
- Native encrypted-key cancellation sent no key authentication or shell. Wrong
  passphrase failed before key authentication; correct passphrase connected. The
  first helper tried selecting a profile while the drawer was closed; it failed
  before another authentication. A drawer-aware helper continued the same app.
  Evidence:.tools/sftp-boundary-encrypted-{native,resume}.log and results JSON.
- Native password mode, blank edit retention, replacement, wrong-password
  rejection and recovery passed. After ordinary normal exit/relaunch the saved
  password reconnected without reentry. Evidence:.tools/sftp-boundary-password-native.log.
- Hostile static HTML showed its unchanged green heading and allowed green SVG.
  Exactly3 native SFTP opens (document/CSS/SVG),0 private-file opens and0 external
  requests; the emulator HTTP positive control counted1. Independent pinned
  SFTP subsequently read the protected sibling and incremented its counter.
  Evidence:.tools/sftp-boundary-hostile-result.json and private-control.log.
- With an actual SFTP request queued at the exact SIGSTOPped own auth peer, normal
  Close Hexgate was requested. Its initial /proc absence check was later
  invalidated because this VM hides even live app /proc entries from the shell. The finally block
  resumed the same peer; that initial absence is not authoritative shutdown evidence; a launcher-list
  regression will replace it.
  Exact timings/queued bytes:.tools/sftp-boundary-stalled-result.json.
- Metadata pull for an optional host-mismatch probe failed due to protected app
  data on this VM; no metadata mutation occurred. Rather than change device
  permissions, testing rotates the actual own SSH server key on the same port.
  The old peer was stopped only after its active counter reached0.

- The optional metadata mismatch helper did not run because the pull failed; an
  ordinary relaunch instead proved secure password retention/reconnect. Then
  disconnected the own peer, waited for active0, stopped it and started a fresh
  fixture on the same port with a new actual host key. Native warning displayed
  the new pinned fingerprint; Reject caused0 accepted/rejected password attempts
  and0 shells on the new server. Own native profile/secure credential deletion
  passed; the rotated peer stopped at active0.
  Evidence:.tools/sftp-boundary-reject-host.log and rotated-result.json.

- Shutdown evidence correction: the shell cannot stat the current live PID3590
  although app_launcher -S lists it. Therefore the earlier2.437s /proc-based
  absence receipt cannot prove exit and is excluded from acceptance. Repeating
  stalled-peer shutdown with an authoritative before/after app-launcher list.
- The second private Herdr SSH client first exited because its headless PTY had
  a zero-sized grid. A confirmed terminal exit was followed by a client with
  explicit100x32 PTY dimensions. Two exact named clients are now live; the first
  multi-client query raced that setup and is not credited. Requerying the same
  installed app, without restarting it.

- Current native input (PID3590) passed rectangle clipboard and unsolicited
  OSC52 rejection, remote Ctrl+C/V/Tab/Enter, input-dialog pwd commit and focus
  restoration. The observer recorded0 unknown bytes. Actual2MiB output reached
  NATIVE_BOUNDARY_FLOW_DONE and survived Smaller/Larger plus Refresh.
  Evidence:.tools/sftp-boundary-input-{native.log,results.json}.
- Native unique-client context opened the changed own pane cwd; the actual
  second live client caused the explicit pane chooser and its selected Markdown
  opened. Unique/multiple result JSON and logs use sftp-boundary-context prefixes.
  The chooser screenshot was visually inspected. The helper was resumed after
  correcting Herdr shell focus on the same app/session.
- Cleanup CLI syntax was read inside the guarded own managed pane. An initial
  absolute-path helper input lost its leading slash while Herdr focus changed;
  the shell's explicit no-such-file output is not success. A focused subsequent
  sh invocation executed the guarded helper. The named own-session stop ended
  both Herdr clients/server; its outer SSH shell remains until Disconnect.


### Remaining-goal acceptance closeout — 2026-10-21

- Authoritative stalled-peer rerun passed: app_launcher -S confirmed present
  before Close and absent after Close while96 SSH/SFTP request bytes remained
  queued at the SIGSTOPped exact own peer. Elapsed2.101s; finally resumed the same
  peer, which reached active0. This supersedes the invalid /proc absence probe.
- Ordinary relaunch for cleanup did not reconnect (auto-connect disabled for
  this cleanup). An early helper toggled the drawer before checking its ready
  state; no deletion is credited to its empty search. Explicitly opening and
  waiting for the actual drawer exposed the sole Boundary shutdown profile;
  Delete credentials succeeded and Settings showed no pending cleanup. Restored
  auto-connect on with no profiles, then normal Close removed Hexgate from the
  authoritative launcher list. Native cleanup PID6283 had95 current error-ring
  entries with0 fatal/recursive/unhandled markers; PID3590 had35/0. Earlier
  bounded excerpts for PID3525 were reviewed separately; final ring rolled them
  out. Framework warnings remain.
- Own Herdr clients/server were stopped by a socket-prefix/HERDR_ENV guarded
  command inside their pane; Disconnect retired the remaining outer SSH shell.
  Private XDG config process count0, peer SSH terminal confirmed ended, and own
 22224 listener stopped. All own auth peers stopped at active0, HTTP probe
  stopped, own Aurum bootstrap and49161 forward removed. Pre-existing22222
  listener remains alive. New staging key files and protected fixture were
  overwritten with zero-byte files using the SDK; protected metadata was never
  edited and device permission/root settings were not changed.
- Current be86d3f3... package remains unchanged. Updated root/Tizen READMEs,
  TEST_REPORT, acceptance, validation, lifetime/provenance docs and progress.
  Remaining goals1-3 pass; goal4 is the documented separate physical-device
  follow-up. No commit, push, store publication, email or Drive operation.

- Final secret scan included all newly generated fixture passwords/passphrases/
  private keys and the original OpenSSH client key; none occurs in the unchanged
  TPK. With no own fixture processes left, removed only generated secret files
  in the explicitly recorded private temp roots. Original OpenSSH key/server
  were preserved. Evidence:.tools/sftp-boundary-secret-cleanup.json.
- Final independent audit rechecked the exact archive hash/DLLs, every native
  result JSON, test totals, author/distributor signatures and notices for all
  redistributed runtime packages and renderer/font assets. SHA256SUMS passes.
  Evidence:.tools/sftp-boundary-final-audit.json.
