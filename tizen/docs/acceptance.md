# Tizen NUI emulator acceptance

The remaining-goal audit passed on 2026-10-21: SFTP receive-boundary correction,
regression on the resulting signed TPK, and delivery/document reconciliation.
Physical-device verification is a separate follow-up. This is an emulator
development artifact, not device/store certification. The original port contract
is [the goal specification](../../docs/tizen-goal-prompt.md); historical port work
and earlier package observations remain in [validation](validation.md).

## Delivered artifact

- Branch: `tizen`; implementation under `tizen/`; Android remains unchanged.
- App and package ID: `org.tizen.hexgate`; label Hexgate; version0.1.0.
- TPK from repository root: `tizen/dist/org.tizen.hexgate-0.1.0.tpk`.
- SHA-256: `be86d3f3f51d02b065c4d0ac17bc7116785afd1519aca0cc45d8b32b1b524ff2`.
- Current target: emulator-26101, VM tizen-10-1-20261016, Tizen10.1 Unified x86_64,
  1920x1080; .NET8/NUI14. Earlier emulator-26111 records are historical.
- Both SDK emulator RSA signatures and70/71 reference digests verified; all69
  payload files are covered by author references. ZIP/IDs, App/Core/SSH DLL
  equality, packaged notices, and exact fixture-secret/cache/private-bundle
  exclusions passed. Emulator certificates do not establish production trust.

## Goal 1: SFTP receive boundary

The original fixture was rejected before the SFTP subsystem handshake. Replacing
its generic process factory with an explicit synthetic SFTP server session
completed version/realpath and reproduced2,424,836 bytes of reassembly for an
oversized declared length. This red result is recorded in
`.tools/sftp-boundary-red.log`.

SftpSession now validates each four-byte length before admitting body data,
including fragmented headers, and assembles only one frame at a time. A frame
including its header is at most1MiB (payload at most1,048,572 bytes). The backing
array grows from32KiB only up to1MiB. Consecutive legitimate frames have no
cumulative transfer limit. Invalid lengths fail immediately; incomplete valid
frames use operation timeout/cancellation. This is a reassembly/backing-array
bound, not a quota on decoded objects or all process memory. A server's single
larger directory/extension response is explicitly rejected; ordinary large files
spanning many frames remain supported.

Twelve real-wire cases pass: valid, split valid, maximum valid,64 consecutive
responses exceeding2MiB in total, oversized, split oversized, zero, negative,
just-over-limit, incomplete header, incomplete body and cancellation. Maximum
backing allocation measured1,048,576 bytes; incomplete requests timed out near
2s, cancellation near250ms, and malformed lengths failed before timeout.
Disposal joins requests; the disposable runner verifies0 active peers.

The SSH.NET adaptation is2026.0.0.2. Source checksums, complete patch replay from
the pinned upstream source and packaged notice pass:336 unchanged files and
seven documented modifications. Authentication/cryptography are unchanged.

## Goal 2: Same-package regression

Every current native row below uses the TPK checksum above. Host suites use the
same built Core/SSH code whose DLL equality with the TPK was checked. Historical
screenshots on earlier TPKs do not substitute for these current results.

| Area | Current evidence |
| --- | --- |
| Password authentication | Native save/use, blank-edit retention, replacement, wrong-password rejection and recovery; normal relaunch reconnects using the retained secure password. `.tools/sftp-boundary-password-native.log`. |
| Private keys | Native unencrypted key import/connection, separate encrypted-key unlock, cancellation without authentication, wrong passphrase before key authentication, correct passphrase connection. `sftp-boundary-encrypted-{native,resume}.log`, encrypted-results.json. |
| Host identity | Actual own SSH server key changed on the same address/port. Native warning showed its fingerprint; Reject produced0 password attempts and0 shells. `sftp-boundary-reject-host.log`, rotated-result.json. |
| Terminal input | Native rectangular copy/paste, unsolicited OSC52 rejection, remote Ctrl+C/V, Tab/Enter, input-dialog pwd commit and focus restoration; observer unknown bytes0. `sftp-boundary-input-{native.log,results.json}`. Input-dialog text used accessibility entry; physical IME composition is not claimed. |
| Output and resize | Actual native2MiB output reached its completion marker and survived font Smaller/Larger plus Refresh. Host8MiB exact ordered-byte receive, repeated saturation, separate-channel progress, full-window disposal and extended-output checks pass. |
| Herdr context | Native unique client automatically browsed its changed pane cwd. With two actual named clients, the explicit pane picker appeared; selecting the pane opened its Markdown. `sftp-boundary-context-{unique,multiple}-result.json`; multiple-client screenshot inspected. This run used one pane/two clients; older two-pane drag evidence remains historical. |
| Four preview formats | Native Markdown with embedded Mermaid/draw.io, static HTML, Mermaid and draw.io screenshots inspected. Light/Dark preserved the active preview. Corrected Markdown/HTML fixture copies include their relative image. |
| Document isolation | Native hostile HTML retained its green heading and allowed SVG; exactly3 SFTP opens (document/CSS/SVG),0 private opens and0 unexpected external HTTP requests. Emulator HTTP and independent pinned SFTP positive controls succeeded. `sftp-boundary-hostile-result.json`, private-control.log. Host canonical/symlink/tree/origin/size tests also pass. |
| Disconnect/reconnect | Repeated native password/key transitions and disconnect/reconnect, plus host cancellation, stale-callback and joined-retirement tests. |
| Pending-work shutdown | Actual96 request bytes queued at the SIGSTOPped exact own SFTP peer. Launcher list confirmed the app present before Close and absent afterward while the peer remained stopped; elapsed2.101s. The same peer was resumed in finally and reached active0. `sftp-boundary-stalled-result.json`, stalled-exit.json. |
| Cleanup | Own native profiles/secure credentials deleted with no pending revocation. Final normal Close removed the app from the launcher list. Own Herdr server/clients, private SSH listener, auth peers, HTTP probe, bootstrap and forward stopped. Own imported staging key files were overwritten with empty files through the SDK; generated host fixture secrets were removed after the final archive scan. No device permission change was needed. |

