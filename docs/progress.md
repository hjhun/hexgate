# Development status

Current remaining-goal status (2026-10-21): emulator goals1-3 passed. See
`tizen/docs/acceptance.md` for the final be86d3f3... package audit. Physical-device
validation is a separate follow-up. Earlier active/open entries below are historical.


Current application version: **0.1.11**, Android version code **12**.

## Implemented

- Kotlin/Compose adaptive shell with a native Termux-derived terminal and SSHJ PTY/SFTP.
- Profile management, encrypted key import/storage, memory-only passphrases, explicit host trust.
- Android IME/hardware-keyboard input, CJK fallback, Nerd Fonts, and seven appearance choices.
- Application-owned active session, foreground connection service, permission prompts, display refresh.
- Fold/cover/rotation-safe native view ownership and latest-size PTY updates.
- Left connection drawer, last-successful-profile startup option, and immersive full-screen toggle.
- Named Herdr sessions, client-local TTY context lookup, and explicit fallback pane selection.
- Bounded local Markdown/Mermaid/static HTML/draw.io previews with canonical SFTP resource isolation.
- Mouse drag selection, context Copy/Paste, Ctrl+Shift+C/V, and Ctrl+Shift rectangular selection.

## Verification

See [validation.md](validation.md) for the recorded suite results, reproducible commands, and
unverified physical-device boundaries. In particular:

- Final 0.1.11 selection suite: 5 passed, including rectangle clipboard/bitmap boundaries.
- Final 0.1.10 clipboard suite: 4 passed; software keyboard/Herdr regression: 3 passed.
- Fold/tablet immersive tests, background/service tests, and two-process recovery passed in
  their respective development runs.
- Latest application lint: zero errors, 28 warnings.

Raw local evidence and private delivery details are excluded from this repository. The
[changelog](../CHANGELOG.md) preserves feature history without account, device, or delivery IDs.

## Repository preparation

- Added a project-focused README, English user guide, contributing/security guidance, changelog,
  and third-party notices.
- Preserved third-party licenses, approved assets, tests, and source history descriptions.
- Excluded SDK paths, caches, build products, credentials, signing material, private keys,
  screenshots/logs, and private delivery metadata.
- Generalized build/ADB configuration for other development environments.

## Remaining validation

Physical Samsung input/desktop modes, long-duration background behavior, external-network
reachability, older Android versions, production signing, and store release preparation remain
separate work. See [design.md](design.md) for architectural boundaries.

## Herdr pane selection and Shift copy (0.1.12)

The original tracked-drag regression failed because the native View intercepted the drag as
local cross-pane selection. Tracked mouse gestures now bypass the local gesture recognizer
and reach the multiplexer exactly once per input event. Herdr owns pane bounds and scrollback;
Ctrl+Shift box selection and explicit local Select mode remain available.

Shift during a remote drag authorizes one bounded OSC52 clipboard response from the current
TerminalSession. The grant expires after five seconds and is cleared on cancellation,
disconnect, or View retirement. Ordinary remote OSC52 is still ignored. Herdr's default
copy_on_select is required; a child application that owns mouse input owns its own selection.

Validation: the initial deterministic mouse-routing test failed before the fix. The final
application build, JVM suite (6 tests), and lint completed (0 errors, 28 warnings). Emulator
coverage includes native remote-drag routing, one-shot clipboard authorization, existing box
selection and context-menu/keyboard shortcuts, plus real SSH/Herdr rendering, Shift-drag OSC52
copy, network recovery and Activity recreation. Physical Samsung input remains unverified.
A first SSH regression run lacked the loopback fixture; it was rerun with the fixture running.
A test assertion was adjusted to account for the new session-attachment grant reset; production
behavior did not require an additional change.

## Password authentication (0.1.13)

Approved scope: a connection-editor password field, encrypted password retention using the
Android Keystore-backed vault, and stored-password initial connection/reconnect. Existing
key profiles keep their default authentication and separate memory-only key-passphrase prompt.
Password ciphertext is bound to the profile ID and credential type with AES-GCM AAD. Passwords
are not serialized in profile JSON, prefilled into the editor, logged, or saved in UI state.

Implementation adds an explicit authentication selector, separate vault record, SSHJ password
branch, and credential cleanup on mode change/profile deletion. Verification uses a new
loopback-only Paramiko fixture with per-run random credentials; system accounts and sshd
configuration remain untouched.

Verification: password authentication/editor/storage/migration/reconnect tests passed (5/5).
Existing terminal, encrypted key authentication, host trust and real SSH/Herdr regressions
passed (8/8). JVM tests passed (6/6); Python context checks passed (2/2). Application and test
APKs built successfully; lint reports 0 errors and the existing 28 warnings. The editor field
was moved before optional startup settings after the first UI run exposed an off-screen input.
APK ZIP integrity, pinned asset hashes, signing-certificate continuity and test-credential
exclusion were checked. Evidence is from a generic Android emulator; physical Galaxy hardware
has not been exercised for this change.

## Tizen goal execution

The approved goal is active on `tizen`. Android remains unchanged. First gates are a native NUI
TPK, a reusable VT core with native rendering, actual emulator SSH and secure credential storage.
No Tizen application runtime claims are made until package installation and target tests pass.

### Tizen first runtime gate — 2026-10-20

- Actual target: emulator-26111, Tizen10.1/Unified 10.1.0, x86_64, 1920x1080 screenshot.
- Native C# NUI baseline is signed, installed and reported running by the SDK install runner.
- SecureRepository DataManager save/get and Manager.RemoveAlias round-trip succeeded in the
  target app (visible `Secure storage verified` status). Full profile persistence tests remain.
- Reused XtermSharp at 1bed529cd20748ef9da43753e8dd553f65fc5582 (MIT). Net8 Rune alias and
  native cell-width/combining/blank-cell fixes are documented in vendor/UPSTREAM.md. Ten portable
  terminal assertions pass. The initial screenshot exposed default cell sentinel characters and
  PointSize scaling; the corrected screenshot uses PixelSize and blank-cell mapping.
- SSH.NET 2026.0.0 transport compiled; no Tizen SSH connection is claimed yet. Version 2025.1.0
  was rejected during restore due to a reported high-severity advisory.
- SDK build runner: managed build succeeds, `tz pack` emits no package. The reference-compatible
  build-package script produces a signed ZIP TPK and checksum; install runner succeeds.
- Native screenshot: /tmp/hexgate-tizen-evidence/native-vt-0738.png. Baseline launcher-tag error
  dump returned zero lines; this narrow filter does not establish a full crash/error audit.
- Goal remains active. All remaining behavior and final acceptance criteria are unchanged.

### Tizen profile/input iteration — 2026-10-20

