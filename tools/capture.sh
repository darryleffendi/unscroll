#!/usr/bin/env bash
# Capture the current screen of the foreground app as a test fixture.
#   tools/capture.sh <app> <screen> [label]
# Writes app/src/test/resources/fixtures/<app>/<screen>/<version>[-label].xml (+ .png under captures/).
set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
app="$1"; screen="$2"; label="${3:-}"

case "$app" in
  instagram) pkg=com.instagram.android ;;
  youtube)   pkg=com.google.android.youtube ;;
  tiktok)    pkg=$("$ADB" shell pm list packages | grep -oE 'com\.zhiliaoapp\.musically|com\.ss\.android\.ugc\.trill' | head -1) ;;
  x)         pkg=com.twitter.android ;;
  linkedin)  pkg=com.linkedin.android ;;
  *) echo "unknown app: $app" >&2; exit 1 ;;
esac

version=$("$ADB" shell dumpsys package "$pkg" | grep -m1 versionName | sed 's/.*versionName=//' | tr -d '\r')
root="$(cd "$(dirname "$0")/.." && pwd)"
dir="$root/app/src/test/resources/fixtures/$app/$screen"
mkdir -p "$dir" "$root/captures/$app/$screen"
name="$version${label:+-$label}"

# uiautomator can't run while another automation client (mobile-mcp) holds the
# connection, so the debug build of unscroll's own service writes the dump.
remote="/sdcard/Android/data/com.darryl.unscroll/files/dumps/capture.xml"
"$ADB" shell rm -f "$remote"
"$ADB" shell am broadcast -a com.darryl.unscroll.DUMP --es name capture >/dev/null
for _ in 1 2 3 4 5 6 7 8 9 10; do
  "$ADB" shell test -s "$remote" && break
  sleep 0.5
done
"$ADB" exec-out cat "$remote" > "$dir/$name.xml"
"$ADB" exec-out screencap -p > "$root/captures/$app/$screen/$name.png"

if ! grep -q "package=\"$pkg\"" "$dir/$name.xml"; then
  echo "warning: dump does not contain $pkg (is the app in the foreground?)" >&2
fi
echo "$dir/$name.xml"
