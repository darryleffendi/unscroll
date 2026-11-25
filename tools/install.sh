#!/usr/bin/env bash
# Build + install the debug APK and (re)enable the accessibility service.
# Reinstalling unbinds the service, and mobile-mcp's UiAutomation agent suppresses it.
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
cd "$(dirname "$0")/.."
./gradlew :app:installDebug -q 2>&1 | grep -v '^w:' || true
"$ADB" shell pkill -f mobilecli 2>/dev/null || true
"$ADB" shell settings put secure enabled_accessibility_services '""'
"$ADB" shell settings put secure enabled_accessibility_services com.darryl.unscroll/.service.FocusService
"$ADB" shell settings put secure accessibility_enabled 1
for _ in $(seq 1 20); do
  if "$ADB" shell dumpsys accessibility | grep 'Bound services' | grep -q 'unscroll'; then echo "service bound"; exit 0; fi
  sleep 0.5
done
echo "service did not bind" >&2; exit 1