- Portable tests now cover 15 assertions, including profile metadata/secret separation,
  empty credential retention, replacement/revocation and deletion. A separate host-only
  fixture test also passed an actual SSH PTY input/output round trip; this is not emulator SSH proof.
- Native connection editor, separate key-passphrase prompt, host fingerprint confirmation,
  theme/font preferences and optional last-profile connection are implemented. Complete
  target acceptance for these features is still pending.
- Actual emulator text entry exposed root reconstruction on IME inset events. The handler
  now retains fields for unchanged window dimensions, guards retired field callbacks and
  restores focus after genuine window reconstruction. Host/port/user entry was observed
  persisting with the target virtual keyboard visible. Keyboard avoidance still needs work.
- Key import diagnostics distinguish missing paths, access denial and oversized files;
  relative filenames resolve inside this application's data directory. Automation originally
  omitted underscore characters; fixing injection did not yet establish successful import.
- Render requests are coalesced to a 33 ms interval; full-screen toggle text reflects the mode.
  The revised signed TPK was rebuilt and installed on emulator-26111. Performance and
  system-bar behavior still require runtime measurement.
- SSH, Herdr, preview, selection/clipboard and lifecycle acceptance gates remain open.

- Subsequent emulator evidence: relative `key` import succeeded from app-private data, profile
  Save succeeded and the SSH host-confirmation dialog displayed exactly the isolated fixture's
  SHA256 fingerprint (screenshot `/tmp/hexgate-tizen-evidence/connect-host-0846.png`).
- The first approval run exposed a real crash: `SshSession.Connected` queried SSH.NET after
  disposal. Full dlog identified ObjectDisposedException in Render after host approval; the
  previous launcher-tag filter missed it. State queries now fail safely for retired clients.
  Transport handshake timeout now allows the 90-second identity prompt rather than expiring
  after 15 seconds. The host fixture regression includes querying a previously connected client
  after disposal. Emulator retest of the correction is pending; no connected-terminal claim yet.

- Corrected package emulator retest reached `Development PC — connected` with a live native
  shell after explicit matching-fingerprint approval. Evidence:
  `/tmp/hexgate-tizen-evidence/ssh-retest-0852.png`. This establishes SSH.NET target compatibility
  and stored key retrieval; it does not establish the remaining Herdr/preview acceptance gates.
  The host regression passed 18 assertions including real PTY input/output and safe disposed state.
- Hardware-key injection into the actual native terminal ran `echo hexgateinputok`; its separate
  output line was visibly rendered (`/tmp/hexgate-tizen-evidence/ssh-input-0854.png`). Both SSH
  network transport and terminal input/output are now proven on the selected emulator.

### Android visual parity correction — 2026-10-20

- TextField and shared label content now use explicit vertical center alignment. Installed-app
  screenshot confirms editor values sit in the middle of their 44-pixel fields rather than at top.
- Reused Android's exact portal PNG and refresh/fullscreen vector paths. Header controls are
  tinted icon buttons, with the same enter/exit artwork rather than Unicode or text substitutes.
- Hexgate Dark, Light, Amber, Dracula, Gruvbox Dark and Solarized Dark use Android's exact
  background/foreground/accent values. Public themes also retain all 16 ANSI colors. Default
  buttons no longer use the platform orange; surfaces match Android's color blending. Existing
  `dark` preferences resolve to Hexgate Dark for compatibility. Bundled Gogh notices were copied.
- Source parity check passed for all six palettes and byte-identical brand PNG; portable tests
  pass (16 assertions). Signed TPK rebuilt and installed. Actual editor and Amber settings
  screenshots: /tmp/hexgate-tizen-evidence/android-style-editor-0905.png and
  /tmp/hexgate-tizen-evidence/android-amber-settings-0910.png. Complete cross-platform pixel
  identity and the remaining functional goal gates are not claimed.

### Remote-preview implementation and feasibility — 2026-10-20

- Added an independently owned SFTP client pinned to the already approved SSH fingerprint.
  Session teardown cancels/disposes it; serialized reads are separate from the terminal PTY.
- Remote listing stops at 500 entries. Document reads cap at 2 MiB, resources at 1 MiB,
  and a per-document budget caps at 64 requests / 16 MiB. Canonical paths from SSH.NET's
  SFTP realpath resolution enforce the resource root; post-read checks reject changed paths.
- Host tests passed 34 assertions, including actual SFTP document/resource/list operations,
  traversal and symlink escape rejection, oversize rejection and authenticated preview HTTP
  routes. Untrusted HTML rejects renderer script requests and carries script-src 'none'.
  These are host transport/policy results, not browser execution-isolation proof.
- Added native remote-path selection and paginated file browsing, with a split NUI terminal /
  document WebView. Renderer assets and their notices are copied unchanged from Android.
- First target preview reached Chromium construction but the engine aborted when changing
  PrivateBrowsingEnabled after creation (FATAL storage_partition_config.in_memory()). Removed
  that transition; HTTP no-store plus WebContext cache disablement preserve non-caching.
- Second target run remained alive but returned HTTP 403: this WebView did not send the custom
  authorization header. The revised native request interceptor keeps the token outside URLs
  and WebView content, forwards only the synthetic preview origin to the authenticated loopback
  server, bounds outstanding requests and rejects foreign origins. Runtime retest is pending.
- Retired SSH output is now bound to its own terminal model, and terminal-response callbacks
  check their owning session generation before sending. Remaining full-goal gates are unchanged.

### Remote-preview target gate — 2026-10-20

- Native request interception works on emulator-26111. The WebView uses a synthetic HTTPS
  origin; a bounded native adapter forwards only that origin to the authenticated loopback
  server. Tokens stay in native request headers, outside document content and engine URLs.
- Installed-app screenshots prove Markdown with embedded Mermaid/draw.io and CJK, standalone
  Mermaid, standalone draw.io, and static HTML. Sources are isolated temporary fixture files.
  Evidence: preview-intercept-0956.png, preview-mermaid-1010.png,
  preview-drawio-ready-1020.png, preview-html-security-1007.png under
  /tmp/hexgate-tizen-evidence/. The terminal remained connected across document changes.
- Hostile HTML contained a script that would replace the visible heading, plus external
  image/CSS/frame requests and a file URL. The heading remained unchanged and the external
  probe received zero requests. A subsequent positive-control GET from the same emulator
  reached the probe (one request), confirming reachability. The own probe listener was stopped.
- Host checks now pass 35 assertions, including preview CSS/renderer theme changes. Native
  theme updates and resize event ordering were corrected afterward and need final regression.
- File browsing now waits for its listing before presenting editable input, preventing an
  asynchronous list redraw from disrupting typed paths. Late results cannot replace another
  dialog or revive canceled preview requests. Latest source is being repackaged/retested.
- This completes the initial four-format renderer feasibility gate, not the full Tizen goal.
  Herdr context/drag/clipboard, full input/lifecycle/security regressions and final acceptance
  remain required. No commits, pushes or publication occurred.

