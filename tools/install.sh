#!/usr/bin/env bash
# Build + install the debug APK and (re)enable the accessibility service.
# Reinstalling unbinds the service, and mobile-mcp's UiAutomation agent suppresses it.
# Toggling too soon after an update can leave it "bound" to a dead process, so wait
# for the service's own "service connected" log line and retry if it doesn't come.
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
cd "$(dirname "$0")/.."
./gradlew :app:installDebug -q 2>&1 | grep -v '^w:' || true
"$ADB" shell pkill -f mobilecli 2>/dev/null || true
sleep 2
for attempt in 1 2 3; do
  "$ADB" logcat -c
  "$ADB" shell settings put secure enabled_accessibility_services '""'
  sleep 1
  "$ADB" shell settings put secure enabled_accessibility_services com.darryl.unscroll/.service.FocusService
  "$ADB" shell settings put secure accessibility_enabled 1
  for _ in $(seq 1 20); do
    if "$ADB" logcat -d -s Unscroll | grep -q 'service connected'; then echo "service bound"; exit 0; fi
    sleep 0.5
  done
  echo "service didn't connect (attempt $attempt), retrying" >&2
  "$ADB" shell am force-stop com.darryl.unscroll
  sleep 2
done
echo "service did not bind" >&2; exit 1
