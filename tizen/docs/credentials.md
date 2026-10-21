# Secure credential updates and recovery

Profiles store only random secure-repository aliases, never passwords, key data or passphrases.
`SecureCredentialStore` uses Tizen DataManager for save/get and the native key-manager removal
result to distinguish an already absent alias from access/storage failure. Missing aliases are
idempotent removals; every other removal error remains a pending cleanup operation.

## Commit protocol

`ProfileRepository` generates a new immutable alias for every replacement. Before calling the
vault, it writes that alias into `PendingRevocations` in the profile metadata. It flushes a full
temporary metadata file and atomically renames it over the committed file. The active profile
continues to reference its old credential during this prepare stage.

After secure save, one metadata commit switches the active alias, removes the new alias from
the cleanup journal, and adds the old alias to it. Only then does cleanup revoke the retired
alias. An interrupted or uncertain save therefore leaves either the original active credential
plus a new-alias cleanup intent, or the replacement active credential plus an old-alias intent.
Active aliases and pending aliases must be disjoint when loading metadata. Invalid journals
fail closed before attempting revocation. This is process-interruption recovery; no claim is
made about arbitrary filesystem or power-loss behavior beyond the target's rename/flush APIs.

Deletion atomically removes the profile and last-profile selection while journaling its alias.
It then revokes the alias. A failed metadata commit preserves the previous profile and secret.
A failed secure removal does not restore the deleted profile or forget its cleanup intent.

Cleanup retries after successful changes, at repository startup, and through **Settings → Retry
credential cleanup**. The UI reports pending cleanup explicitly. Committed saves/deletions do
not become misleading generic failures merely because retirement is delayed. Pending aliases
are bounded; new changes stop before exhausting the journal. Retrying an already removed alias
is safe if a cleanup metadata write failed. Empty credential edits retain the existing alias;
changing authentication methods requires a new credential and retires the previous method's
alias. Key passphrases are requested separately and are not persisted.

## Evidence and limits

Portable fault tests cover intent-write failure, profile-commit failure, uncertain secure save,
failed revocation/restart, empty edits with pending cleanup, method changes, deletion failure,
interrupted-save replay and an invalid journal targeting an active credential. Secrets are
asserted absent from metadata. These injected failures use a deterministic test vault; they
are not claims that the physical Tizen key manager was forced to fail in the same ways.

The installed signed emulator package separately passed native password save, blank-edit
retention, replacement, wrong-password failure, recovery, changed-host warning/rejection and
explicit approval, and profile deletion with no pending native revocation. The independent
loopback authentication fixture accepts test-only passwords/keys and a PTY but executes no host
shell. Actual shell/SFTP behavior is tested against the separate isolated OpenSSH fixture.
Neither fixture changes real account passwords or production sshd configuration.