### Preview lifetime regression — 2026-10-20

- An additional target regression exposed native SIGSEGV while opening settings/changing a
  theme with a document loaded. This invalidates any broad stability claim based only on
  successful renderer screenshots.
- WebView and its WebContext now survive NUI chrome reconstruction. The context/callback
  remains strongly referenced; retired views invalidate asynchronous callbacks before
  deferred disposal. The native request cap applies across view generations.
- The exact failing UI sequence was rerun on a rebuilt/reinstalled TPK, followed by four
  theme transitions (Light, Dark, Amber, Dark). The process remains running (PID 937109)
  and Markdown with both diagram types remains displayed with the SSH terminal alive.
  Evidence: /tmp/hexgate-tizen-evidence/preview-theme-retained-1042.png.
- Further full-goal lifecycle/resize and hostile-resource regressions remain pending. The
  goal is active; initial preview feasibility does not replace those acceptance gates.
- A further Light transition on the same retained engine passed: native chrome, terminal,
  Markdown CSS and Mermaid colors all changed together while the session stayed alive.
  Evidence: /tmp/hexgate-tizen-evidence/preview-light-retained-1045.png. The temporary target
  key-import source was truncated after use; authentication uses the secure repository.
- Bounded error-log review includes 230 own-process error-priority lines (mostly platform
  input/engine diagnostics), with no unhandled/fatal markers in that dump. This is not a
  zero-error claim or a substitute for the final crash/lifecycle stress matrix.
- Current signed emulator TPK SHA256:
  736fbb3fb9aa9e9168eda8e32826d17c9589cab58961e3fbe027420ec6feba54.

### Native terminal input/clipboard increment — 2026-10-20

- Added a portable TerminalInteraction controller: tracked drags use the mature engine's mouse
  encoder; Ctrl+Shift forces rectangular local selection; ordinary local selection and wheel
  scrollback are available. Shift during a tracked drag grants one bounded OSC52 write for five
  seconds. Cancellation, replacement and disposal revoke it; malformed replies consume the grant.
- Added native highlighted selection, system clipboard shortcuts, copy/paste/select accessories,
  Ctrl/Alt latches, context-menu routing, application-cursor/function-key/modifier encoding, and
  accessory wrapping. Enter remains immediately after Tab. Plain Ctrl+C/V retain PTY semantics.
- Fixed upstream OSC packet-boundary/first-byte and UTF8 defects while adding a 1,400,000-byte
  bound; oversized commands are discarded through their terminator. Registered only the OSC52
  handler needed by the controller. Engine diagnostics no longer log remote terminal payloads.
  See vendor/XtermSharp/UPSTREAM.md for patch details.
- Host tests pass 55 assertions with actual SSH/SFTP fixtures. Interaction checks include SGR
  event counts, rectangle bounds, one-shot/expired/canceled/retired copy grants, parser recovery,
  Unicode titles, negotiated paste, key encodings and engine scrollback.
- Actual target: a Ctrl+Shift drag selected only ALPHA and DELTA (five columns on two rows),
  Ctrl+Shift+C set the system clipboard, and Ctrl+Shift+V pasted exactly those rows into the
  SSH shell. Plain Ctrl+C canceled the remote input instead of copying. Evidence:
  /tmp/hexgate-tizen-evidence/input-box-copy-corrected-1108.png and
  /tmp/hexgate-tizen-evidence/input-clipboard-paste-1112.png.
- Test-driver correction: move the pointer before mouse-down; do not omit coordinates in
  mouse-up. Raw mouse button injection needs target-specific mapping validation. Context-menu,
  actual Herdr pane/OSC52 and target IME regressions remain pending; no broad input-complete
  claim is made. The newest package also explicitly retires native terminal views/controllers
  rather than depending on parent disposal, and retains IME draft text across reconstruction.

### Native IME and Herdr RGB failure investigation — 2026-10-20

- On the installed target, opened Terminal input and tapped four keys on the actual target
  virtual keyboard. ECHO appeared in the native field and Send text delivered exactly ECHO
  to the SSH prompt without Enter. Plain Ctrl+C canceled that pending input. Evidence:
  /tmp/hexgate-tizen-evidence/input-ime-keyboard-1203.png and input-ime-sent-1205.png.
  This verifies English target IME, not Korean composition or physical-device IME behavior.
- Menu key opens the native Copy selection/Paste/Close popup without reconstructing the
  terminal. Evidence: /tmp/hexgate-tizen-evidence/input-menu-visible-1206.png. Aurum F10 and
  secondary-pointer injection are not yet proven to reach the application; these paths remain
  unverified, rather than being reported as passed.
- Launched only a uniquely isolated fixture with its own XDG config directory and named
  hexgate-nui-test session. Developer Herdr sessions/configuration were not inspected or changed.
- First real Herdr startup exposed an upstream NotImplementedException in MatchColor on RGB
  SGR, causing the reader to close. Exact captured fixture-byte replay reproduced the exception.
  Implemented bounded fixed-palette matching and guarded incomplete RGB/indexed arguments;
  the same replay now passes. RGB remains quantized to the engine's 256-color representation.
  Rebuild and actual target Herdr recheck are in progress; full Herdr acceptance remains open.

- RGB fix installed and rerun on emulator-26111. Herdr starts and remains connected, with its
  welcome/UI colors and a live two-pane layout. Setup ran inside the fixture pane after checking
  HERDR_ENV=1 and its exact private socket path; returned IDs were w1:p1 and w1:p2.
- A tracked ordinary drag across the right-pane boundary reached Herdr (its copy toast appeared)
  without a local clipboard status change. Repeating with Shift enabled the one-shot OSC52
  grant and changed the native status to Selection copied. Ctrl+Shift+V pasted exactly
  LEFT PANE ALPHA and LEFT PANE DELTA into the left shell: no right-pane text or borders.
  Evidence: herdr-rgb-fixed-1218.png, herdr-pane-setup-1222.png, herdr-shift-copy-1226.png and
  herdr-shift-paste-1227.png under /tmp/hexgate-tizen-evidence/.
- Rebuilt signed TPK SHA256: 9c2e822878f35d3aead4b402844f84a230891876ace67333ae2b2c8774943ff7.
  Actual SSH/SFTP integration harness now passes 57 assertions. A first concurrent build/test
  attempt collided on shared .NET output; rerunning the test after packaging completed passed.
  Use sequential builds for projects sharing these output directories.

- Additional native input stress is not clean: Aurum-injected command typing sometimes
  duplicates/drops letters after the live Herdr/selection sequence, including after explicit
  press/release injection. Do not infer full hardware-input correctness from the earlier simple
  command/clipboard pass. Root cause (injection timing vs native focus/input delivery) is still
  under investigation. The English on-screen touch-keyboard ECHO result is a separate proven path.
