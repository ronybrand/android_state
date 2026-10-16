#!/usr/bin/env bash
# Runs every Maestro flow against the installed app and the mock API. Meant to run while an
# Android emulator is up (see .github/workflows/e2e-android.yml), but it works against any
# adb-reachable device.
#
#   bash e2e/run-flows.sh [path/to/app-e2e.apk]
set -uo pipefail

APK="${1:-app/build/outputs/apk/e2e/app-e2e.apk}"
PORT=4010
REPORTS=e2e-reports
mkdir -p "$REPORTS"

node e2e/mock-api/server.js "$PORT" &
MOCK_PID=$!
trap 'kill "$MOCK_PID" 2>/dev/null || true' EXIT

# The app was built to call http://localhost:4010; on the device that is the host.
adb reverse "tcp:$PORT" "tcp:$PORT"
adb install -r "$APK"

# A CI emulator is slow: keep the system from putting "isn't responding" dialogs over the
# app, close any that is already up, and give the launcher a moment to settle.
adb shell settings put global hide_error_dialogs 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null || true
adb shell input keyevent KEYCODE_HOME
sleep 20

for _ in $(seq 1 20); do
  curl -fsS -o /dev/null "http://localhost:$PORT/actuator/info" && break
  sleep 0.5
done

failed=0
for flow in e2e/flows/[0-9]*.yaml; do
  name="$(basename "$flow" .yaml)"
  # Every flow starts from the seed data, whatever the previous one did to it.
  curl -fsS -X POST "http://localhost:$PORT/__reset" -o /dev/null
  echo "::group::$name"
  if ! maestro test "$flow" --format junit --output "$REPORTS/$name.xml"; then
    echo "::error::flow $name failed"
    failed=1
  fi
  echo "::endgroup::"
done

exit "$failed"
