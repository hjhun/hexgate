# Changelog

All versions below are development builds. Dates use the development record's local date.

## 0.1.11 — 2026-10-19

- Add Ctrl+Shift+drag rectangular selection with matching highlighting and clipboard extraction.
- Preserve whole wide Unicode glyphs at rectangle boundaries and support reverse dragging.

## 0.1.10 — 2026-10-19

- Add local mouse drag selection and context-menu paste.
- Use Ctrl+Shift+C/V for clipboard operations; preserve plain Ctrl+C/V as terminal input.
- Preserve remote TUI mouse dragging with Alt and avoid opening the IME on mouse clicks.

## 0.1.9 — 2026-10-19

- Add right-click Copy while terminal text is selected.
- Snapshot selection text before context-menu focus changes.

## 0.1.8 — 2026-10-19

- Move the Enter accessory immediately after Tab.

## 0.1.7 — 2026-10-19

- Add immersive full-screen/window-mode switching beside Refresh.
- Add an Enter accessory and preserve immersive state through Activity recreation.

## 0.1.6 — 2026-10-19

- Retain live SSH sessions across backgrounding and window reopening.
- Add an ongoing foreground service, notification actions, and battery-consent entry point.
- Add terminal display refresh without reconnecting or sending a shell command.

## 0.1.5 — 2026-10-19

- Prevent retired foldable windows from resizing the active terminal.
- Bind terminal transports to their own SSH connection and replay the latest PTY size.

## 0.1.4 — 2026-10-19

- Fix touch focus and Android soft-keyboard input.
- Add a Keyboard accessory and update IME metadata/inset handling.

## 0.1.3 — 2026-10-19

- Add a collapsible connection drawer, startup reconnect preferences, and seven themes.
- Fix preview readiness/rerender races and preserve selected documents across theme changes.

## 0.1.2 — 2026-10-19

- Apply the approved minimal hexagonal portal icon with a white terminal prompt.

## 0.1.1 — 2026-10-19

- Refresh the native interface, launcher artwork, appearance settings, and bundled fonts.

## 0.1.0 — 2026-10-19

- Introduce the Kotlin/Compose SSH client with native terminal emulation and adaptive layouts.
- Add key import/storage, host-key trust, SFTP, named Herdr sessions, and local document previews.