- Current own process PID 952922 remains running. Bounded target error dump contains 33
  own-process error-priority lines but no Unhandled/Fatal/SIGSEGV/NotImplementedException markers.
  This is a bounded observation, not a zero-error or full-lifecycle stability claim.

- Compared the same injected command in the terminal IME TextField: the plain native field also
  shows duplicated/dropped letters. This narrows the issue beyond PTY encoding; Aurum/system
  key delivery remains suspect, but the exact root cause is not yet proven.
- Actual touch-keyboard input exposed a separate reproducible defect: sentence capitalization
  changed sh to Sh. All application input fields now explicitly disable automatic capitalization
  and prediction; hidden fields also request the system Password panel layout. Signed package
  rebuilt and installed; lowercase touch-input target recheck is in progress.
- Current package SHA256: 9c36198c8bb6638abe2615e16112abb6ba0ed68a9e10337104d6774bc0f6751d.

- Latest signed TPK target recheck passes lowercase native touch input: pwd remains lowercase
  in the TextField and Send + Enter executes pwd, producing /home/hjhun. One direct injected
  echo abc command also matches exactly after prediction was disabled. This is encouraging
  evidence, not yet proof that the prior intermittent duplication has been eliminated.
  Evidence: ime-lowercase-draft-1248.png, ime-lowercase-send-1249.png and
  input-after-prediction-off-1251.png under /tmp/hexgate-tizen-evidence/.
- Temporary target import source contains only this test key and was truncated after secure
  import. Profile credentials remain in the target secure repository.

- Reattached the same isolated Herdr server using the latest IME-policy package. A subsequent
  short injected sh g became ssh g, so the intermittent key duplication is still reproducible;
  prediction disablement fixes capitalization but does not close that separate investigation.
  Do not use unreliable injected command typing for further fixture control until diagnosed.

- Latest-package touch-keyboard path typed lowercase sh g and Send + Enter executed the own
  fixture exactly. It clears only the fixture's pane and prints actual UTF8 CJK, combining,
  box-drawing and Nerd Font powerline/terminal glyphs. Native glyphs are visible in
  /tmp/hexgate-tizen-evidence/herdr-touch-glyphs-1256.png. The two-pane Herdr session remains alive.
- Follow-up lifetime audit clue: local TizenFX ViewInternal.Dispose clears child parent links
  but does not explicitly dispose child controls; native input fields are currently cleared
  from inputFields before root disposal. Investigate explicit owned-field/context retirement
  with a reproducer before attributing the intermittent input issue to that behavior.

### Input-boundary diagnosis and retained terminal actors — 2026-10-20

- Previous goal turn made implementation/target-evidence progress; full acceptance remains open.
- Added temporary DEBUG counters that record aggregate event counts, held/repeated downs,
  same-time duplicate events and text lengths only; no key names, scan codes or text are logged.
  First plain native TextField and ten reconstruction cycles both input abcdefgh exactly.
- Added an isolated Herdr raw-input recorder under the existing private fixture directory.
  It bounds reads to 1024 bytes/12 seconds, restores terminal settings and records only the
  explicitly injected fixture alphabet. Sending eight characters reproducibly received nine
  or ten. A detailed trace captured actual repeated Down events after ~400 ms holds, with
  distinct timestamps, zero duplicate-event count and zero multi-character key strings.
- An eight-character baseline run had request latencies 184.72-1099.97 ms and received nine
  bytes. Native C# Paint samples: 29, median 5.04 ms/max 26.59 ms, including startup and typing.
  This measures managed scene updates, not complete presentation/compositor latency. Input
  injection/platform scheduling is suspect; do not disable legitimate keyboard auto-repeat or
  claim the exact underlying platform cause is proven.
- Implemented retained native row-run TextLabels: changed text/style/geometry updates an existing
  actor; only added runs allocate and removed tails dispose. Theme/viewport retirement still
  clears the cache. Portable SSH/SFTP checks remain 57/57. Target A/B comparison is in progress.
- The SDK full redeploy removes application metadata and secure aliases on this emulator.
  Confirmed missing profiles.json before launch and failed secure retrieval after restoring only
  test metadata. This is not a persistence-across-normal-launch test; do not call forced full
  reinstall an application update or claim its aliases survive. Reimported only the own fixture
  key and restored only own fixture metadata for the next benchmark.

- Retained-actor target recheck passed CJK rectangular copy/paste: selecting the two wide glyphs
  copied/pasted exactly 한글 in the live Herdr shell. Evidence: retained-cjk-clipboard-1332.png.
  In a valid connected typing run, ordinary one-row updates created zero actors (previously9),
  usually taking1.30-1.67 ms. First added run created1 and final multi-row change created2.
  Startup remains about26.61 ms. These are incremental managed-update measurements, not FPS.
- The connected raw-input test still failed (12 received bytes for8; request latency152.83-
  805.02 ms). Retaining actors improves scene churn but does not resolve injected key repeats.
  A disconnected attempt with no recorder output was explicitly rejected as benchmark evidence.
- Background investigation exposed overbroad interception: unhandled XF86 home/audio/power keys
  were consumed while the terminal had focus. Forward those platform keys, retaining app-owned
  XF86Back and XF86Menu. The latest package is being checked for actual home/background behavior.

- Follow-up target testing confirmed a genuine missing-alias replacement defect: after a
  key-manager reset, Save committed the new alias then the managed RemoveAlias wrapper threw
  while deleting an already missing old alias. The editor misleadingly showed failure.
  SecureCredentialStore now uses the same native ckmc_remove_alias entry point to distinguish
  CKMC_ERROR_DB_ALIAS_UNKNOWN (already revoked) from every access/locking/storage error.
  Put/Get remain managed SecureRepository calls; other revocation errors still fail closed.
- The startup secure-store probe now proves Put/Get, existing-alias removal and a second removal
  of that same own temporary alias on target. The rebuilt app launches, a single Save closes the
  recovered profile editor, new alias metadata persists and the saved key opens real SSH/Herdr.
  Evidence: vault-recovery-import-ready-1348.png, vault-recovery-saved-1349.png and
  vault-recovery-connected-1350.png. Permission/locking fault injection is still pending.
- Some fixture automation ran before the newly launched UI was ready; rejected those attempts
  and verified the visible editor/import/Save stages. Do not equate process launch with UI readiness.
- Correction to earlier import-source notes: this target has no truncate command. The earlier
  truncation receipts were failures, so those historical removal claims are retracted. The exact
  own fixture source /opt/usr/home/owner/apps_rw/org.tizen.hexgate/data/key was now removed with
  a successful SDK-helper rm receipt (.tools/tizen-vault-source-removed.json). Host test keys stay
  in ignored .tools; no user credential file was touched.
