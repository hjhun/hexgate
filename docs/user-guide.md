# User guide

## Install

Hexgate requires Android 8.0 / API 26 or later. Build a debug APK as described in the
[README](../README.md), then install it:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Install updates over the existing app when they use the same signing certificate. Uninstalling
removes the app's profiles and encrypted keys. A debug APK built on a different machine can
have a different signing identity.

## Connect to a server

1. Prepare a reachable OpenSSH server and a separate key for each device.
2. Open the left drawer and add a connection.
3. Enter a profile name, host, port, and SSH username. Import an OpenSSH private key through
   the Android document picker. Do not import the `.pub` file.
4. Enter a dedicated Herdr session name if desired. Leaving it blank opens a regular shell
   or runs the configured startup command.
5. Save the profile and select it in the drawer.
6. Compare the server's SHA256 host fingerprint with a value obtained directly on the server
   before trusting it. A changed host key requires explicit confirmation.

Encrypted-key passphrases are requested when needed and remain in memory. The imported key
copy is encrypted with an Android Keystore-backed key. The original file remains under your
control; remove unnecessary downloaded copies after successful import and verification.

By default, a fresh launch connects to the last successfully used profile. Disable this in
**Settings → Connect to last session on launch**. Reopening a window with an already active
connection reuses that connection. Canceling a passphrase prompt or manually disconnecting does
not trigger an automatic reconnect merely because the screen rotates.

## Terminal and keyboard

Tap the terminal to open the Android IME, or use **Keyboard** at the start of the accessory bar.
Keyboard also returns from a narrow preview-only view to the terminal and exits selection mode.
Hardware keyboards remain supported.

The accessory bar includes Ctrl, Alt, Esc, Tab, Enter, arrows, Select, Copy, and Paste. Enter is
immediately after Tab. Scroll the bar horizontally on narrow windows. Ctrl and Alt accessories
apply to the next key.

| Action | Input |
| --- | --- |
| Linear text selection | Left mouse drag |
| Rectangular text selection | Hold Ctrl+Shift before starting a left drag |
| Copy selected text | Ctrl+Shift+C or context-menu Copy |
| Paste clipboard text | Ctrl+Shift+V or context-menu Paste |
| Terminal control input | Plain Ctrl+C / Ctrl+V |
| Remote TUI mouse drag | Alt+drag with Select mode off |

Context-menu clipboard labels currently follow the app's Korean popup labels. Copy is disabled
when no text is selected. Select mode suppresses remote mouse reports; long-press selection and
the accessory Copy/Paste controls are also available.

For rectangular selection, each selected row contributes only the specified column range.
Bound the rectangle inside a Herdr pane to avoid copying adjacent panes. Reverse-direction
selection and full-width Unicode characters are supported. Plain Ctrl+C/V retain terminal
semantics even when a selection is present.

## Background connection and reconnect

A foreground service maintains a user-initiated SSH connection while the app is backgrounded
or its window is reopened. Android 13+ notification permission is requested on first connection.
The connection notification can reopen Hexgate or disconnect. Notification denial does not
prevent SSH authentication.

For longer screen-off use, open **Settings → Background connection permission** and review
Android's battery optimization exception prompt. This is separate from notification consent.
The service's CPU wake lock is released when the service ends.

Android force-stop, manufacturer power policies, network loss, or server shutdown can still
terminate SSH. After network or process loss, select the saved profile to reconnect. A running
remote Herdr session can survive an SSH disconnect; shutting down its host does not preserve
its processes. See the [connection guide](connection-guide.md) for WSL lifetime considerations.

## Refresh and full screen

Use the header refresh icon to repaint the terminal and request remote PTY redraw without
reconnecting or submitting the pending command. The full-screen icon beside it hides Android
status and navigation bars; the exit icon returns to normal mode. Android may display a first-use
immersive-mode tutorial. Edge swipes can reveal system bars temporarily.

Full-screen state and the active terminal are retained through Activity recreation. Terminal
size changes are forwarded to the remote PTY during folding, rotation, IME, and split resizing.

## Preview documents

Select **Preview** near the bottom of the left drawer. Hexgate queries the named Herdr session
and this SSH client's TTY. If the selected pane cannot be identified safely, choose a pane
explicitly. Another client's global focus is not silently reused. Automatic editor-file detection
is not provided.

Browse the selected working directory or reopen a recent file. Supported formats include:

- Markdown, including Mermaid fences and relative images/diagrams.
- Standalone Mermaid (`.mmd` and `.mermaid`).
- Static HTML (`.html` and `.htm`).
- Standalone or embedded draw.io XML (`.drawio` and supported `.xml`).

Wide windows show a resizable terminal/preview split. Narrow windows switch between panes.
Documents and resources are read through SFTP. The current limits are 2 MiB per document,
1 MiB per resource, and 16 MiB / 64 resources per document. Paths must stay within the canonical
document directory; symlink escapes and external URLs are rejected.

HTML does not run document scripts. Trusted local Mermaid/draw.io engines are isolated from
untrusted HTML. Remote shape libraries, math engines, and network resources are not loaded.

## Appearance

Use **Settings** at the bottom of the drawer for theme, font family, and font size. Themes are
Hexgate Dark, Light, Follow system, Amber, Dracula, Gruvbox Dark, and Solarized Dark. Fonts are
JetBrains Mono, Fira Code, and SauceCodePro Nerd Font Mono; Android provides CJK fallback.
Selections persist across launches. Explicit colors emitted by remote applications or contained
in documents may remain unchanged.

## Validation scope

Generic foldable and tablet emulators have exercised real SSH/Herdr, Unicode input, previews,
folding, and process recovery. They are not Samsung firmware emulations. Physical Samsung IME,
Bluetooth input, DeX/taskbar behavior, long screen-off operation, and external-network reachability
remain device-specific validation work. See [validation.md](validation.md).
