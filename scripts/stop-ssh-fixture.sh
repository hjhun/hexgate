#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
hexgate_fixture="$PWD/.tools/ssh-fixture"
if [[ ! -f "$hexgate_fixture/sshd.pid" ]]; then echo 'No fixture PID file.';exit 0;fi
hexgate_pid=$(cat "$hexgate_fixture/sshd.pid")
if [[ ! "$hexgate_pid" =~ ^[0-9]+$ ]]; then echo 'Invalid fixture PID; nothing stopped.' >&2;exit 1;fi
if [[ ! -r "/proc/$hexgate_pid/cmdline" ]]; then echo 'Recorded fixture process is no longer running.';exit 0;fi
if [[ "$(stat -c %u "/proc/$hexgate_pid")" != "$(id -u)" ]] || ! tr '\0' ' ' < "/proc/$hexgate_pid/cmdline" | rg -Fq "$hexgate_fixture/sshd_config"; then
 echo 'PID does not identify this user-owned fixture; nothing stopped.' >&2;exit 1
fi
kill -TERM "$hexgate_pid"
echo 'Stopped only the Hexgate test listener. Existing developer SSH/Herdr sessions were not targeted.'