- Actual background/return passed for about94 seconds: launched the installed platform keyboard
  settings without changing any setting. Window-manager inspection showed Hexgate iconified/
  obscured while Settings was visible, then visible again after launch/raise. App PID965234
  and the same established loopback fixture SSH tuple (including server child PIDs) survived
  unchanged. A post-return touch-keyboard command executed in the live Herdr pane.
  Evidence: .tools/tizen-background-windows.json, tizen-background-resumed-windows.json,
  tizen-background-live-*-socket.txt and /tmp/hexgate-tizen-evidence/resume-live-terminal-1401.png.
- The screenshot runner kept returning the Hexgate client buffer during background, so screenshots
  alone do not prove foreground state. Also discarded old server.log counters: they were not
  the active daemon's output. Used current ss socket state and window/process data instead.
- System Home injection did not yield a verifiable home-screen transition on this image even
  after forwarding XF86 keys. Do not mark Home or system-bar/fullscreen acceptance passed.
- Latest signed emulator TPK SHA256:
  cf46f57435d50ec3ba9c84159aa07ef0e43c65920e8fa2c3cbf4839e7b764023.
  ZIP/manifest/signature-entry/notice checks passed; neither own fixture private keys nor the
  encrypted test key are packaged. Android remains unchanged. Full goal remains active.

- After the host tests and graph-index update had finished (no graphify process), one further
  controlled raw-input round passed exactly8/8 bytes. Injection request times were113.36-
  410.21 ms. A single passing trial does not resolve the earlier repeat/jitter condition;
  future comparisons must record host workload and exclude concurrent index/build activity.
- Graph update completed (13416 nodes/49821 edges). It reports partial extraction for two
  untouched Android Kotlin files and zero nodes for public-palettes.json/vendor-checksums.json.
  This is an analysis-index limitation, not Tizen compiler validation; Android compiler checks
  were not run for this increment.
- Explicit child/IME ownership disposal, normal-launch credential retention and permission-fault
  cleanup, remaining key/password/host-change matrix, small-window/inset/fullscreen/D-pad tests,
  parser CSI/DCS/title bounds, and full preview/lifecycle stress are still acceptance work.

### Bounded VT parser and line-operation correction — 2026-10-20

- Reproduced oversized CSI parameter/intermediate dispatch, streamed DCS growth, excessive
  line-insertion allocation and independent title-stack restoration failures before fixing them.
  The mature XtermSharp parser now caps headers at 256 parameters/16 intermediates, saturates
  numeric arguments at 65535 and discards overflowed sequences without executing their prefixes.
  Streaming DCS delivery is capped at 4096 bytes; canceled/overflowed requests cannot reply.
  CAN/SUB cancellation now takes priority in every parser state. Parser disposal aborts an
  active handler. Title payloads are capped at 4096 characters and history at32 entries each.
- IL/DL/SU/SD counts are clamped to the affected scroll region; inserts/deletes outside margins
  are ignored. Scroll-down now actually shifts retained rows and uses the current erase
  background. Forward-tab work is capped to the viewport. These are defensive engine patches,
  recorded with upstream provenance under tizen/vendor/XtermSharp/UPSTREAM.md.
- Host integration passed86 assertions, including the live isolated SSH/SFTP fixture. An actual
  installed emulator app consumed100000-semicolon CSI, oversized intermediates/DCS/numbers/title
  and canceled controls, then returned exactly one valid SGR status response (9 bytes), with no
  partial oversized-query response. Scroll-region movement/blue erase background and CJK/box/
  combining/font output were visible. Releasing the fixture restored the original normal buffer.
  Evidence: .tools/tizen-parser-bounded-integration.log, tizen-native-parser-result.json;
  /tmp/hexgate-tizen-evidence/parser-bounds-native-1438.png and parser-normal-restored-1439.png.
- The parser build was installed and tested before the next scene-lifetime increment. The prior
  cf46f574... package checksum above is historical; see the newest dist/SHA256SUMS for the
  current development package. Full VT conformance and remaining goal gates are not claimed.

### Scene ownership experiment — 2026-10-20 (regression under investigation)

- Added explicit ownership for app-created NUI views/labels/buttons/fields/icons, with deferred
  old-scene disposal after native callbacks. Button-internal children are left to Button's
  disposer; retained preview WebView/context and native terminal have independent lifetimes.
  Retiring the terminal now explicitly disposes its cursor. Pause resets modifier button colors.
- Built and installed the signed experimental TPK (SHA256
  cc605cd3198f1ec1fd406cd9bb253af34de4d24a59798aa0f8cc7db346bee713).
  Host SSH/SFTP integration passed86 assertions. Four editor cycles switched key/password mode
  while focusing the fields; nine theme changes without preview returned to stable owned-view
  counts (editor53/54, drawer30, settings46) with no retired scenes left queued. The actual
  app PID993631 and SSH tuple127.0.0.1:40726 remained unchanged during that phase.
- Markdown+embedded diagrams loaded successfully afterward. However, switching through more
  themes WITH that preview triggered a native SIGSEGV at19:53:06 (PID993631, TID995398).
  This invalidates stability of this experimental package; do not present it as a verified
  release. Relevant evidence: .tools/tizen-scene-bounded-errors.json, tizen-scene-crash-manager.json,
  tizen-scene-crash-controls.json; /tmp/hexgate-tizen-evidence/scene-preview-loaded-1521.png.
- Reproducing under a new process with a live log monitor. Following ordinary app restart (no
  redeploy/reimport), the saved secure key authenticated and resumed the own Herdr fixture.
  The normal-launch persistence path therefore has positive target evidence, independent of
  earlier full-deploy data resets. Additional replacement/deletion/fault cases remain pending.
- The crash manager named a dump ZIP but the SDK transfer reported that exact device path
  unavailable. The crash-artifact probe also returned unavailable; no root/permission change
  was made. The new shell reports owner and window-manager diagnostics now return permission
  denied, so current process checks use app_launcher's supported running-app list. No unrelated
  app/session was terminated. The first broad investigator included an unrelated Action-library
  crash; that finding is not evidence of Hexgate's cause.
- A second native crash reproduced after18 successful preview-theme transitions in the fresh
  process, then switching back to Light (PID993762, TID997234, SIGSEGV at19:57:29). The automated
  process-survival assertion failed as intended: .tools/tizen-aurum/scene-theme-repro.py and
  tizen-scene-theme-repro-stress.log. The live log monitor independently detected that app's crash.
  This is a reproducible regression signal; its root cause is still under investigation.
- Removing only StopLoading did not fix the defect: the comparison build (8fd2f7f8...)
  crashed again after10 completed theme switches. Collected Chromium logs identify the crashing
  thread as the URL-loader IO thread: cancellation of architecture.drawio had an invalid producer
  handle immediately before SIGSEGV inside libchromium-impl.so. Native Dali also reports recursive
  Emit warnings during chrome reconstruction; those are not yet independently resolved.
