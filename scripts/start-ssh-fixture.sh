#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
hexgate_root="$PWD"
hexgate_fixture="$hexgate_root/.tools/ssh-fixture"
mkdir -p "$hexgate_fixture"
chmod 700 "$hexgate_fixture"
if [[ -f "$hexgate_fixture/sshd.pid" ]]; then
 hexgate_pid=$(cat "$hexgate_fixture/sshd.pid")
 if [[ "$hexgate_pid" =~ ^[0-9]+$ ]] && [[ -r "/proc/$hexgate_pid/cmdline" ]] && tr '\0' ' ' < "/proc/$hexgate_pid/cmdline" | rg -Fq "$hexgate_fixture/sshd_config"; then
  echo "Hexgate loopback SSH fixture is already running on port 22222."
  exit 0
 fi
fi
if ss -ltnH 'sport = :22222' | rg -q .; then echo 'Port 22222 is in use; no listener was changed.' >&2; exit 1; fi
for hexgate_key in host client; do
 if [[ ! -f "$hexgate_fixture/$hexgate_key" ]]; then ssh-keygen -q -t ed25519 -N '' -f "$hexgate_fixture/$hexgate_key"; fi
done
cp "$hexgate_fixture/client.pub" "$hexgate_fixture/authorized_keys"
chmod 600 "$hexgate_fixture/authorized_keys"
cat > "$hexgate_fixture/sshd_config" <<CONFIG
Port 22222
ListenAddress 127.0.0.1
HostKey "$hexgate_fixture/host"
PidFile "$hexgate_fixture/sshd.pid"
AuthorizedKeysFile "$hexgate_fixture/authorized_keys"
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
PermitRootLogin no
UsePAM no
StrictModes no
AllowUsers $(id -un)
Subsystem sftp internal-sftp
CONFIG
/usr/sbin/sshd -t -f "$hexgate_fixture/sshd_config"
if [[ "${1:-}" == --foreground ]]; then
 echo 'Running only the Hexgate loopback fixture in the foreground; stop with Ctrl-C.'
 exec /usr/sbin/sshd -D -e -f "$hexgate_fixture/sshd_config"
fi
nohup /usr/sbin/sshd -D -e -f "$hexgate_fixture/sshd_config" > "$hexgate_fixture/server.log" 2>&1 < /dev/null &
hexgate_pid=$!
for hexgate_attempt in {1..30}; do
 if ! kill -0 "$hexgate_pid" 2>/dev/null; then echo "Fixture failed; inspect .tools/ssh-fixture/server.log" >&2;exit 1;fi
 if ss -ltnH 'sport = :22222' | rg -q .; then echo "Hexgate loopback SSH fixture started (PID $hexgate_pid).";exit 0;fi
 sleep .1
done
echo 'Fixture startup not yet confirmed; inspect this PID before retrying.' >&2
exit 1
