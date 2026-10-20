# SSH password authentication

Status: approved by the user, including encrypted password retention and a settings input field.

## Behavior and boundaries

Connection profiles explicitly choose Private key or Password. Legacy profiles default to
Private key. Password profiles expose a masked SSH password field in the connection editor,
require no private key import, and use the stored password for initial connection and reconnect.
A blank field when editing retains the saved password; a new value replaces it. Empty new
password profiles cannot be saved. Switching back to key authentication removes the stored
password; deleting the profile removes both kinds of credentials.

Passwords are encrypted with AES-GCM and the existing non-exportable Android Keystore key.
Authenticated associated data binds each password ciphertext to its profile and credential type.
Plain profile JSON contains only the authentication method, never the password. Password input
is ephemeral UI state, not saved instance state. The editor never decrypts a saved password to
populate the field. App backups remain disabled. There is no biometric prompt requirement.

SSHJ performs the selected authentication method only, after existing explicit server-host-key
verification. Password authentication does not silently fall back to private keys. Connection
failure permits correction in the editor and retry; encrypted key passphrases remain separate
and memory-only. Server configuration is unchanged. OTP/MFA and arbitrary interactive
keyboard challenges remain outside this version.

## Implementation and verification plan

1. Extend profile serialization with a backward-compatible authentication method and reuse the
   Keystore vault for separately named password records.
2. Branch SSHJ authentication and saved-profile connection setup by the chosen method.
3. Add the selector and masked field to the connection editor; remove the misleading global
   key-passphrase field from the connection list. Encrypted keys keep their existing prompt.
4. Test migration, ciphertext retention/replacement/deletion, UI editing, password login,
   invalid passwords, host rejection, reconnect, and private-key regression.
5. Build the next debug APK, audit its signature/assets/secret exclusion, and deliver privately
   through the previously authorized Drive and Gmail flow.

Password tests use a random per-run credential and a dedicated loopback SSH server; no host
account passwords, real developer sessions, system sshd settings, or router settings change.
Emulator evidence and physical-device verification are reported separately.