- Reworked theme application to update only app-owned CSS through native evaluation in the
  retained page, with a diagram rerender only when light/dark changes. No page navigation is
  issued on a theme change. Static HTML keeps JavaScript disabled and receives only generated
  CSS; target capability/security verification is still in progress for this new path.
- Added a bounded per-document SFTP resource snapshot:64 attempted reads/1MiB per resource/
  16MiB total. Each locally requested light/dark rerender has a separate64-response/16MiB
  delivery budget, including cached responses. Changing appearance no longer rereads remote
  files or drains all future renders' allowance. No document can reset these limits itself.
  Host regression failed before the change and now passes90 assertions, including80 alternating
  theme/resource reads, snapshot reuse after deleting an own fixture source, cached delivery
  limits, and static-HTML theme script isolation. New TPK built/installed; target testing pending.
- After secure import and successful SSH, the SDK accepted a zero-byte overwrite of the exact
  own test-key source data/key. Its subsequent pull precheck reports that path unavailable
  under the current owner shell; do not claim independent readback verification. No root change
  or unrelated user key-file access was attempted. Receipts: tizen-preview-inplace-key-cleared.json
  and tizen-preview-inplace-key-clear-check.json in ignored .tools.
- New installed package f78c06045e3786b7031910fe331f80eedeb1d60a76482336dfbc807493a290dd
  completed60 preview-theme transitions with actual Markdown/Mermaid/embedded draw.io loaded.
  PID1000422 and the established SSH tuple127.0.0.1:38322 (server child PIDs2687366/2687364)
  survived unchanged. Owned settings controls stayed48, retired0; the current terminal/document
  remained visible afterward. Evidence: tizen-preview-inplace-stress.log, scenes.json,
  sockets-before/after.txt, /tmp/hexgate-tizen-evidence/preview-inplace-sixty-themes-1632.png.
- The native input injector still repeats some characters during log collection; screenshots
  caught malformed fixture paths before treating file-open failures as preview defects. Stopped
  the own log collector after the successful theme stress and tightened injected stroke timing
  before continuing static-HTML/IME checks. This does not establish physical keyboard fidelity.
- Static HTML target check passed: Light then Amber updated the actual document background
  without enabling document scripts; SCRIPT BLOCKED remained visible. External probe count0;
  host and emulator curl positive controls each reached the own probe. The owned probe was
  stopped afterward. Native virtual-keyboard touches entered exactly pwd after the scene stress,
  Send+Enter executed it in the own Herdr pane, and three Menu/Close popup cycles retired all
  popup controls (owned24, retired0). Screenshots: preview-inplace-html-light-1648.png,
  preview-inplace-ime-pwd-1654.png, preview-inplace-amber-ime-result-1657.png in the evidence dir.
- Direct draw.io and Mermaid also loaded in the exact f78c0604... package; verified screenshots
  preview-inplace-drawio-1713.png and preview-inplace-mermaid-1716.png. Earlier screenshot names
  drawio-1703/mermaid-1706 did NOT show those formats: the temporary automation helper failed
  before file selection. Those are discarded as format evidence. The corrected helper uses
  explicit drawer state and checked native accessibility field set/get for own fixture paths.
- Repeated narrow terminal transitions exposed residual wrapped shell-prompt fragments in the
  Herdr panes. The ownership/preview crash fix does not establish viewport redraw correctness.
  Captured before/after a real Refresh request (preview-inplace-mermaid-1716.png and
  preview-inplace-refresh-1719.png); investigate remote Herdr pane state versus native VT
  reflow rather than masking the discrepancy. Small-window/inset/fullscreen and repeat-resize
  gates remain open, along with credential failure cleanup/authentication matrix and final
  engine/transport lifecycle/bounds checks. The full goal remains active.
- Refresh did not visibly remove the wrapped prompt fragments in this comparison. Their source
  (remote Herdr pane screen versus local reflow/redraw state) is not yet established. Do not
  mark refresh/resize correctness complete based only on the button request or clean compilation.
- Final package structure/IDs/signature entries/fixture-exclusion check passed for f78c0604...
  (13913016 bytes). Portable+live fixture checks90; app process1000422 still running at last
  app_launcher query. Android and renderer source reference directories have no Git diff.
  No commits, pushes, publication, mail or Drive upload occurred.
- Graph refresh initially requested a semantic-extraction API key; used the local code-only
  path instead. Completed13435 nodes/49882 edges. Existing partial extraction warnings for two
  untouched Kotlin files and empty palette/checksum JSON nodes remain index limitations; they
  are not compiler errors or proof of Android/Tizen acceptance. No API credential was requested.

### Viewport source comparison and clipping correction — 2026-10-20

- The current shell is outside Herdr. No focused developer-session command was issued there.
  Executed a guarded read-only helper through the actual own Hexgate/Herdr pane instead; it
  verified HERDR_ENV=1 and the exact private fixture socket before using --current. Both mangled
  prompt markers appeared in Herdr's visible pane read AND corresponding native label rows.
  Evidence: .tools/tizen-herdr-native-source-comparison.json and own fixture pane reads. These
  source fragments are not caused by the local label renderer; do not sanitize/mask remote text.
- app_launcher -S and Aurum getAppInfo under the current owner context omitted the app even
  while its actual UI command executed, produced new own fixture files and retained the same
  SSH socket. Treat those lists as incomplete observations here. No restart was performed just
  because that observation was missing. The subsequent install below was for changed code.
- Portable regressions separately reproduced stale alternate-screen cells returning after
  shrink/grow, then a clipped two-column glyph left at the right edge. Fixed alternate-row
  truncation independently of reflow, cleared the clipped primary cell, and grew an originally
  full-width horizontal margin with the viewport while retaining explicit restricted margins.
  Mature normal-buffer reflow is preserved. Its intentional active-cursor-line exclusion means
  the scrollback regression uses completed output (including CJK), not an unfinished cursor line.
- SSH/SFTP integration passed97 assertions. Built and installed signed TPK37e52ce0... and launched
  a dedicated raw-PTY grid fixture. Initial actual native157x30 cells match every expected row;
  stdin byte count0. The fixture records its own process/SSH peer port and regenerates on actual
  SIGWINCH. Ongoing drawer/font/preview/refresh checks compare complete actor rows, not merely
  screenshots or resize requests. Android and reference sources remain untouched.

### Visual parity recheck — 2026-10-20

- Rechecked the installed connection editor: both values and placeholders are vertically
  centered in their input fields. Screenshot: /tmp/hexgate-tizen-evidence/input-alignment-before.png
  (the filename denotes the inspection before any additional changes; no new change was needed).
