# Direct SSH connection and WSL lifetime

## Development emulator

The Windows emulator reaches Windows loopback through `10.0.2.2`. First establish that Windows can reach the selected WSL SSH listener; WSL-to-Windows loopback behavior varies with NAT/mirrored networking and firewall configuration. The project integration fixture uses a separate loopback-only sshd on port 22222. It does not alter the system ssh service.

Use a dedicated key and a dedicated Herdr session such as `hexgate-mobile`. The profile's Herdr session field starts/reattaches that named session. A blank field opens a regular shell or runs the optional startup command. Disconnect closes SSH, not the Herdr session server.

## Prepare the real PC (user-reviewed changes)

1. Identify the distribution with `wsl.exe -l -v`. Confirm the intended Ubuntu user and Herdr binary/version. Avoid changing an active development session.
2. If OpenSSH is absent, install/configure it in that distribution. For systemd-enabled Ubuntu, `sudo systemctl enable --now ssh` enables the listener; review the actual system state first. A WSL configuration change requiring distribution shutdown must wait for explicit approval and a suitable maintenance window.
3. Create a separate OpenSSH key pair for each Android device, install its public key in the intended user's `~/.ssh/authorized_keys`, and import the private key through the app's Android document picker. Prefer passphrase-protected private keys. The app encrypts its copy using a Keystore-protected AES key and excludes app backup.
4. Verify server fingerprints on Ubuntu with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256` (or the matching RSA/ECDSA host public key). Compare the exact SHA256 fingerprint with the app's trust dialog. A changed pin requires explicit replacement; rejection preserves the previous pin.
5. Review sshd configuration to permit key authentication and disable password/root login as appropriate. Validate with `sshd -t` before reloading. This project does not execute these changes automatically.

## LAN and Internet

- LAN: use a Windows/WSL address reachable from the phone. A NAT-mode WSL address is generally not directly reachable from another LAN device; use a Windows TCP port proxy to the WSL SSH listener where required.
- Internet: use a reachable public IP or DDNS name. Configure the router to forward the chosen public TCP port to the chosen Windows LAN address/port, then forward to WSL as needed. DDNS does not bypass CGNAT. If the ISP does not provide reachable inbound connectivity, obtain a public address or revisit the connection architecture.
- Reserve the Windows LAN address in DHCP. Review Windows/Hyper-V/Ubuntu firewall rules and IP Helper service availability. Bind the forwarded listener only to the intended Windows address. Opening a router/firewall port is a separate, explicit system change.
- Test LAN from a second device, then test Internet access from a genuinely different network. A local emulator does not prove Internet reachability or router hairpin behavior.

Microsoft reference: https://learn.microsoft.com/en-us/windows/wsl/networking

## WSL address changes

`scripts/windows/Refresh-WslSshForward.ps1` is a reviewable NAT-mode TCP forwarding helper. It is not run by the app. The caller must select the listener address, distribution and ports. Inspect `hostname -I` and confirm the selected address when Ubuntu has multiple interfaces; mirrored mode may require a different setup.

Example in elevated Windows PowerShell, after reviewing actual values:

```powershell
.\Refresh-WslSshForward.ps1 -Distribution Ubuntu -ListenAddress 127.0.0.1 -ListenPort 2222 -SshPort 22 -WhatIf
```

`127.0.0.1` is for Windows-hosted emulator/local access. For LAN/router forwarding, explicitly choose the Windows LAN address instead. Remove `-WhatIf` only after reviewing the displayed target. The helper updates only the selected TCP mapping; it does not create firewall rules or router mappings.

## Startup and uptime

Windows must be awake, and Ubuntu/OpenSSH must be running. Herdr persistence survives client disconnect; it does not keep processes alive through Windows/WSL shutdown.

After initial manual verification, a user-approved Task Scheduler setup can:

1. At Windows user logon, start the selected distribution and keep a foreground `wsl.exe -d Ubuntu -- sleep infinity` task running if needed for WSL lifetime. A systemd service alone should not be assumed to keep the distribution alive.
2. After that distribution is ready, run the reviewed forwarding helper with the approved address/ports and sufficient Windows permissions. Re-run it after a deliberate distribution restart that changes its NAT address.
3. Confirm the actual SSH service starts and the expected forwarded port is reachable. Log task success/failure without credentials.

Running before Windows login needs a separately reviewed Windows account/task configuration. Wake-on-LAN, automatic router setup and unattended credential provisioning are outside this first version. Never run `wsl --shutdown` to refresh networking while development work is active.
