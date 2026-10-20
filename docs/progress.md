# Development status

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
