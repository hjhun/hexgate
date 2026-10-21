# Hexgate

Read docs/design.md and docs/progress.md before implementation. Keep progress and verification evidence current.

- Kotlin Android app; Compose shell, native terminal View, SSH transport, isolated document preview.
- Reuse mature engines; keep transport, terminal rendering and remote files independently testable.
- Bound buffers and remote reads. Keep credentials and terminal content out of logs.
- Trust host keys explicitly. Encrypt private keys with a Keystore-protected key.
- Use isolated Herdr test sessions. Preserve existing developer processes and configuration.
- Build and test locally; commits, pushes and publication require an explicit user request.
- Record emulator results separately from physical-device and public-network results.

## Tizen branch

- On `tizen`, implement the approved C# NUI port under `tizen/`; preserve Android as the behavior reference.
- Read docs/tizen-goal-prompt.md and docs/tizen-port-analysis.md before port work. Track actual results in docs/progress.md.
- Both Tizen package and application IDs are `org.tizen.hexgate`.
- Reference tizen-action-examples read-only. Do not add Action providers without a request.
- Keep networking off the NUI UI thread; use Tizen secure storage, not Android APIs or plaintext fallback.
- Local emulator build/sign/install/test is authorized. Do not commit, push or publish automatically.
