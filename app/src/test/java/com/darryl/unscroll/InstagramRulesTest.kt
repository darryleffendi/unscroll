package com.darryl.unscroll

import com.darryl.unscroll.apps.InstagramRules
import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramRulesTest {
    private val pkg = "com.instagram.android"
    private val defaults = Settings.DEFAULT

    private fun decide(screen: String, label: String? = null, settings: Settings = defaults) =
        Fixtures.decide(InstagramRules(), pkg, Fixtures.file("instagram", screen, label), settings)

    @Test fun `home feed is covered from the stories tray to the tab bar`() {
        val d = decide("home")
        assertEquals(InstagramRules.HOME, d.screen)
        val cover = d.action as Action.Cover
        assertEquals(624, cover.bounds.top)     // reels_tray_container bottom
        assertEquals(2211, cover.bounds.bottom) // tab_bar top
    }

    @Test fun `hide home screen covers the header and stories too`() {
        val cover = decide("home", settings = defaults.with(Toggle.IG_HIDE_HOME, true)).action as Action.Cover
        assertEquals(132, cover.bounds.top)
        assertEquals(2211, cover.bounds.bottom)
    }

    @Test fun `home is left alone when feed hiding is off`() {
        assertEquals(Action.Allow, decide("home", settings = defaults.with(Toggle.IG_HIDE_FEED, false)).action)
    }

    @Test fun `open in messages taps the Messages tab right after launch`() {
        val rules = InstagramRules()
        val settings = defaults.with(Toggle.IG_OPEN_MESSAGES, true)
        val file = Fixtures.file("instagram", "home")
        val first = rules.decide(Fixtures.context(pkg, file, settings, nowMs = 1_000, foregroundSinceMs = 500))
        assertEquals("open Messages", (first.action as Action.Tap).label)
        // After visiting another screen, coming back to Home is not redirected.
        rules.decide(Fixtures.context(pkg, Fixtures.file("instagram", "direct_inbox"), settings, nowMs = 1_500, foregroundSinceMs = 500))
        val back = rules.decide(Fixtures.context(pkg, file, settings, nowMs = 3_000, foregroundSinceMs = 500))
        assertTrue(back.action is Action.Cover)
        // Long after launch: no redirect either.
        val late = InstagramRules().decide(Fixtures.context(pkg, file, settings, nowMs = 60_000, foregroundSinceMs = 500))
        assertTrue(late.action is Action.Cover)
    }

    @Test fun `explore grid is covered below the search bar`() {
        val d = decide("explore")
        assertEquals(InstagramRules.EXPLORE, d.screen)
        val cover = d.action as Action.Cover
        assertTrue(cover.bounds.top in 250..450)
        assertEquals(2211, cover.bounds.bottom)
    }

    @Test fun `typing a search is not covered`() {
        assertEquals(Action.Allow, decide("search_typing", "query").action)
        assertEquals(Action.Allow, decide("search_typing", "empty").action)
    }

    @Test fun `keyword results cover the suggested grid below the account rows`() {
        val d = decide("search_results", "keyword")
        assertEquals(InstagramRules.SEARCH_RESULTS, d.screen)
        assertEquals(573, (d.action as Action.Cover).bounds.top) // last row_search_user_container bottom
    }

    @Test fun `reels tab is left via the Home tab`() {
        val d = decide("reels_tab")
        assertEquals(InstagramRules.REELS_TAB, d.screen)
        assertEquals("leave Reels tab", (d.action as Action.Tap).label)
    }

    @Test fun `reel from a DM plays once in balanced mode`() {
        val rules = InstagramRules()
        val first = rules.decide(Fixtures.context(pkg, Fixtures.file("instagram", "reel_viewer_dm"), defaults))
        assertEquals(InstagramRules.REEL_VIEWER, first.screen)
        assertEquals(Action.Allow, first.action)
        val again = rules.decide(Fixtures.context(pkg, Fixtures.file("instagram", "reel_viewer_dm"), defaults))
        assertEquals(Action.Allow, again.action)
    }

    @Test fun `moving to the next reel closes the viewer`() {
        val rules = InstagramRules()
        rules.decide(Fixtures.context(pkg, Fixtures.file("instagram", "reel_viewer_dm"), defaults, viewerIndex = 0))
        val next = rules.decide(Fixtures.context(pkg, Fixtures.file("instagram", "reel_viewer_dm", "next"), defaults, nowMs = 110_000, viewerIndex = 1))
        assertEquals(Action.Back, next.action)
    }

    @Test fun `reel from a profile plays once`() {
        assertEquals(Action.Allow, decide("reel_viewer_profile", "reels-tab").action)
    }

    @Test fun `strict mode closes every reel`() {
        val strict = defaults.with(Toggle.IG_BLOCK_REELS_ALL, true)
        assertEquals(Action.Back, decide("reel_viewer_dm", settings = strict).action)
        assertEquals(Action.Back, decide("reel_viewer_profile", "reels-tab", settings = strict).action)
    }

    @Test fun `stories are never blocked, even in strict mode`() {
        val strict = defaults.with(Toggle.IG_BLOCK_REELS_ALL, true)
        for (f in Fixtures.files("instagram", "story_viewer")) {
            val d = Fixtures.decide(InstagramRules(), pkg, f, strict)
            assertEquals(f.name, InstagramRules.STORY, d.screen)
            assertEquals(Action.Allow, d.action)
        }
    }

    @Test fun `messages, profiles and a profile's post list are allowed`() {
        for ((screen, label) in listOf("direct_inbox" to null, "direct_thread" to null, "profile" to "own",
            "profile" to "other", "profile" to "other-reels-tab", "reel_viewer_profile" to "grid-feed")) {
            val d = decide(screen, label)
            assertEquals("$screen $label", Action.Allow, d.action)
        }
    }

    @Test fun `an unrecognized full-screen prompt is not covered`() {
        assertEquals(Action.Allow, decide("unknown", "location-prompt").action)
    }
}

class InstagramScrolledHomeTest {
    private val pkg = "com.instagram.android"

    @Test fun `stories scrolled off - the cover expands and the feed is scrolled back`() {
        for (label in listOf("scrolled", "scrolled-far")) {
            val ctx = Fixtures.context(pkg, Fixtures.file("instagram", "home", label), Settings.DEFAULT)
            val d = InstagramRules().decide(ctx)
            assertEquals(label, InstagramRules.HOME, d.screen)
            val cover = d.action as Action.Cover
            assertTrue("$label covers from the top of the content", cover.bounds.top <= 279)
            assertEquals(2211, cover.bounds.bottom)
            assertTrue("$label asks to scroll the feed back", cover.scrollBack != null)
        }
    }

    @Test fun `stories in view - normal cover, no scrolling`() {
        val d = InstagramRules().decide(Fixtures.context(pkg, Fixtures.file("instagram", "home"), Settings.DEFAULT))
        assertEquals(null, (d.action as Action.Cover).scrollBack)
    }
}

/** Instagram's MainTabActivity layout renamed the header (main_feed_action_bar). */
class InstagramMainTabLayoutTest {
    private val pkg = "com.instagram.android"

    @Test fun `new layout - feed covered below the stories, header found for the pull cap`() {
        val rules = InstagramRules()
        val ctx = Fixtures.context(pkg, Fixtures.file("instagram", "home", "maintab"), Settings.DEFAULT)
        assertTrue("header must be found, or pull-to-refresh can expose posts", ctx.has("home_header"))
        val cover = rules.decide(ctx).action as Action.Cover
        assertEquals(ctx.find("stories_tray")!!.bounds.bottom, cover.bounds.top)
    }
}