- Source comparison confirms all six complete palettes, the byte-identical portal PNG and
  the refresh/fullscreen/exit vector paths match Android. The menu glyph also matches Android.
- Portable checks passed77 assertions; current signed TPK SHA256 is
  37e52ce0afc43df0f780f7de13a5cb230970142e9c5c188e67ed5973caaf752d.
  This recheck did not rebuild/reinstall or modify Android. Full pixel identity is not claimed.


### Credential recovery and native authentication — 2026-10-20

- The previous goal turn added current visual-parity evidence. This turn reproduced a separate
  credential bug: failed retired-alias removal was forgotten after a committed replacement, so
  restarting left the old secret orphaned. Red log: tizen-credential-journal-red.log.
- Added an alias-only bounded write-ahead journal before secure save. A single metadata commit
  activates the replacement and journals retirement. Deletion commits its revocation intent and
  clears last-profile selection. Startup/manual retries preserve failed operations; active-alias
  overlap fails closed. Empty edits retain the secret. Committed operations report pending cleanup
  separately from save failure. Startup initialization failure now keeps a native explanation
  visible instead of crashing before render. No plaintext credential fallback was introduced.
- Host faults cover metadata prepare/commit failure, uncertain secure-save result, removal failure,
  interrupted save replay, deletion/restart and method change. Default92 checks; real SSH/SFTP112;
  independent real SSH password/key/encrypted-key/changed-host/cancellation matrix102 (shared92
  counted in both modes, not additive totals). New tests do not change production sshd/passwords.
- Built signed TPK7ea92b2a043c5caf4ef2d6c410f570e448fb06f04a51ba908622a4dc72fb7745, installed through
  SDK job20261020T121050-56103e, launched PID1038856. Native password blank edits, replacement,
  wrong-password failure/recovery and deletion passed; no pending native revocation remained.
  Native changed-host rejection yielded0 password authentications/0 shells, then explicit approval
  connected. Evidence: tizen-password-retention-native.json, tizen-native-host-reject.json,
  tizen-native-credential-delete.json. Actual password field was not read back or logged.
- The first custom-auth fixture exited on a resize notification after authenticating. Its early
  screenshot is not connection-survival evidence. Corrected the fixture to handle resize signals;
  subsequent saved-password reconnects stayed connected. This fixture executes no host commands;
  actual shell/SFTP tests still use the separate own OpenSSH server.
- Independent signature verification checked both RSA/SHA512 signatures and65/66 reference digests.
  The helper initially used an lxml subtree canonicalization that changed namespace context; a
  detached equivalent subtree produced the expected properties digest. Exact signature/reference
  checks now pass; no package payload was changed to accommodate the helper. Test certificates
  are emulator-only. Archive scan excluded own exact private-key/password/passphrase bytes.
- Bounded100-entry error excerpt:30 own-PID entries,14 recursive Dali Emit warnings,0 own crash
  markers in that excerpt. This does not establish an entirely crash-free run. Seven existing
  vendored VT compiler warnings remain; build has0 errors. Full goal remains active.
- Reconciled earlier viewport evidence:18 whole-grid comparisons and3 no-repaint wide-clipping
  comparisons passed with stdin0 on37e52ce0.... Positive Settings focus, same app/SSH/grid process
  and exact rows survived32.25 seconds of background. Fullscreen still retains OS back/home UI.
  Details and source-fragment distinction are now in tizen/docs/validation.md.
- Next required work includes normal-process-restart credentials, remaining native auth/fault UI,
  engine/transport disposal and queue bounds, small-window/inset/D-pad behavior, recursive signals,
  wider preview boundaries, diagnostic cleanup and final exact-package regression. Android and
  reference repositories remain unchanged; no commit, push, publishing, email or Drive action.

- Added a reproducible host-only auth fixture under tizen/tests/fixtures with pinned dev-only
  dependencies, loopback binding, fresh private output-directory enforcement, no host command
  execution and a30-minute lifetime. The checked-in fixture independently passed102 assertions;
  its generated keys/passwords remain ignored outside packaged assets. Both own auth servers
  were stopped only after their live statistics showed0 active connections. Original OpenSSH,
  Herdr/developer processes were left alone. Current TPK checksum verifies from tizen/dist.


### Transport and VT retirement — 2026-10-20

- Previous goal turn was progress: durable credential recovery and native authentication checks.
  This increment reproduced an independent input bound bug:512 one-byte commands bypassed the
  byte-only quota. Added a256-command/2MiB limit,1MiB per command, empty-input no-op and bounded
  channel with one reserved/coalesced geometry slot. Pending writes stop when retired.
- Reworked SSH ownership so concurrent DisposeAsync callers await the same cleanup, including
  in-flight connect and output callbacks. Late-created shells cannot be adopted. Reader/writer
  close notification is one-shot; keys/authentication/SFTP/events/queued input are released.
  Shutdown runs off NUI; normal app exit joins it after the UI loop returns. Added Close Hexgate
  to Settings, enabling normal exit/relaunch tests instead of destructive SDK kill/reinstall.
- The reused VT core now explicitly releases parser/OSC handlers, both row stores and saved
  titles/events. Retired models/views/interactions ignore late input and cannot paint frames.
  An overbroad intermediate edit accidentally cleared the normal parser Reset callback; the
  existing DCS-reset regression failed and the correction preserves Reset behavior. Final checks
  are green; intermediate logs are not acceptance evidence.
- Added one-pending-message status delivery scoped to connection generation, following Calendar's
  owning-context dispatcher pattern. Async failures recheck ownership after awaited cleanup so
  they cannot clear a newer connection. Retired producers cannot replace current pending text.
- Live OpenSSH test separately reproduced lost geometry while host verification was waiting.
  Preserved latest desired size through shell adoption; actual stty now returns29x113. Output-
  blocked disposal, host-verification retirement and SFTP-open/dispose races also pass. Default108,
  SSH/SFTP/lifetime134, authentication118 assertions (shared108 not summed repeatedly).
- Signed/installed f6148416a4abe0814b7520f83d235a570db43731910a7c3ad67f855651ee23af through SDK
  job20261020T123419-d9eee3.0 build errors/7 existing VT warnings. Native normal close removed PID
  1050307 and its SSH connection; normal launch PID1050546 retained saved password. A second
  close/launch removed old process and created PID1051501; no credential entry or host-trust
  prompt was permitted by the verification script. No reinstall or data restoration was involved.
- Deferred button actions until their native signal returned.20 native theme/chrome changes kept
  the same SSH connection/shell counts; recursive Emit warnings0 for that PID in this interval.
  Other callback paths remain to verify. Final saved ring2107 entries contained43 for PID1051501,
  with0 own recursive Emit/crash markers; framework VCONF/RPC/EGL warnings remain. The trailing100
  lines contained0 own entries, so only the complete bounded saved-ring review supports this.
