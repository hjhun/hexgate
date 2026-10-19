#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
hexgate_adb="${HEXGATE_ADB:-adb}"
hexgate_serial="${HEXGATE_SERIAL:-emulator-5554}"
hexgate_run() { "$hexgate_adb" -P "${HEXGATE_ADB_PORT:-5037}" -s "$hexgate_serial" "$@"; }
hexgate_cleanup() { hexgate_run shell rm -f /data/local/tmp/hexgate-test-key >/dev/null 2>&1 || true; }
trap hexgate_cleanup EXIT
hexgate_key="$PWD/.tools/ssh-fixture/client"
if [[ "$hexgate_adb" == *.exe ]]; then hexgate_key=$(wslpath -w "$hexgate_key"); fi
hexgate_run push "$hexgate_key" /data/local/tmp/hexgate-test-key
hexgate_run shell chmod 644 /data/local/tmp/hexgate-test-key
hexgate_fingerprint=$(ssh-keygen -lf .tools/ssh-fixture/host.pub | awk '{print $2}')
hexgate_args=(-e class dev.hexgate.ProcessRecoveryTest -e sshHost "${HEXGATE_SSH_HOST:-10.0.2.2}" -e sshUser "$(id -un)" -e sshFingerprint "$hexgate_fingerprint")
for hexgate_stage in seed verify; do
 if [[ "$hexgate_stage" == verify ]]; then hexgate_run shell am force-stop dev.hexgate; fi
 hexgate_run shell am instrument -w "${hexgate_args[@]}" -e recoveryStage "$hexgate_stage" dev.hexgate.test/androidx.test.runner.AndroidJUnitRunner | tee "artifacts/recovery-$hexgate_stage.txt"
 rg -q '^OK \(' "artifacts/recovery-$hexgate_stage.txt"
done
hexgate_run exec-out run-as dev.hexgate cat cache/process-recovery.png > artifacts/process-recovery.png
