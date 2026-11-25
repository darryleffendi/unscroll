# Focus App — Build Prompt

Nov 24, 2026 · @Darryl

## Objective

Build a personal Android app in Kotlin that strips the addictive surfaces out of Instagram and YouTube, while keeping the parts I actually use. Think "Undistracted" Chrome extension, but for my phone.

| App | Keep | Hide |
| --- | --- | --- |
| Instagram | Stories, DMs, profiles, search, posting with all native features | Home feed, Explore, Reels |
| YouTube | Subscriptions feed, search, watching videos | Home recommendations, Shorts |

This is for my own phone only, sideloaded. It is not going to the Play Store.

## Constraints

Device safety comes first: nothing shady, nothing that touches my account credentials outside official surfaces.

- **No root, no Xposed/LSPosed.**
- **No modded or patched APKs** (no ReVanced, no Instagram mods). Both apps stay the official, unmodified Play Store builds.
- **No private or reverse-engineered APIs.**
- **No `INTERNET` permission anywhere in the app**, so it physically cannot send anything off the device.
- **Accessibility service scoped to two packages** via `packageNames`: `com.instagram.android` and `com.google.android.youtube`. It never sees events from other apps (banking, work apps).
- **Never lock me out:** an overlay must step aside on any screen it can't recognize, and on system dialogs or permission prompts.
- **No analytics, no third-party SDKs, no accounts.**
- Sideloaded; Android 13+ will need "Allow restricted settings" to enable the accessibility service.

## Architecture

One accessibility service watches the official Instagram and YouTube apps, classifies each screen, and covers, exits, redirects or allows it. Both apps stay native, so posting, music, stickers and every other feature keep working.

&#91;embedded content: architecture · 1 APK, 4 actions\]

Why not a WebView wrapper: Instagram's web version lacks story music, stickers and most editing tools. Why a curtain instead of a redirect for the Home feed: it's the only way to keep the stories tray while hiding the posts below it, which is the approach AwayDoomscrollin proved out.

## Build spec

One Gradle project, Kotlin, minSdk 26, one app module, one APK. The manifest declares no `INTERNET` permission.

### Screen policy

`FocusService : AccessibilityService` runs one loop per event: classify the screen from view IDs into an enum, look up its policy, apply it.

| Screen | Action |
| --- | --- |
| Instagram Home feed | **Cover:** curtain from the stories tray's bottom to the top of the bottom nav |
| Instagram Explore | **Cover** the grid below the search bar; search stays usable |
| Instagram Reels tab or Reels viewer | **Exit:** `GLOBAL_ACTION_BACK`, or click the Home tab |
| Instagram stories, DMs, profiles, posting | **Allow** |
| YouTube Home | **Redirect:** click the Subscriptions tab |
| YouTube Shorts tab or player | **Exit:** Back, or click Subscriptions |
| YouTube Subscriptions, search, watch page | **Allow** |
| Unrecognized screen | **Allow:** remove any curtain after \~600 ms |

### Service config

- `packageNames` = Instagram and YouTube only.
- Events: `typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled`; flags `flagReportViewIds|flagRetrieveInteractiveWindows`; `canRetrieveWindowContent="true"`.
- Keep each scan cheap: look up a few known IDs, debounce content-changed events, and never walk the DM thread list or long feeds.

### The curtain

1. A touchable `TYPE_ACCESSIBILITY_OVERLAY` window with `FLAG_NOT_FOCUSABLE`. It needs no `SYSTEM_ALERT_WINDOW`, and it swallows touches, so the feed can't scroll and the stories tray stays put.
2. **Top** = bottom of the stories tray (`reels_tray_container`, `tray_recycler_view`, `stories_tray`, `overlay_stories_tray_container`), capped at header bottom + tray height so pull-to-refresh never exposes a post. **Bottom** = top of the bottom nav.
3. The header and bottom nav stay uncovered, so DMs, my profile and the create button stay reachable.
4. Remember the last curtain top, so tapping Home covers instantly before Home has drawn.
5. **Step aside** when a full-screen window stays unrecognized for \~600 ms, or when a system dialog or permission prompt takes focus.
6. On Android 14+, call `setCanPlayMoveAnimation(false)` so the curtain edge never lags behind the content.

### Selectors as config

- `assets/selectors.json` holds, per app and per screen, the resource IDs plus content-description fallbacks.
- Match resource IDs first; fall back to content descriptions when Meta renames an ID.
- Seed it with AwayDoomscrollin's observed IDs, then verify against my installed versions with `adb shell uiautomator dump`. Leave clear TODO placeholders until then.
- When an app update breaks something, I edit config, not Kotlin.

### Settings screen

A toggle per rule, a button that opens Accessibility settings, and a debug line showing the currently classified screen, to diagnose breakage after an app update.

### Reference

[AwayDoomscrollin](https://github.com/resolvecommunity/AwayDoomscrollin) implements this same core design (screen classifier, curtain anchored under the stories tray, Back on Reels). It is GPL-3: read it for ideas and edge cases, but don't copy code.

## Scope and done criteria

**Out of scope for v1:** a WebView wrapper (the web version lacks story music, stickers and editing), network or request blocking, TikTok and other apps, timers and usage stats, a Play Store release.

**Done when:**

- [ ] Instagram opens with stories visible and no feed posts, and the feed can't be scrolled.
- [ ] Pull-to-refresh on Home never exposes a post.
- [ ] The Explore grid is covered, while people search still works.
- [ ] The Reels tab and any Reels viewer bounce back out.
- [ ] Stories, DMs, profiles and posting (including stories with music and stickers) all work.
- [ ] Opening YouTube lands on Subscriptions; tapping Home bounces back to Subscriptions.
- [ ] Shorts are unreachable from the nav bar.
- [ ] The APK has no `INTERNET` permission (verify with `aapt dump permissions`).
- [ ] The curtain never covers a system dialog, a permission prompt or an unrecognized full-screen page.
- [ ] Fixing a broken selector only needs an edit to `selectors.json`.

**v2, only if needed:** snap-back scrolling as a lighter fallback if the Home curtain proves flaky, catching Shorts that appear inside Subscriptions, and a strict mode that also covers profile grids.
