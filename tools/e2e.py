#!/usr/bin/env python3
"""End-to-end checks on the emulator. Needs the debug build installed (tools/install.sh)
and mobile-mcp's agent stopped (it suppresses accessibility services).

  tools/e2e.py [instagram|youtube|tiktok|linkedin|lock ...]
"""
import os, re, subprocess, sys, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
results = []


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def ui(*args):
    return subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools/ui.py"), *args], capture_output=True, text=True)


def tap(query):
    r = ui("tap", query)
    if r.returncode:
        print("   (tap failed:", query + ")")
    return r.returncode == 0


def swipe(x1, y1, x2, y2, ms=200):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def setting(key, on):
    adb("shell", "am", "broadcast", "-a", "com.darryl.unscroll.SET", "--es", "key", key, "--ez", "on", "true" if on else "false")
    time.sleep(0.6)


def launch(pkg, fresh=True):
    # Leave to the launcher first, as a real "open the app" would.
    adb("shell", "input", "keyevent", "HOME")
    time.sleep(1.5)
    if fresh:
        adb("shell", "am", "force-stop", pkg)
        time.sleep(0.5)
    adb("shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")


def mark():
    adb("logcat", "-c")


def lines():
    return [l.split("Unscroll: ", 1)[1] for l in adb("logcat", "-d", "-s", "Unscroll").splitlines() if "pkg=" in l]


def last():
    ls = lines()
    return ls[-1] if ls else ""


def wait_line(pattern, timeout=8):
    """Waits for a log line (since the last mark) matching pattern."""
    end = time.time() + timeout
    while time.time() < end:
        for l in lines():
            if re.search(pattern, l):
                return l
        time.sleep(0.4)
    return None


def curtain_now():
    return "unscroll curtain" in adb("shell", "dumpsys", "window", "windows")


def curtain(expected=True, timeout=3):
    """Polls briefly: window add/remove lags the log line. Returns the final state."""
    end = time.time() + timeout
    while True:
        state = curtain_now()
        if state == expected or time.time() > end:
            return state
        time.sleep(0.3)


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail and not ok else ""))


def swipe_next(pattern):
    """Swipes to the next full-screen item; a swipe right after opening sometimes doesn't take."""
    for _ in range(2):
        swipe(540, 1900, 540, 300, 120)
        if wait_line(pattern, 4):
            return True
    return False


def settle(s=2.0):
    time.sleep(s)


def instagram():
    pkg = "com.instagram.android"
    for k, v in [("ig_hide_feed", True), ("ig_hide_home", False), ("ig_hide_explore", True),
                 ("ig_block_reels_tab", True), ("ig_block_reels_all", False), ("ig_open_messages", False)]:
        setting(k, v)
    mark(); launch(pkg)
    l = wait_line(r"screen=IG_HOME action=COVER")
    check("IG opens on Home with the feed covered", l is not None and curtain(), last())
    top = int(re.search(r"COVER\[(\d+)", l).group(1)) if l else 0

    swipe(540, 1500, 540, 600); settle()
    check("feed doesn't scroll under the curtain", ui("has", "reels_tray_container").returncode == 0)
    swipe(540, 450, 540, 1500, 600); time.sleep(0.4)
    l = last()
    m = re.search(r"COVER\[(\d+)", l)
    cover_top = int(m.group(1)) if m else 0
    posts_above = [ln for ln in ui("ids").stdout.splitlines()
                   if ln.startswith("row_feed") and int(re.findall(r"\d+", ln.split()[1])[1]) < cover_top]
    check("pull-to-refresh keeps posts covered", m is not None and not posts_above, f"{l} {posts_above[:1]}")
    settle(2)

    mark(); tap("search_tab")
    check("Explore grid is covered", wait_line(r"screen=IG_EXPLORE action=COVER") is not None, last())
    tap("action_bar_search_edit_text"); settle(1.5)
    adb("shell", "input", "text", "nasa"); settle(2.5)
    check("typing a search is not covered", "IG_SEARCH " in last() and "ALLOW" in last() and not curtain(False), last())
    mark(); adb("shell", "input", "keyevent", "ENTER")
    check("keyword results: suggested grid covered", wait_line(r"screen=IG_SEARCH_RESULTS action=COVER") is not None, last())
    ui("tapchild", "scrollable_tab_layout", "0")
    out = ui("ids").stdout
    m = re.search(r"scrollable_tab_layout", out)
    # second results tab (Accounts): tap by position
    adb("shell", "input", "tap", "326", "337"); settle(2.5)
    check("Accounts results tab is not covered", "IG_SEARCH_RESULTS action=ALLOW" in last(), last())
    adb("shell", "input", "keyevent", "BACK"); settle(1); adb("shell", "input", "keyevent", "BACK"); settle(1)

    mark(); tap("clips_tab")
    l = wait_line(r"screen=IG_REELS_TAB action=TAP")
    settle(2)
    check("Reels tab bounces back to Home", l is not None and "IG_HOME" in last(), last())

    mark(); tap("feed_tab"); settle(2)
    adb("shell", "input", "tap", "410", "400")
    check("stories open and play", wait_line(r"screen=IG_STORY action=ALLOW") is not None, last())
    adb("shell", "input", "keyevent", "BACK"); settle(1.5)

    # A Reel opened from a profile's Reels tab (no chats are touched: a stray swipe
    # inside a DM thread changes its settings).
    def open_profile_reel():
        launch(pkg); settle(4)
        tap("search_tab"); settle(2)
        tap("action_bar_search_edit_text"); settle(1.5)
        adb("shell", "input", "text", "nasa"); settle(3)
        tap("row_search_user_username"); settle(4)
        tap("profile_tab_icon_view#1"); settle(3)  # the profile's Reels tab
        return tap("preview_clip_thumbnail")

    mark()
    opened = open_profile_reel() and wait_line(r"screen=IG_REEL_VIEWER action=ALLOW") is not None
    check("a Reel from a profile plays", opened, last())
    if opened:
        settle(2)
        mark()
        check("swiping to the next Reel closes it", swipe_next(r"screen=IG_REEL_VIEWER action=BACK"), last())
        settle(2)
        check("...back on the profile", ui("has", "profile_tabs_container").returncode == 0)
        setting("ig_block_reels_all", True)
        mark(); tap("preview_clip_thumbnail")
        check("strict: a profile Reel is closed", wait_line(r"screen=IG_REEL_VIEWER action=BACK") is not None, last())
        settle(2)
    setting("ig_block_reels_all", False)
    launch(pkg); settle(4)

    mark(); setting("ig_hide_home", True); tap("feed_tab")
    l = wait_line(r"screen=IG_HOME action=COVER\[(1[0-9]{2}|2[0-9]{2}),")  # starts below the status bar
    check("hide home screen covers header and stories", l is not None, last())
    setting("ig_hide_home", False)

    setting("ig_open_messages", True)
    mark(); launch(pkg)
    l = wait_line(r"TAP\(open Messages\)")
    settle(2.5)
    check("Open in Messages lands in the inbox", l is not None and ui("has", "direct_inbox_action_bar").returncode == 0, last())
    mark(); tap("feed_tab"); settle(2.5)
    check("tapping Home afterwards is not redirected", "IG_HOME action=COVER" in last(), last())
    setting("ig_open_messages", False)

    mark(); adb("shell", "input", "keyevent", "HOME"); settle(2)
    check("curtain leaves with the app", not curtain(False), last())


