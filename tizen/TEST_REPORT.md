# Tizen port verification status

Date: 2026-10-21. Remaining-goal emulator acceptance passed.
The authoritative requirement audit is [acceptance](docs/acceptance.md); detailed
commands, failures and package-specific observations are in [validation](docs/validation.md).
The completion contract remains `../docs/tizen-goal-prompt.md`.

## Current artifact

- `tizen/dist/org.tizen.hexgate-0.1.0.tpk` (path from repository root).
- SHA-256: `be86d3f3f51d02b065c4d0ac17bc7116785afd1519aca0cc45d8b32b1b524ff2`.
- Current target: emulator-26101, VM tizen-10-1-20261016, Tizen10.1 Unified x86_64,1920x1080.
- .NET8/NUI14; app and package ID both org.tizen.hexgate; SDK emulator-only signer.
- Author/distributor RSA signatures and70/71 digests passed. Every69 payload file
  is covered by author references. App/Core/SSH DLLs equal the archive; ZIP, IDs,
  licenses, cache/private-bundle and exact own fixture-secret exclusions passed.

## Fresh host results

| Mode | Passed | Shared count | Evidence |
| --- | ---: | ---: | --- |
| Portable |151|151|.tools/sftp-boundary-portable.log|
| Real OpenSSH/SFTP/receive/context |198|151|.tools/sftp-boundary-ssh.log|
| Authentication |161|151|.tools/sftp-boundary-auth.log|
| Real-wire SFTP runner |208|151|.tools/sftp-boundary-runner.log|
| Python Herdr helper |8 tests|0|.tools/sftp-boundary-helper.log|

Do not sum shared assertions. The SFTP runner has56 framing assertions plus one
fixture cleanup assertion. Its12 cases cover valid/split/maximum/consecutive
responses, oversized/split/zero/negative/just-over-limit headers, incomplete
header/body timeouts and cancellation. Actual backing allocation stays within
1MiB. Before the fix the real malformed length accumulated2,424,836 bytes.
SSH.NET provenance verifies336 unchanged files and seven documented modifications.
Existing vendored VT compiler warnings remain; they are not hidden as clean-build proof.

## Current native results and completion audit

The installed candidate rendered Markdown (embedded Mermaid/draw.io), static
HTML, Mermaid and draw.io. Light/Dark changes retained the active preview.
Encrypted-key cancellation, wrong passphrase and correct passphrase passed,
with independent server counters. Hostile HTML opened only its document, CSS
and allowed SVG; native private-file opens and unexpected HTTP requests were0.
Pinned independent SFTP and emulator HTTP positive controls succeeded.

Final native password retention/replacement/rejection/recovery, real host-key
rotation rejection, rectangular input/clipboard,2MiB output, font/refresh,
unique/multiple-client Herdr context and cleanup also passed. Pending SFTP
shutdown used an authoritative launcher before/after check while96 bytes were
queued at a stopped own peer; exit took2.101s. The earlier /proc-based absence
receipt was invalidated and is not credited. Earlier package observations remain
historical; acceptance.md contains the current requirement-by-requirement audit.

## Reproduction and boundaries

```sh
dotnet run --project tizen/tests/Hexgate.Tests
python3 tizen/tests/fixtures/test_herdr_context.py
/path/to/pinned-venv/bin/python tizen/scripts/test-sftp-boundary.py
export PATH="$HOME/tizen-studio/tools/ide/bin:$PATH"
tizen/scripts/build-package.sh
(cd tizen/dist && sha256sum -c SHA256SUMS)
```

Install the pinned virtual-environment dependencies from
`tests/fixtures/requirements.txt`. The validation record describes real OpenSSH
and auth fixtures and the SDK background install runner. The documented legacy
staging path is required because the SDK build runner compiles but emits no TPK.

Physical keyboard/right-click, Korean IME composition, Samsung hardware,
screen-off endurance and public-network validation remain separate follow-up.
No process-death survival, unlimited background operation, full VT conformance
or device/store certificate readiness is claimed. RGB maps to the256-color
palette. Existing system fullscreen controls remain visible on the emulator.
