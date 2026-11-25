# Unscroll — Requirements & Plan

Supersedes the scope section of `Focus App — Build Prompt.md` (now 5 apps). Constraints from that doc still apply: no root/mods/private APIs, no `INTERNET`, no SDKs, never lock the user out, selectors live in `assets/selectors.json`, AwayDoomscrollin (GPL-3) is reference only — no copied code.

## Stack

Kotlin, Jetpack Compose, minSdk 26, one module, package `com.darryl.unscroll`. Settings in DataStore. One `FocusService : AccessibilityService`.

## Packages

| App | Package(s) |
| --- | --- |
| Instagram | `com.instagram.android` |
| YouTube | `com.google.android.youtube` |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` (regional build; the emulator has this one) |
| X | `com.twitter.android` |
| LinkedIn | `com.linkedin.android` |

## Switches

### Instagram
| Switch | Behavior |
| --- | --- |
| Hide feed | Curtain from stories tray bottom to bottom nav top. Pull-to-refresh never exposes a post. |
| Hide home screen | Curtain covers whole Home content (header + stories + feed); bottom nav stays usable. When on, "Hide feed" is greyed out (implied). |
| Hide Explore | Covers Explore grid and the suggested grid under search results. Search input and people results stay usable. |
| Block Reels tab | Balanced: Reels tab exits. One Reel opened from DMs, a profile or Saved plays; swiping to the next exits. |
| Block Reels entirely | Strict: every Reel viewer exits, including DM-shared. Stories/Highlights unaffected. |
| Open in Messages | On app launch landing on Home, taps the DM button. Verified with retry; if it fails, Home cover still applies. Manual Home taps are never redirected. |

Note: Instagram's internal IDs call stories "reels" (`reels_tray_container`, `reel_viewer`). The classifier must separate story viewer from clips (Reels) viewer.

### YouTube
| Switch | Behavior |
| --- | --- |
| Hide Home | Curtain over Home feed. Subscriptions, search, channels, playlists, You stay open. |
| Block Shorts tab | Balanced: Shorts tab exits. One Short from search/channel/link/Subscriptions plays; next swipe exits. |
| Block Shorts entirely | Strict: every Shorts player exits. |
| Hide Up next | Watch page (portrait): curtain over the scrollable suggestions list below the comments teaser. Comments sheet still opens. |

### TikTok
| Switch | Behavior |
| --- | --- |
| Hide feeds | Covers For You and Following. DMs, search, profile, inbox usable. One video opened from a DM or link plays; next swipe exits. |

### X
| Switch | Behavior |
| --- | --- |
| Hide For You | Curtain over timeline while For You tab is selected. User taps Following manually (no auto-switch). Following tab, search, lists, notifications, DMs untouched. |

### LinkedIn
| Switch | Behavior |
| --- | --- |
| Hide feed | Curtain over Home feed. Everything else open. |

### Lock
One password field + one button on the main screen. Unlocked: type password, tap Lock (sets the password each time) — stores salted PBKDF2 hash, all switches disabled. Locked: type same password, tap Unlock. Forgotten password = clear app data / reinstall (resets settings). Settings are persistent; no schedules or pause in v1.

### Cover design
Curtain shows "Hidden by unscroll" with app icon, themed light/dark, swallows touches, no animation lag (`setCanPlayMoveAnimation(false)` on 34+).

## Verification method

- **Unit tests** (`./gradlew test`): classifiers run against `uiautomator dump` XML fixtures in `app/src/test/resources/fixtures/<app>/<screen>/*.xml`, loaded into the same node interface the service uses. Pure logic (curtain bounds, session state machines, lock hashing, setting dependencies) tested directly.
- **Emulator tests** (mobile-mcp + adb on `emulator-5554`): install debug APK, enable service via `adb shell settings put secure enabled_accessibility_services com.darryl.unscroll/.FocusService`, drive the app, then assert with:
  - logcat tag `Unscroll` line `screen=<SCREEN> action=<ACTION>`
  - `adb shell dumpsys window windows` shows/does not show the curtain window
  - screenshot for visual check
- **Other phones**: selectors are ID/structure based (no UI text), geometry from node bounds. Re-run emulator checks on a second AVD profile (small phone + large/fold) before release.

Test account prerequisites: an Instagram DM containing a shared Reel, a Saved Reel; YouTube with subscriptions; a TikTok DM containing a video (all set up). X not available on emulator — verified on physical phone.

## To-dos

### M0 — Setup
- [ ] **0.1 Project skeleton.** Gradle, Compose, manifest without `INTERNET`. Verify: `./gradlew assembleDebug test` passes; `aapt dump permissions` shows no `INTERNET`; APK installs and launches on emulator.
- [ ] **0.2 Fixture capture tooling.** `tools/capture.sh <app> <screen>` saves dump + screenshot. If `uiautomator dump` times out on video screens, add a debug-only service command that dumps the tree to a file. Verify: fixtures captured for every screen in M2–M6, with app version in filename.

### M1 — Core engine
- [ ] **1.1 Node abstraction + selectors.json loader.** ID match first, content-desc fallback, per package/screen. Verify: unit tests on loader and matcher against fixtures.
- [ ] **1.2 Service loop.** Config for 6 packages, event types, debounce, classify → policy → action; debug screen line on settings screen. Verify: emulator — open each app, logcat shows package + `screen=UNKNOWN`; no events logged from other apps.
- [ ] **1.3 Curtain.** Accessibility overlay, bounds from anchors, last-top memory, step aside after ~600 ms unknown or on system dialog/permission prompt/keyboard-over-target. "Hidden by unscroll" design. Verify: unit tests for bounds math; emulator — overlay appears via a debug trigger, touches don't reach app underneath, disappears on a permission dialog.
- [ ] **1.4 Exit action + single-item session.** Back / tab click; generic balanced state machine (allow first item from a whitelisted origin, exit on next). Verify: unit tests covering origins, next-swipe, window recreation (rotation).
- [ ] **1.5 Settings store + main UI.** Per-app switch groups, dependency (home off greys feed). Verify: unit tests for dependencies; emulator — toggles persist across force-stop.

### M2 — Instagram
- [ ] **2.1 Classifier.** HOME, EXPLORE, SEARCH_RESULTS, REELS_TAB, REEL_VIEWER, STORY_VIEWER, DIRECT, PROFILE, CREATE, UNKNOWN. Verify: fixture tests, including story viewer ≠ reel viewer.
- [ ] **2.2 Hide feed.** Verify: emulator — stories visible, feed covered, scroll blocked, pull-to-refresh exposes nothing, stories open, DM/profile/create reachable.
- [ ] **2.3 Hide home screen.** Verify: whole Home covered, nav works, feed switch greyed.
- [ ] **2.4 Hide Explore.** Verify: grid covered; typing a username in search shows results and opens profile; suggested grid under keyword results covered.
- [ ] **2.5 Block Reels tab (balanced).** Verify: Reels tab exits; DM Reel plays, swipe exits; profile/Saved Reel plays once.
- [ ] **2.6 Block Reels entirely (strict).** Verify: DM Reel exits; stories and highlights still play.
- [ ] **2.7 Open in Messages.** Verify: force-stop + launch lands in DM inbox 10/10; tapping Home afterwards shows Home (with covers), no redirect; notification deep link not redirected.

### M3 — YouTube
- [ ] **3.1 Classifier.** HOME, SUBSCRIPTIONS, SEARCH, WATCH, SHORTS_TAB, SHORTS_PLAYER, CHANNEL, YOU, UNKNOWN. Verify: fixture tests.
- [ ] **3.2 Hide Home.** Verify: Home covered; Subscriptions, search, You open.
- [ ] **3.3 Shorts balanced/strict.** Verify: tab exits; Short from search plays once then exits on swipe (balanced); exits immediately (strict).
- [ ] **3.4 Hide Up next.** Verify: watch page suggestions covered; video controls, description, comments sheet work; fullscreen/miniplayer untouched.

### M4 — TikTok
- [ ] **4.1 Classifier** (both packages). FOR_YOU, FOLLOWING, VIDEO_SINGLE, INBOX, DM, PROFILE, SEARCH, UNKNOWN. Verify: fixture tests.
- [ ] **4.2 Hide feeds.** Verify: For You + Following covered; DM video plays, next swipe exits; search/profile usable.

### M5 — X
- [ ] **5.1 Classifier + Hide For You.** X can't be used on the emulator. Verify: fixture tests from dumps captured on the physical phone; manual check on phone — For You covered, Following uncovers, search/notifications/DMs usable.

### M6 — LinkedIn
- [ ] **6.1 Classifier + Hide feed.** Verify: Home feed covered; search, network, messaging, notifications, jobs usable.

### M7 — Lock
- [ ] **7.1 Password lock.** Verify: unit tests (hash/verify, wrong password); emulator — locked state disables all switches, survives force-stop/reboot, wrong password rejected, right one unlocks.

### M8 — Hardening & release
- [ ] **8.1 Never lock out.** Verify: system dialogs, share sheet, keyboard, rotation, split screen, another device language — no curtain where it shouldn't be.
- [ ] **8.2 Watchdog notification** when service stops. Verify: disable service via adb, notification appears.
- [ ] **8.3 Performance.** Event handling time logged; verify p95 < 16 ms on emulator, no full-tree walks on feeds/DM lists.
- [ ] **8.4 Second device profile.** Re-run M2–M6 emulator checks on a different AVD size.
- [ ] **8.5 Release build.** Signed APK, permission check, install guide (Allow restricted settings).
- [ ] **8.6 Device admin (last, optional).** Uninstall needs admin deactivated first. Verify: `adb uninstall` fails while admin active; deactivating then uninstalling works; app still usable without granting admin.
