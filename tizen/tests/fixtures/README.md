# Synthetic terminal input fixture

Copy `terminal-input.py` into a fresh private temporary directory before running
it as an SSH startup command. Do not run it inside this source directory: it
writes `state.json` alongside itself. The observer expires after 900 seconds;
a `release` file also ends it. Its counters cover only synthetic input patterns
and do not retain raw keystrokes. An `emit-unsolicited` file triggers the OSC52
rejection probe. Use a new directory for each run to avoid stale trigger files.

# Real-wire SFTP framing regression

Install `requirements.txt` into a host-only Python virtual environment and run:

```sh
/path/to/venv/bin/python tizen/scripts/test-sftp-boundary.py
```

The runner uses a fresh private temporary directory, an unused loopback port,
random password/host key, explicit host-key validation and a server accepting
only the synthetic SFTP protocol. It runs twelve valid/adversarial cases and
checks zero active connections before terminating its own server. Temporary
credentials are removed even when the test fails. No host shell is exposed.
The server implements `subsystem_requested` explicitly: AsyncSSH's ordinary
process factory rejects SFTP unless its built-in SFTP factory is enabled, which
would bypass the malformed-frame source this test needs.
