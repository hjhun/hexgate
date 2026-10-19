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
