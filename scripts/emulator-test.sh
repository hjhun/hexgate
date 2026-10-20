#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
hexgate_adb="${HEXGATE_ADB:-adb}"
hexgate_port="${HEXGATE_ADB_PORT:-5037}"
hexgate_serial="${HEXGATE_SERIAL:-emulator-5554}"
hexgate_path() { if [[ "$hexgate_adb" == *.exe ]]; then wslpath -w "$1"; else printf '%s\n' "$1"; fi; }
hexgate_run() { "$hexgate_adb" -P "$hexgate_port" -s "$hexgate_serial" "$@"; }
hexgate_run install -r "$(hexgate_path "$PWD/app/build/outputs/apk/debug/app-debug.apk")"
hexgate_run install -r "$(hexgate_path "$PWD/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")"
hexgate_cleanup() {
 hexgate_run shell rm -f /data/local/tmp/hexgate-test-key /data/local/tmp/hexgate-test-key-encrypted /data/local/tmp/hexgate-password-fixture.json >/dev/null 2>&1 || true
 rm -f docs/fixtures/outside.png
}
trap hexgate_cleanup EXIT
hexgate_network_faults=false
if [[ "$hexgate_serial" == emulator-* ]]; then hexgate_network_faults=true; fi
hexgate_args=(-e networkFaults "${HEXGATE_NETWORK_FAULTS:-$hexgate_network_faults}")
if [[ -f .tools/ssh-fixture/client ]]; then
 ln -sfn ../../.tools/ssh-fixture/host.pub docs/fixtures/outside.png
 hexgate_run push "$(hexgate_path "$PWD/.tools/ssh-fixture/client")" /data/local/tmp/hexgate-test-key
 if [[ ! -f .tools/ssh-fixture/client-encrypted ]]; then
  cp .tools/ssh-fixture/client .tools/ssh-fixture/client-encrypted
  ssh-keygen -q -p -P '' -N 'hexgate-fixture-only' -f .tools/ssh-fixture/client-encrypted >/dev/null
 fi
 hexgate_run push "$(hexgate_path "$PWD/.tools/ssh-fixture/client-encrypted")" /data/local/tmp/hexgate-test-key-encrypted
 hexgate_run shell chmod 644 /data/local/tmp/hexgate-test-key /data/local/tmp/hexgate-test-key-encrypted
 hexgate_fingerprint=$(ssh-keygen -lf .tools/ssh-fixture/host.pub | awk '{print $2}')
 hexgate_args+=( -e fixtureRoot "$PWD/docs/fixtures" -e sshFingerprint "$hexgate_fingerprint" -e sshHost "${HEXGATE_SSH_HOST:-10.0.2.2}" -e sshPort 22222 -e sshUser "$(id -un)")
fi
if [[ -f .tools/password-fixture/credentials.json ]]; then
 hexgate_run push "$(hexgate_path "$PWD/.tools/password-fixture/credentials.json")" /data/local/tmp/hexgate-password-fixture.json
 hexgate_run shell chmod 644 /data/local/tmp/hexgate-password-fixture.json
fi
mkdir -p artifacts
hexgate_run shell am instrument -w -e class "${HEXGATE_TEST_CLASS:-dev.hexgate.AppSshIntegrationTest,dev.hexgate.PreviewIntegrationTest,dev.hexgate.TerminalIntegrationTest}" -e traceHerdr "${HEXGATE_TRACE_HERDR:-false}" "${hexgate_args[@]}" dev.hexgate.test/androidx.test.runner.AndroidJUnitRunner | tee artifacts/instrumentation.txt
hexgate_run shell rm -f /data/local/tmp/hexgate-test-key /data/local/tmp/hexgate-test-key-encrypted
if ! rg -q '^OK \(' artifacts/instrumentation.txt; then exit 1; fi
