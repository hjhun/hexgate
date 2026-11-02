# Hexgate v1 design

Approved direction: Kotlin native Android SSH client for foldable phones and tablets. Compose hosts a mature native terminal View; an on-demand WebView previews remote documents. No required VPN, relay, public web server or public Herdr socket.

## Modules

- Connection: profiles, explicit host trust, encrypted key/password storage, SSH PTY and SFTP. Network work stays off the main thread. Lifecycle survives Activity recreation; process death requires reconnect.
- Terminal: mature emulator handles byte streams, VT state, UTF-8, cursor, color, mouse and IME. Output bypasses Compose state. Scrollback and input queues are bounded. Resize updates remote PTY dimensions.
- Remote workspace: identify this client's Herdr session and selected pane without assuming another client's focus. Use an explicit captured pane identifier and validated cwd. Fall back to explicit pane selection if installed Herdr cannot identify client-local focus safely.
- Preview: bounded SFTP reads; Markdown, Mermaid, HTML and draw.io rendered locally. Untrusted HTML cannot run scripts or access native interfaces. Trusted diagram renderers are separated from document scripts. Relative resources are scoped to the selected remote document tree.
- UI: narrow windows switch terminal/preview; wide windows show resizable side-by-side panes. Input accessory keys, font size, selection mode and profile settings.

## Validation gates

1. Prove SSH stream/native terminal integration before selecting the engine.
2. Build and exercise real SSH in an isolated test server/session; host trust, resize, Unicode and reconnect checks.
3. Exercise Markdown/Mermaid/HTML/draw.io fixtures and hostile document isolation.
4. Foldable and tablet emulator screenshots, IME and window changes, startup/memory/output measurements, APK.
5. Document unverified physical Samsung keyboard, battery, background and external network behavior.

## Direct connection

LAN uses a reachable PC address. Internet access requires a public reachable address, optional DDNS, router forwarding and Windows-to-WSL forwarding as appropriate. Do not change router/firewall/sshd settings without approval. WSL IP changes and startup recovery need documented handling.

## Resolved investigations

Installed Herdr 0.9.4 was checked against real commands and its source. Client focus is inferred only for the unique matching SSH TTY; otherwise a pane picker is required. Termux library-only Java terminal adapter passed real SSH/Herdr checks. Nerd Font/CJK glyphs were exercised on the emulator. Renderers are packaged locally; the debug APK is approximately 20 MB. Windows SDK/AVDs and hardware virtualization are available. Physical Samsung behavior and public networking remain explicit environmental validation boundaries in docs/validation.md.

## Background connection and display refresh — 2026-10-19

An application-owned ViewModelStore retains the current terminal/SSH connection while a window is recreated, backgrounded or finished. Idle sessions are released after the last window closes; reconnect-on-launch never replaces an already active session. A non-exported specialUse foreground service protects a user-initiated connecting/connected SSH session, posts a private ongoing notification with Return/Disconnect, and holds a partial wake lock only for that service lifetime. Android 13+ notification permission is requested once on first connection; denial does not prevent SSH. Failure, explicit disconnect or remote EOF stops the service. No automatic service restart or credential replay after process death is introduced.

The header Refresh terminal icon invalidates the native view and serializes a temporary one-column PTY resize followed by the latest actual size, prompting remote SIGWINCH redraw without typing Ctrl-L, Enter, or a shell command. The transport generation and shell identity guard stale requests. No pull gesture consumes terminal selection, scroll or mouse events. Existing trust, encrypted key storage and preview isolation remain unchanged. Force-stop, network loss, server shutdown and manufacturer battery policies can still end a connection; physical Samsung screen-off endurance must be verified on hardware.

Settings includes an explicit **Background connection permission** button. It opens Android's per-app battery optimization consent dialog (or the optimization list if already exempt); fallback is the app details page. This is opt-in, separate from notification consent, because a persistent interactive SSH connection cannot be replaced by deferred WorkManager jobs or FCM. It does not silently change device power settings. Android documentation: https://developer.android.com/training/monitoring-device-state/doze-standby . Lint flags the direct exception request and connection-scoped un-timed wake lock; these are retained intentionally for the requested screen-off interactive connection, with release on every connection termination path.

