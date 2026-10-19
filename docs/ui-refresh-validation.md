# UI refresh v0.1.1 validation — 2026-10-19

Implemented in native Kotlin/Compose using the installed Claude Design skill's Command/Inspect approach. No hosted Claude model or extra UI framework was introduced. Scope is debug-device evaluation; no Play Console registration, release signing or public deployment was performed.

Changes: original gold/blue portal artwork and adaptive launcher icon, structured app header and SSH state strip, distinct active-preview styling, tactile 44dp-minimum keyboard accessories, grouped profile controls, empty-state connection action, and an Appearance dialog with three persistent Nerd Font Mono family choices plus size/sample. Preview CSS follows the navy/cyan palette; static HTML trust mode and CSP remain unchanged.

Fonts: existing JetBrains Mono plus Fira Code and Source Code Pro (patched family SauceCodePro), added from Nerd Fonts v3.4.0. Individual OFL and aggregate glyph notices are included; font bytes and artwork hashes are recorded in renderers/vendor-checksums.json. Free redistribution is subject to the included license conditions, with patched font names preserved.

Verification:

- Build, JVM unit suite and lint passed: artifacts/ui-refresh-build.txt. Lint has zero errors and existing advisory/style warnings; no suppression added.
- Real WSL SSH/Herdr plus preview regression: 12 passed, artifacts/ui-refresh-core-tests.txt. Includes Markdown/Mermaid/draw.io/static HTML isolation, host trust, encrypted key authentication, IME and network/recreation behavior.
- Appearance-specific integration: 2 passed, artifacts/ui-refresh-appearance-tests.txt. Native Paint confirms equal-width ASCII/box characters and required Nerd glyphs/CJK fallback in every bundled face. Actual Herdr accepts input after changing each face; selected native Typeface is applied, connection survives Activity recreation, and saved font choice reads back.
- Fold, cover, rotation and actual split-divider test: 1 passed, artifacts/ui-refresh-posture-tests.txt. Source retains the existing 840dp layout threshold.
- Wide/narrow screenshots and Appearance dialog were captured and inspected. Synthetic test content only; actual Samsung IME/Bluetooth/battery/DeX and real public-network access remain unverified.
- New APK is dist/hexgate-0.1.1-debug.apk, 23,383,699 bytes; signature verification is recorded in artifacts/ui-refresh-apk-signature.txt. Original v0.1 files are preserved. Packaged font licenses and absence of fixture keys were checked before delivery.

Performance snapshot on the same generic Fold AVD: observed IME/SSH response 222 ms with 100 ms polling; 10,000-line output 245 ms with 50 ms polling; 6 sampled frames, all over 16.67 ms, maximum 41.56 ms; app PSS 140,496 KiB. See artifacts/ui-refresh-performance.json. Debug/Swiftshader results include renderer/font/host effects, are too small for percentile claims and do not imply zero stutter or physical-device performance.

Text contrast checks against relevant flat surfaces: main text 14.46:1, muted text 9.12:1, gold accent 10.43:1, selected inspector text 9.28:1, primary button text 9.99:1. These cover the selected token pairs, not every third-party terminal color or arbitrary remote document.

Artwork prompt/provenance: assets/branding/README.md. No extracted game artwork or Riot logo is bundled. The generated image remains a user-reviewable design candidate; branding changes do not affect SSH behavior.
