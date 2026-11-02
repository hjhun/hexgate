# Android packages

## Hexgate 0.1.15

- APK: [hexgate-0.1.15-debug.apk](hexgate-0.1.15-debug.apk)
- Android application ID: `dev.hexgate`
- Version code: `16`; minimum Android version: `8.0` (API 26).
- Built on 2026-11-02 from the Korean IME ordering and Shift/multitouch patches.
- This is a development APK signed with the existing Android debug certificate.
  It retains the signing identity used for the previous distributed APK.

The build fixes Korean composition preceding punctuation/Enter and includes the
Shift accessory immediately before the arrow keys, simultaneous modifier holds,
and tap-to-latch input.

## Verify and install

From this directory:

```sh
sha256sum -c SHA256SUMS
adb install -r hexgate-0.1.15-debug.apk
```

Signing certificate SHA-256:

```text
d4bbd8f3c4048b75a27da616b58ddec71eb178ac9027f2d942a9f8106fe9f06c
```

## Validation

The 2026-11-02 clean app build, all 9 unit tests, Android lint, APK ZIP integrity,
and APK signing verification passed. The same source patches passed 16 configured
Android emulator/SSH tests on 2026-10-29; that historical receipt is not a fresh
runtime test of this rebuilt APK. Physical Samsung IME behavior remains untested.

Build command (JDK 17 and Android SDK 36):

```sh
./gradlew :app:clean :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```
