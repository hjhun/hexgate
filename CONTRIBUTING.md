# Contributing

This is a private development repository. Coordinate changes through repository issues and
pull requests, and follow [AGENTS.md](AGENTS.md) when using coding agents.

## Development environment

- JDK 17 and Android SDK Platform 36 / Build Tools 36.0.0.
- Android 8.0 / API 26 is the minimum application API; the target API is 36.
- Node.js with npm is needed only to rebuild renderer JavaScript.
- Linux OpenSSH server, Python 3, `rg`, and an emulator are needed for the full integration fixture.
- Herdr must be available on the test SSH account for Herdr-specific cases. Development used 0.9.4.

Use the checked-in Gradle wrapper. Keep SDK paths, IDE state, keys, and generated files out of Git.
The build helper honors `HEXGATE_JAVA_HOME` when a separate JDK is needed:

```sh
export HEXGATE_JAVA_HOME=/path/to/jdk-17
scripts/build.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 -m unittest discover -s scripts -p 'test_*.py'
```

## Integration tests

Use a dedicated test account and disposable emulator. Tests intentionally change emulator
network, keyboard, clipboard, fold posture, and battery-consent state. Review test scope before
using a physical device. Never target an existing development Herdr session.

In one terminal, start the isolated loopback fixture:

```sh
scripts/start-ssh-fixture.sh --foreground
```

The fixture creates ignored test keys under `.tools/ssh-fixture/` and listens on
`127.0.0.1:22222`. It does not reconfigure the system SSH service. The fixture's relaxed
filesystem checks are for local tests only.

In another terminal:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
export HEXGATE_ADB=adb
export HEXGATE_ADB_PORT=5037
export HEXGATE_SERIAL=emulator-5554
export HEXGATE_SSH_HOST=10.0.2.2
scripts/emulator-test.sh
scripts/stop-ssh-fixture.sh
```

The emulator's host-loopback route must reach the fixture. With a Windows emulator and WSL,
verify Windows-to-WSL loopback forwarding separately. You can select a Windows `adb.exe` via
`HEXGATE_ADB` and use a separate ADB server port via `HEXGATE_ADB_PORT`; do not mix unrelated
Linux and Windows ADB servers.

Select a focused suite with `HEXGATE_TEST_CLASS`:

```sh
HEXGATE_TEST_CLASS=dev.hexgate.RectangularSelectionTest,dev.hexgate.ContextCopyTest \
  scripts/emulator-test.sh
```

Runner output is stored in ignored `artifacts/`. Review actual test counts and failures;
a successful install alone is not a passing test. The two-stage process recovery runner
requires the app and test APKs to be installed first:

```sh
scripts/emulator-recovery-test.sh
```

See [validation.md](docs/validation.md) for historical evidence and remaining hardware coverage.

## Change guidelines

- Keep SSH transport, native terminal rendering, and remote document access independently testable.
- Preserve terminal control-key semantics and modifier behavior.
- Bound input queues, remote reads, clipboard input, and document resources.
- Keep private keys, passphrases, and terminal contents out of logs.
- Preserve explicit host trust and the separation between static HTML and trusted local renderers.
- Write project documentation, commit messages, and pull request descriptions in English.
- Keep multilingual text in test fixtures when it exercises Unicode rendering.
- Update dependency provenance and packaged notices when changing bundled dependencies.
- Run checks appropriate to the change and report emulator versus physical-device results separately.

APK files, test recordings, local credentials, signing keys, caches, and private delivery links
must not be committed. Build release artifacts separately after choosing a signing policy.