- Both package signatures and65/66 reference digests verified; exact own credential/key fixture
  bytes and test assemblies/metadata are excluded. Native screenshot: lifetime-retained-password-
  no-prompt.png in /tmp/hexgate-tizen-evidence. Deleted own test profile/credential, closed app
  normally and stopped only the own auth fixture at0 active connections. Developer sessions and
  the original isolated OpenSSH/Herdr fixtures were preserved. Android/reference source unchanged.
- Upstream source review found ShellStream's read buffer grows beyond its initial bufferSize;
  its internal session/channel interfaces are not a supported public replacement seam. Incoming
  backpressure remains required; this input/UI/lifetime fix does not claim that dependency quota.
  Also remaining: native encrypted-key/cancellation/fault UI, preview/interceptor retirement,
  small-window/insets/D-pad, wider hostile preview cases, diagnostics cleanup and full final
  exact-package regression. Full goal remains active. No commits, pushes, publication or mail.

- Refreshed the local code graph after the ownership changes:13552 nodes/51558 edges; extraction
  retried35 uncached files without an LLM/API key. Existing two Kotlin partial-parse and two empty
  palette/checksum-node warnings remain index limitations, not Android compiler or runtime tests.

- Re-clustering completed with13552 nodes/50079 collapsed edges and508 communities. HTML output
  was intentionally skipped; source/index changes did not alter the signed TPK or Android code.


### Android visual parity follow-up — 2026-10-20

- Explicitly set zero vertical and 12px horizontal padding on native input fields,
  retaining vertical center alignment. Added 8px rounded outlines, palette-derived
  placeholder colors and Amber accent outlines. The current pre-change emulator
  already showed centered text; the originally reported state was not reproduced.
- Matched Android surface/foreground button colors, rounded corners, centered button
  labels and selected theme/authentication accent containers (18 percent blend).
  Android remains unchanged. Portal PNG hashes and all three toolbar SVG path data
  match the Android resources exactly; existing identical icon artwork is retained.
- Built with zero errors and seven existing vendored warnings. Installed through SDK
  job 20261020T132823-def03a on emulator-26111. Default portable checks: 133 passes.
  Actual screenshot measures Name glyph center y242 versus field center y241.
  Seven native inputs render; Light settings screenshot was inspected. Native theme
  selection checks cover all six presets. These checks do not establish physical
  device or native narrow-width parity.
- Signed emulator-only TPK SHA256:
  a6f03dbc88cb596d2bda14e76a4eedb5b6d913f87713a83a798d2195f71b6cd2.
  Evidence: style-input-after.png and style-light-after.png under
  /tmp/hexgate-tizen-evidence; ignored SDK receipts and test logs under .tools.
  The complete port goal remains open; this is the requested UI follow-up only.


### Preview lifetime and adaptive runtime follow-up — 2026-10-20

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


### Remaining-goal execution (2026-10-21)

- Scope: first establish a real-wire malformed-SFTP regression with a successful
  version/realpath handshake; measure reassembly and allocation, then add the
  smallest receive-boundary correction if the regression fails. Retain normal
  SFTP operations and the existing SSH/preview ownership.
- Validate oversized, split, incomplete and consecutive frames. Update vendored
  source provenance after any library change. Rebuild/sign/install one final TPK
  and associate affected native and host regressions with its checksum.
- Reconcile README, TEST_REPORT, acceptance, validation and progress only with
  observed results. Physical-device input/power/network/certification remains a
  separate follow-up; no automatic commit, push or publication.
- Existing malformed fixture log fails before the SFTP handshake; it is not
  evidence of either boundedness or memory growth. Investigation continues.

- Real-wire SFTP gate reproduced 2,424,836 buffered bytes after repairing the
  fixture's rejected subsystem. Added per-frame 1 MiB length/admission/reassembly
  bound; a 12-case matrix passed, including maximum allocation, continuous valid
  traffic, malformed headers, incomplete timeouts and cancellation. Reproducible
  disposable runner passes208 (151 shared +56 SFTP +1 cleanup).
- Updated SSH.NET assembly adaptation2, patch/checksums/notices. Fresh host
  portable/OpenSSH/authentication suites pass151/198/161. New signed TPK hash is
  be86d3f3f51d02b065c4d0ac17bc7116785afd1519aca0cc45d8b32b1b524ff2; signatures,
  70/71 digests, DLL equality and archive exclusions passed. Installed/launched
  on actual emulator-26101 (tizen-10-1-20261016). Native regression continues.

- Final TPK native regression passed rectangle clipboard, unsolicited OSC52
  rejection, exact Ctrl+C/V/Tab/Enter, input-dialog commit/focus restoration
  (zero unknown bytes),2MiB actual terminal output and font/refresh preservation.
  Normal exit was requested with96 SFTP request bytes at the SIGSTOPped own peer;
  the peer was resumed in finally. Its initial2.437s /proc-based exit proof was
  invalidated because the VM hides live app /proc entries; launcher-list proof
  is required and is being gathered. Ordinary relaunch retained the secure password.
  Actual same-port host-key rotation was rejected before any password attempt.
- Replaying the dependency patch revealed pre-existing BOM-removal omissions in
  its textual diff. Regenerated the diff without changing compiled sources; it
  now reproduces all recorded source checksums exactly from pinned upstream.
- New private Herdr first setup received input while its settings/sidebar held
  focus. No cwd-change success is credited to that attempt. Native focus setup
  is being corrected on the same live app/session; no timeout-driven restart.


### Remaining-goal completion audit — 2026-10-21

- Goals1-3 passed on exact signed TPK SHA256
  be86d3f3f51d02b065c4d0ac17bc7116785afd1519aca0cc45d8b32b1b524ff2.
  Real-wire SFTP red/green, per-frame/backing-array bound, normal SSH/SFTP and
  native four-format/auth/input/context/lifetime regression are recorded in
  tizen/docs/acceptance.md. Fresh totals151 portable,198 integration,161 auth,
 208 framing runner and8 Python tests; shared151 assertions are not summed.
- Replaced the invalid /proc exit receipt with launcher-list present/absent
  proof during a stopped-peer request:96 queued bytes,2.101s normal exit, peer
  resumed in finally and active0. Final ordinary cleanup exit also absent from
  launcher. Own secure profiles deleted with no pending cleanup, staging fixture
  contents cleared, all own peers/private Herdr config/bootstrap/forward stopped.
- Package signatures/digests/all-payload coverage, source patch replay, checksum
  and DLL equality pass. READMEs, TEST_REPORT, acceptance and validation now
  describe the same artifact/status; historical package checks remain scoped.
  Android is unchanged and the pre-existing22222 listener is preserved.
- Goal4 remains separate: physical input/IME/D-pad/resolutions, power/background,
  real networks and device signing require hardware and recorded per-case results.
  No automatic commit, push or publication.
