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

Status legend: [x] done and verified · [~] built, partly verified (note says what's missing) · [ ] not started.

### M0 — Setup
- [x] **0.1 Project skeleton.** Release APK permissions: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED only (`aapt dump permissions`).
- [x] **0.2 Fixture capture tooling.** `tools/capture.sh`. `uiautomator dump` can't run while mobile-mcp's agent is connected, so the debug build's service writes dumps (`am broadcast -a com.darryl.unscroll.DUMP`).

### M1 — Core engine
- [x] **1.1 Node abstraction + selectors.json loader.** Unit tests run every rule against captured fixtures.
- [x] **1.2 Service loop.** Throttled (80 ms) evaluate, 1 s watchdog while an app is in front; log line `pkg= screen= action=`.
- [x] **1.3 Curtain.** Verified on emulator: covers, swallows scrolls, steps aside on permission dialogs and app prompts, leaves with the app, never covers the keyboard, starts below the status bar.
- [~] **1.4 Exit action + single-item session.** Unit tested (identity + pager position + warm-up). Rotation not tested.
- [x] **1.5 Settings store + main UI.**

### M2 — Instagram
- [x] **2.1 Classifier.** Story viewer (`reel_viewer_root`) ≠ Reels viewer (`clips_viewer_view_pager`).
- [x] **2.2 Hide feed.** e2e: covered, scroll blocked, pull-to-refresh exposes no post, stories open.
- [x] **2.3 Hide home screen.**
- [x] **2.4 Hide Explore.** e2e: grid covered, typing not covered, keyword "For you" results covered below account rows, Accounts tab open.
- [~] **2.5 Block Reels tab (balanced).** e2e: Reels tab bounces to Home; profile Reel plays, next swipe closes. DM Reel: fixture-tested only (test thread lost its shared Reel).
- [~] **2.6 Block Reels entirely (strict).** e2e on profile Reels; DM path fixture-tested.
- [x] **2.7 Open in Messages.** e2e: lands in inbox; Home tapped afterwards is not redirected. Notification deep links not tested.

### M3 — YouTube
- [x] **3.1 Classifier.** Fixture-tested on 21.47.483 (top Home/Subs tabs, bottom pivot by position).
- [~] **3.2 Hide Home.** e2e covered. Subscriptions check blocked: YouTube's feed stopped loading on the emulator (network validated, app-side), so the Subs tab never appeared.
- [~] **3.3 Shorts balanced/strict.** Fixture-tested; Shorts tab bounce verified earlier. Re-run e2e once YouTube loads.
- [~] **3.4 Hide Up next.** Fixture-tested (cover starts below the comments teaser, panel open = no cover). e2e pending same YouTube issue.

### M4 — TikTok
- [x] **4.1 Classifier.** Structural (ids are obfuscated): 5-cell bottom bar, DetailActivity for single videos.
- [~] **4.2 Hide feeds.** e2e: Home covered, Inbox open. Single video from DM fixture-tested; next-swipe in a DM video didn't advance on the emulator, so exit-on-next is unit-tested only.

### M5 — X
- [~] **5.1 Classifier + Hide For You.** Built from labels ("For you" tab, structural bottom bar). Untested: X refuses sign-in on the emulator.

### M6 — LinkedIn
- [x] **6.1 Classifier + Hide feed.** e2e: feed covered, Jobs open, push prompt left alone.

### M7 — Lock
- [x] **7.1 Password lock.** Unit tests (PBKDF2 verify/salt). Emulator: switches disabled when locked, survives force-stop, wrong password rejected, right one unlocks.

### M8 — Hardening & release
- [~] **8.1 Never lock out.** Verified: permission dialogs, app prompts, keyboard, leaving the app. Not yet: share sheet, rotation, split screen, other language.
- [x] **8.2 Watchdog notification.** Posts on unbind, clears on reconnect, re-checks after boot/update.
- [ ] **8.3 Performance.** Scans are budgeted (400–700 nodes); timing not measured yet.
- [ ] **8.4 Second device profile.**
- [~] **8.5 Release build.** Minified release verified on emulator; signing via untracked `keystore.properties`. Install guide in README.
- [x] **8.6 Device admin.** `adb uninstall` fails while active; switching it off in unscroll removes it.
