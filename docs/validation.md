# Validation

This document separates recorded development evidence from remaining validation work.
The checked-in tests are reproducible; raw logs, APKs, screenshots, generated keys, and private
delivery metadata are deliberately excluded from Git.

## Environment

Development used JDK 17, Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.1.20,
compile/target SDK 36, and minimum API 26. Runtime checks used generic API 37 x86_64 Android
emulators: a foldable profile with inner/cover postures and a 1600×2560 tablet profile.
These were Windows-hosted emulators using hardware virtualization and software graphics.

Network integration used an isolated WSL loopback SSH fixture on port 22222 with generated
throwaway keys and unique Herdr session names. Tests did not target an existing development
Herdr session or change the production SSH service. Herdr 0.9.4 was used during development.

## Recorded results

The following are historical passing runs, not a claim that every suite was rerun for each
later documentation-only change. Source tests remain under `app/src/androidTest/` and
`app/src/test/`; Python context tests are under `scripts/`.

| Area | Recorded evidence | Limits |
| --- | --- | --- |
| Core SSH, terminal, previews | Initial core integration suites passed on generic tablet and foldable emulators | Not physical-device certification |
| Host trust / encrypted keys | Explicit changed-key rejection, encrypted OpenSSH authentication, and encrypted local storage tested | User must verify real server fingerprints |
| Unicode / IME / input | Native IME commits, CJK, bracketed paste, Enter, and soft-keyboard reopening tested | Physical Samsung IME and Bluetooth layouts remain unverified |
| Fold / resize / reconnect | Retired-view ownership, cover/inner recreation, rotation, PTY size and retained Herdr output tested | Device-specific desktop modes remain unverified |
| Preview isolation | Markdown/Mermaid/draw.io, embedded resources, static HTML, and path/symlink rejection tested | Only local bundled renderer resources supported |
| Background service | Connection retained during backgrounding, 20-second screen-off, and window reopen on fold/tablet profiles | No long-duration Samsung Doze guarantee |
| Permission / service cleanup | Battery consent, notification disconnect action, and service shutdown tested | Manufacturer power controls may differ |
| Process recovery | Separate seed/verify processes with force-stop passed | A killed process requires reconnect |
| Immersive display | Status/navigation visibility, Activity recreation, retained SSH and Enter action passed on fold/tablet | Physical taskbar behavior remains unverified |
| Clipboard shortcuts | 4 tests passed in the final 0.1.10 clipboard run | Synthetic emulator clipboard only |
| IME / Herdr regression | 3 tests passed after the 0.1.10 input changes | Isolated named Herdr sessions |
| Rectangular selection | 5 tests passed in the final 0.1.11 selection run | Panel-shaped synthetic text; no automatic remote pane clipping |

The rectangular-selection run checked matching clipboard text and bitmap highlighting,
unchanged adjacent-pane pixels, reverse ranges, whole Korean glyphs, ordinary drag behavior,
context Copy, and terminal ownership. Clipboard tests additionally verified actual SSH paste,
plain Ctrl+V as raw byte 22, and plain Ctrl+C interrupting a command even during selection.

JVM transport policy tests contain five passing cases. The most recent application lint run
reported zero errors and 28 warnings. Warnings include dependency/style guidance and intentional
background-power APIs; they are not suppressed as a substitute for validation.

Historical appearance checks and performance observations are retained in
[UI validation](ui-refresh-validation.md) and [panel/theme validation](panel-themes-validation.md).
Those results describe their respective versions and debug/emulator environments only.

## Reproduce

Follow [CONTRIBUTING.md](../CONTRIBUTING.md) for the SDK/JDK setup and isolated SSH fixture.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Representative focused suites:

```sh
HEXGATE_TEST_CLASS=dev.hexgate.RectangularSelectionTest,dev.hexgate.ContextCopyTest \
  scripts/emulator-test.sh
HEXGATE_TEST_CLASS=dev.hexgate.ClipboardShortcutsTest,dev.hexgate.TerminalOwnershipTest \
  scripts/emulator-test.sh
```

Keep the fixture listener alive for SSH tests. The process-recovery runner intentionally
force-stops only the test app between stages. Inspect actual instrumentation results and test
counts rather than relying on an APK install or script exit alone.

## Artifact checks

Development APKs were checked for ZIP integrity, signing-certificate continuity, vendored asset
hashes, and exclusion of generated fixture/device private keys. The 0.1.11 evaluation build had
SHA256 `862af08cc0803b8f523770b2322678fb9556fbb1ab4c6eb813530616fd6e9918`.
A rebuild can differ because signing keys and build environments differ; this is provenance
for the privately delivered evaluation artifact, not a reproducible-build guarantee.

## Remaining work

- Physical Galaxy foldable/tablet behavior, Samsung IME, external keyboard and mouse mappings.
- Long screen-off, Doze, manufacturer battery policy, and real Wi-Fi/mobile network changes.
- Samsung DeX/taskbar and multiwindow presentation.
- Runtime coverage for older supported Android versions.
- Production signing, store policy review, privacy declarations, and broader performance profiling.

No Play Store release or general hardware/security certification is implied by these results.
