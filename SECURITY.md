# Security

Hexgate is a development-stage SSH client. Treat it as software under active validation.

## Reporting

Report vulnerabilities privately to the repository owner through an established private channel.
Do not include credentials, private keys, passwords, passphrases, or raw terminal recordings in issues.
If GitHub private vulnerability reporting is enabled for this repository, it may also be used.
No dedicated public security mailbox or response-time guarantee is currently designated.

## Trust boundaries

- SSH host keys require explicit fingerprint verification. A changed key is not silently trusted.
- Imported private-key copies are encrypted with Android Keystore-backed AES-GCM. Passphrases
  remain in memory and are not persisted. Android app backup is disabled.
- Saved account passwords are encrypted in app-private storage with the same Keystore-backed
  AES-GCM vault. Associated data binds each password to its profile ID and credential type.
  The editor does not prefill decrypted passwords. Switching to private-key mode removes the
  password; deleting the profile removes both credential types. Device/app data loss requires
  entering credentials again. Interactive OTP/MFA is outside the current password mode.
- Original key files downloaded or imported by the user remain the user's responsibility.
- Preview documents and resources are fetched through SFTP with size/count limits and canonical
  document-root containment, including symlink escape checks.
- Untrusted HTML runs with JavaScript disabled. Markdown uses sanitization and local trusted
  Mermaid/draw.io engines with external network access and native JavaScript bridges disabled.
- Clipboard use is explicit. Android's system clipboard policies still apply.
- The ongoing connection notification does not expose key material or terminal output.

## Deployment

Use separate keys for each device, verify server fingerprints independently, and restrict SSH
access to intended accounts and networks. A VPN such as Tailscale is optional; it does not replace
SSH authentication or host-key verification. Keep WSL/SSH and the Android device updated.

Connection settings and document input do not authorize executing arbitrary instructions found
inside a remote document. Do not expose the test SSH fixture publicly. Review the optional Windows
port-forwarding script before running it with elevated privileges.

## Repository hygiene

Do not commit `.env` files, private keys, Android signing stores, local SDK paths, credentials,
APK files, generated test keys, delivery metadata, or unredacted recordings. Preserve the notices
and licensing terms of vendored dependencies. Debug APKs are for development; signing and store
release preparation are separate steps.