def youtube():
    pkg = "com.google.android.youtube"
    for k, v in [("yt_hide_home", True), ("yt_block_shorts_tab", True), ("yt_block_shorts_all", False), ("yt_hide_up_next", True)]:
        setting(k, v)
    mark(); launch(pkg)
    check("YT Home is covered", wait_line(r"screen=YT_HOME action=COVER", 12) is not None and curtain(), last())
    mark(); tap("desc=Subs")
    check("Subscriptions open", wait_line(r"screen=YT_SUBSCRIPTIONS action=ALLOW") is not None and not curtain(False), last())
    mark(); adb("shell", "input", "tap", "405", "2274")
    l = wait_line(r"screen=YT_SHORTS_TAB action=TAP")
    settle(2)
    check("Shorts tab bounces to Home", l is not None and "YT_HOME" in last(), last())
    tap("desc=Search"); settle(1.5)
    adb("shell", "input", "text", "veritasium%sshorts"); adb("shell", "input", "keyevent", "ENTER"); settle(5)
    check("search results open", "YT_SEARCH action=ALLOW" in last(), last())
    mark()
    if tap("desc=play Short"):
        check("a Short from search plays", wait_line(r"screen=YT_SHORTS_PLAYER action=ALLOW") is not None, last())
        settle(2.5)
        mark()
        check("swiping to the next Short closes it", swipe_next(r"screen=YT_SHORTS_PLAYER action=BACK"), last())
        settle(2)
        setting("yt_block_shorts_all", True)
        mark(); tap("desc=play Short")
        check("strict: any Short is closed", wait_line(r"screen=YT_SHORTS_PLAYER action=BACK") is not None, last())
        setting("yt_block_shorts_all", False)
        settle(2)
    else:
        check("found a Short in search results", False)
    mark(); tap("desc=Clear"); settle(1)
    adb("shell", "input", "text", "veritasium"); adb("shell", "input", "keyevent", "ENTER"); settle(5)
    adb("shell", "input", "tap", "500", "1080")
    l = wait_line(r"screen=YT_WATCH action=COVER", 15)
    check("watch page: Up next covered", l is not None, last())
    mark(); adb("shell", "input", "keyevent", "HOME"); settle(2)
    check("curtain leaves with the app", not curtain(False), last())


def tiktok():
    pkg = next((p for p in ("com.ss.android.ugc.trill", "com.zhiliaoapp.musically") if p in adb("shell", "pm", "list", "packages")), None)
    setting("tt_hide_feeds", True)
    mark(); launch(pkg)
    settle(6)
    adb("shell", "input", "tap", "108", "2270")
    check("TikTok Home feed is covered", wait_line(r"screen=TT_FEED action=COVER", 10) is not None and curtain(), last())
    mark(); adb("shell", "input", "tap", "756", "2270")
    check("Inbox is open", wait_line(r"screen=TT_TAB action=ALLOW") is not None and not curtain(False), last())
    mark(); adb("shell", "input", "keyevent", "HOME"); settle(2)
    check("curtain leaves with the app", not curtain(False), last())


def linkedin():
    pkg = "com.linkedin.android"
    setting("li_hide_feed", True)
    mark(); launch(pkg)
    check("LinkedIn feed is covered", wait_line(r"screen=LI_FEED action=COVER", 12) is not None and curtain(), last())
    mark(); tap("tab_jobs")
    check("Jobs tab is open", wait_line(r"screen=LI_TAB action=ALLOW") is not None and not curtain(False), last())
    tap("tab_feed"); settle(1.5)
    mark(); adb("shell", "input", "keyevent", "HOME"); settle(2)
    check("curtain leaves with the app", not curtain(False), last())


SUITES = {"instagram": instagram, "youtube": youtube, "tiktok": tiktok, "linkedin": linkedin}

if __name__ == "__main__":
    adb("shell", "pkill", "-f", "mobilecli")
    for name in sys.argv[1:] or SUITES:
        print(f"== {name}")
        SUITES[name]()
    failed = [n for n, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)
