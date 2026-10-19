# Hexgate 0.1.3 navigation, themes and preview validation

Date: 2026-10-19. Target: generic API 37 Fold AVD, Windows WHPX/Swiftshader, actual isolated WSL SSH/Herdr fixture. This revision was not rerun on the separate Tablet AVD; earlier tablet results belong to the prior version. Physical Samsung devices and external-network routing remain unverified.

## Behavior

- Approved v3 artwork is shared by launcher and workspace. A collapsible left panel groups saved connections, add/manage, Preview near the bottom and Settings at the bottom. It overlays the terminal without changing PTY width.
- Fresh launch connects once to the last successfully authenticated saved profile by default. Settings can disable this. Encrypted-key unlock, explicit host trust, cancellation, manual disconnect and recreation remain bounded; passphrases are not persisted.
- Seven persistent theme choices cover Hexgate Dark, Light, System, Amber, Dracula, Gruvbox Dark and Solarized Dark. Fonts and size live in Settings. Public palette provenance and MIT notices are in docs/themes.md and packaged assets. Explicit remote colors remain authored by the remote application/document.

## Preview correction

Visual inspection found missing Mermaid and an unconverted draw.io image in a capture that passed a readiness-only test. The new real-WebView rerender regression failed before the fix: readiness remained true while an asynchronous rerender was starting (`artifacts/preview-race-before.txt`).

Renderer requests now clear readiness synchronously, run through one serial queue for Mermaid's shared state and discard superseded results. Each WebView document navigation has a unique query token; page-finished dispatch happens once for the current URL. These safeguards remove stale/duplicate callback entry points without changing SFTP resource roots or HTML trust settings.

Theme validation now requires a visible Mermaid SVG, embedded draw.io SVG and decoded relative image, plus native terminal colors and status-bar contrast. Light and Amber captures were visually inspected and contain both diagrams. HTML remains JavaScript-disabled with no native bridge; remote reads and CSP boundaries are unchanged.

## Evidence

- Build, 5 JVM tests, and lint completed successfully: `.tools/preview-fix-build.log`. Lint: zero errors, 21 warnings; no suppression added.
- Preview/startup/theme instrumentation: 8 passed in 54.25 seconds, `artifacts/preview-fix-tests.txt`. Includes the previously failing rerender regression, all preview types, static HTML isolation and all seven theme choices.
- Initial workspace runs retained in `artifacts/preview-fix-workspace-tests.txt` and `preview-fix-workspace-recheck.txt`: 10 of 11 passed. Splitting the combined font assertion showed the native face did apply, but the sample SSH output was sometimes missing after a font change. The test had sent input concurrently with the asynchronous native font/PTY resize. The test now waits for native face and stable terminal geometry before typing. Targeted font validation then passed 2 tests in 11.674 seconds (`preview-fix-font-stable-tests.txt`). Final combined rerun is recorded separately below.
- Current screenshots: `artifacts/preview-fixed-theme-light.png`, `preview-fixed-theme-amber.png`, `preview-fixed-panel-connections.png`, `preview-fixed-panel-settings.png`. Other cache artifacts collected under the same prefix may predate this revision; only these and preview-specific screenshots are used as current evidence.
- APK: `dist/hexgate-0.1.3-debug.apk`, version code 4, min SDK 26, target SDK 36, 24,267,382 bytes. Signature verified (`artifacts/preview-fix-apk-signature.txt`), ZIP checked, approved icon bytes match, renderer/font palette hashes match and fixture private keys are excluded (`artifacts/preview-fix-apk-audit.json`).
- SHA256: `a766f99a71b18fd78810218a5bcfebcc68e9b6268e7921d16dd059d8d8f90fcf`.

No commit, push, public publication, router/firewall or system SSH changes were made. Private delivery uses files owned only by the user's Gmail account, with no public sharing permissions.

Final combined workspace rerun: **11 passed in 53.913 seconds**, `artifacts/preview-fix-workspace-final.txt`. Together with the targeted preview/startup run, all 19 distinct current instrumentation cases passed. This final rerun used the stabilized font-input sequence.
