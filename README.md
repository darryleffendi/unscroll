# unscroll

Hides feeds, Reels, Shorts and recommendations inside the official Instagram, YouTube,
TikTok, X and LinkedIn apps, using an Android accessibility service. No root, no modded
apps, no internet permission. Personal, sideloaded build.

## Install on a phone

1. Build: `./gradlew :app:assembleDebug` (or a signed release, below) and install the APK
   (`adb install -r app/build/outputs/apk/debug/app-debug.apk`).
2. Android 13+: **Settings › Apps › unscroll › ⋮ › Allow restricted settings**
   (confirm with PIN). Without this the accessibility switch is greyed out.
3. **Settings › Accessibility › unscroll protection** → on.
4. Open unscroll, allow notifications (used only for "Protection is not running").
5. Optional: lock the settings with a password, and turn on "Make uninstalling harder".
   Forgot the password? Clear the app's data or reinstall (settings reset).

### Signed release

Create `keystore.properties` in the project root (untracked):

```
storeFile=unscroll.jks
storePassword=...
keyAlias=unscroll
keyPassword=...
```

then `./gradlew :app:assembleRelease`. Without it the release APK is unsigned.

## When an app update breaks something

1. Open the broken screen on the phone/emulator with the debug build installed.
2. `tools/capture.sh <app> <screen> [label]` saves a dump into the test fixtures.
3. Fix ids in `app/src/main/assets/selectors.json`, add or adjust a test, run
   `./gradlew :app:testDebugUnitTest`.

The settings screen's "Now:" line shows what the service thinks the current screen is.

## Testing

- Unit: `./gradlew :app:testDebugUnitTest` — every rule runs against captured dumps.
- Emulator: `tools/install.sh && tools/e2e.py [instagram youtube tiktok linkedin]`.
  mobile-mcp's on-device agent suppresses accessibility services; the scripts stop it.
  Never swipe inside an Instagram DM thread in tests: it changes the chat's settings.
