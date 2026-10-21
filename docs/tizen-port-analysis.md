# Hexgate Tizen NUI port analysis

## Scope and observed repository state

The requested target is a C# Tizen NUI application named Hexgate. Both package ID and application
ID must be `org.tizen.hexgate`. The local `tizen` branch was created from `main` at `c472912`.
No application implementation, package installation, signing changes, commits or pushes were
performed during this analysis. Android code remains the behavior reference and is preserved.

The existing Android implementation uses Kotlin/Compose, SSHJ, an Android-dependent terminal
View/emulator, Android Keystore, and an isolated WebView document preview. These are not portable
NUI components. Reuse behavior contracts, fixtures, licensed fonts/icons/themes and local preview
assets after reviewing their licenses; do not assume Android runtime code can execute on Tizen.

## Local reference applications inspected

Reference root: `/home/hjhun/samba/workspace/tizen-action-examples` (read-only reference).

- `Calendar/src/Calendar.App/Calendar.App.csproj`: net8.0, Tizen.NET 14.0.0.19326, separate
  platform-independent projects, and linked shared UI dispatch code.
- `Calendar/src/Calendar.App/CalendarApplication.cs`: NUI composition root, NUI synchronization
  context, subscriptions, pause/resume handling and termination cleanup.
- `Calendar/src/Calendar.App/ProportionalViewport.cs` and `CalendarDisplayMetrics.cs`: validated
  window/inset geometry and a single uniform reference-canvas transform. Adapt this for chrome;
  terminal cell geometry must derive from the real drawable viewport rather than a stretched
  1920x1080 terminal bitmap or fixed PTY size.
- `Shared/Ui/UiChangeDispatcher.cs`: coalesced UI-thread updates with disposal guards.
- `Calendar/build.sh` and `Calendar/package.sh`: separate managed build and signed ZIP-based TPK
  packaging, including flattened output staging, manifest and signature checks. The manifest
  declares common profile/API 10.0 while its UI application declares API 14. This is a reference
  observation, not proof that the same combination is appropriate for Hexgate's selected target.
- `Browser/src/Browser.App/NuiWebViewRuntime.cs`: system WebView construction and serialized,
  UI-thread-bound navigation with cancellation/disposal. It is a browser navigation adapter,
  not a ready-made secure local document/terminal bridge.

Do not copy Calendar's alarms, Action providers, entities, annotations, or privileges without
an actual Hexgate requirement. Its root AGENTS.md explicitly treats Calendar as a reference,
not a template to copy wholesale. Hexgate's own repository instructions govern the new work.

## Verified local tools and target

- `dotnet --list-sdks`: SDK 8.0.131 is installed.
- Tizen.NET 14.0.0.19326 is present in the local NuGet cache.
- Tizen device-manager runner reported connected emulator `emulator-26111`, profile `tizen`.
  It did not launch an emulator. This discovery does not establish the target API level,
  resolution, .NET runtime compatibility, secure repository privileges, or installed WebView
  capabilities. Inspect these before implementation; no Hexgate Tizen runtime test has run.

## Proposed architecture and early feasibility gates

Start with a small layout under `tizen/`: `Hexgate.App` for NUI/platform composition, a portable
`Hexgate.Core` for connection/profile/terminal/preview contracts and state, and platform adapters
only where required. Avoid duplicating Calendar's full project/provider hierarchy.

1. Prove an actual packaged NUI application on the selected emulator and confirm both IDs.
2. Prove SSH transport plus a mature VT terminal engine and rendering/input integration before
   building the full UI. SSH.NET is a candidate, not a verified Tizen dependency. Do not implement
   a VT parser from scratch or present terminal output as an append-only TextLabel.
3. Prefer native NUI terminal rendering with a reused terminal core. A local WebView terminal
   engine is an explicitly documented fallback if native engine integration fails its measured
   feasibility gate. NUI must still own the application shell, connection editor and settings.
4. Prove Tizen secure repository/key-manager access for persisted passwords and private keys.
   If unavailable, use an explicit temporary memory-only mode and report the unmet secure-save
   requirement; never silently persist plaintext or use a fixed embedded encryption key.
5. Prove safe local document rendering: static HTML without arbitrary scripts/external access,
   with separately trusted local Markdown/Mermaid/draw.io renderers and bounded SFTP resources.
6. Prove pause/resume and view/viewport changes preserve a live session where the target permits
   it. Android foreground services and wake locks are not a Tizen lifecycle design. Do not promise
   survival of process termination, suspend or emulator shutdown.

## Behavior to retain

Saved profiles with key/password authentication, explicit SHA256 host trust, last-profile
reconnect, a collapsible left drawer, Preview and Settings near its bottom, themes/fonts,
refresh and full-screen toggles, Enter after Tab, IME/hardware input, local Ctrl+Shift+C/V,
box selection, and Herdr-owned tracked drag/Shift-copy behavior. Preserve bounds, stale-session
protection, cancellation and secret-safe logging. Preview pane context must belong to this
client's named Herdr session/TTY, with explicit file/pane selection when ambiguous.

Use isolated SSH/Herdr fixtures. Do not change production sshd, router, VPN, reference app data,
other emulator apps, or existing developer sessions. Host-only tests, TPK checks, emulator input,
visual evidence and physical-device verification are distinct validation layers.

## Primary external references

- SSH.NET project and license: https://github.com/sshnet/SSH.NET
- Tizen secure storage concepts: https://github.com/Samsung/tizen-docs/blob/master/docs/application/native/guides/security/overview.md

These references establish candidate capabilities, not successful execution on this emulator.