## Immersive window and Enter accessory — 2026-10-19

The header places an Enter/Exit full screen toggle immediately after Refresh terminal. WindowInsetsControllerCompat hides both status and navigation bars in immersive mode; Android's edge swipe can reveal them transiently. Normal mode restores both bars. Activity saved state retains the mode through configuration recreation, and window focus restoration reapplies it after dialogs or returning to the app. Safe-drawing/cutout and IME insets continue to protect input; the SSH model is not recreated. The horizontally scrollable accessory bar adds Enter immediately after Tab, using the existing terminal key encoder and modifier handling.

## Mouse context-menu copy — 2026-10-19

Secondary mouse button events are handled locally before native text-selection gesture handling; both touch-dispatched mouse down/up and generic button press/release open the Android context menu without forwarding right clicks to the remote terminal. The app populates a Korean Copy item (Copy), enabled only for a nonempty text selection. Selection is snapshotted while constructing the menu, including the native More-menu stored-selection fallback, so focus/toolbar changes cannot erase the copy source. Existing accessory Copy and encrypted/remote transport behavior remain unchanged. No text is copied merely by opening the menu.

## Drag selection and explicit clipboard shortcuts — 2026-10-19

Primary mouse dragging selects local terminal text by default, including reversed ranges and full-width Unicode boundaries. A click without dragging is replayed to the existing terminal mouse path; Alt+drag retains remote TUI mouse handling when Select mode is off. The final mouse-up coordinate is included and pending events are recycled on window detach. Touch gestures retain their existing behavior, and mouse clicks no longer request the soft keyboard.

User correction: only Ctrl+Shift+C copies and Ctrl+Shift+V pastes locally. Plain Ctrl+C and Ctrl+V always remain terminal control input, including when a selection exists. Clipboard shortcuts are handled before native typing clears the selection, with pure modifier keys preserving it. The right-click menu also includes Paste, using the existing bounded/native bracketed-paste path. Ctrl+Shift+C without selected text is a no-op. No ordinary Ctrl shortcut is redefined.

## Ctrl+Shift rectangular mouse selection — 2026-10-19

Ctrl+Shift held at primary mouse-down selects a rectangular block instead of a linear stream. The modifier is latched for the gesture; reversed directions normalize X and Y independently. Every selected row is highlighted at the same column range and copied row-by-row, separated by newlines. Full-width characters intersecting the edges are selected/copied whole. Ordinary drag retains existing linear behavior, and Ctrl+Shift+C/V retain their clipboard actions. Manual rectangle bounds let users isolate text inside one Herdr pane without inferring untrusted/global Herdr focus or changing remote mouse state. The original renderer API remains a linear-selection overload for compatibility.

## Password authentication

Profiles explicitly select private-key or password authentication; missing legacy metadata
means private key. Account passwords have separate AES-GCM vault records protected by the
existing Android Keystore key, with profile/type-bound associated data. The connection editor
never preloads decrypted passwords. Empty editing input retains a stored password; replacement
input updates it. Key mode removes the stored password; profile deletion removes both kinds of
credentials. Password profiles skip key loading and use SSHJ password authentication after
normal host trust. Existing session/background ownership and SFTP/PTY paths are shared.

## Shift and simultaneous accessory input — 2026-10-29

The Android accessory bar places Shift immediately before the arrow keys. Ctrl, Alt and
Shift share a tap-to-latch / hold-to-modify state: a tap applies to the next input, while a
held modifier applies to every input until release. Used or cancelled holds never latch.
Ordinary accessory keys retain horizontal scrolling and capture the modifier mask at touch
down, so releasing a modifier before the key does not lose the chord. Native key events and
IME commits read the same state; multi-character IME insertion bypasses the paste fast path
when Shift is active. Existing Ctrl+Shift+C/V clipboard shortcuts remain available.

## Korean composition before key events — 2026-10-29

The terminal retains the active native input connection and finalizes pending composing
text before processing an ordinary key event. This preserves the order of a final Korean
syllable followed by punctuation or Enter. Modifier-only events do not end composition;
the incoming key's modifiers are captured before flushing. The input-connection reference
is dropped when the terminal view detaches. Later finishComposingText calls do not resend
already finalized text.