The first shutdown probe used `/proc` absence. This VM hides even live app
entries from its shell, so that receipt and its2.437s timing are excluded. The
launcher-list test above replaces it. Helper selector/focus/setup failures and
corrections are recorded in validation.md, without restarting live apps solely
because an observation timed out.

## Fresh host test totals

| Mode | Passed | Composition | Log under `.tools/` |
| --- | ---: | --- | --- |
| Portable |151|shared baseline|sftp-boundary-portable.log|
| Real OpenSSH/SFTP/receive/context |198|151 shared +47 integration|sftp-boundary-ssh.log|
| Independent authentication |161|151 shared +10 protocol|sftp-boundary-auth.log|
| Disposable SFTP framing runner |208|151 shared +56 framing +1 peer cleanup|sftp-boundary-runner.log|
| Python read-only Herdr helper |8 tests|separate Python suite|sftp-boundary-helper.log|

Do not add shared counts. Existing vendored VT compiler warnings and native
framework warnings remain. Bounded current PID log excerpts contain0 crash,
recursive or unhandled markers; this is not an unlimited-duration crash claim.

## Reproduction and delivery

From the repository root:

```sh
dotnet run --project tizen/tests/Hexgate.Tests
python3 tizen/tests/fixtures/test_herdr_context.py
python3 -m venv .tools/sftp-test-env
.tools/sftp-test-env/bin/pip install -r tizen/tests/fixtures/requirements.txt
.tools/sftp-test-env/bin/python tizen/scripts/test-sftp-boundary.py
python3 tizen/scripts/verify-sshnet-source.py
export PATH="$HOME/tizen-studio/tools/ide/bin:$PATH"
tizen/scripts/build-package.sh
(cd tizen/dist && sha256sum -c SHA256SUMS)
```

The SDK build runner compiles but reports no TPK, so the checked-in packaging
script uses the documented legacy staging/signing path. Full SDK install job
20261021T011311-c9f5d0 reported installed/launched/running. Rebuilding re-signs the
archive and may change its checksum; runtime evidence here belongs to the exact
checksum above. SDK background-job installation and independent authentication
fixture commands are in validation.md.

Screenshots under `/tmp/hexgate-tizen-evidence/`:

- `sftp-boundary-resource-markdown.png`, `sftp-boundary-resource-html.png`.
- `sftp-boundary-mermaid.png`, `sftp-boundary-drawio.png`.
- `sftp-boundary-hostile.png`, `sftp-boundary-context-multiple.png`.

Temporary logs/credentials/evidence remain ignored, outside the deliverable TPK.
No automatic commit, push or publication occurred. The original OpenSSH fixture
on22222 was preserved; new private peers/listeners were stopped. The emulator
remains available, with Hexgate installed and normally exited.

## Goal 4: Separate physical-device follow-up

When hardware is available, record the device model, OS, input devices and
per-case results for physical keyboard/right-click, Korean IME composition,
remote/D-pad navigation and resolutions, screen-off/background endurance,
network loss/recovery and an actual remote server, and device certificates.
Store publishing requires a separate request.

The emulator's OS back/home bubble remains after fullscreen requests. RGB is
quantized to the256-color palette. No unlimited background execution,
process-death survival, universal VT/browser conformance, public-network success
or physical-device certification is claimed. Automatic Herdr context needs
Linux `/proc`, Python3 and the supported read-only snapshot API; unsupported
identities use explicit files and ambiguous clients require pane selection.
