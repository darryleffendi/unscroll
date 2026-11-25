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
        // Later in the same visit (the user tapped Home themselves): no redirect, feed is covered.
        val later = rules.decide(Fixtures.context(pkg, file, settings, nowMs = 60_000, foregroundSinceMs = 500))
        assertTrue(later.action is Action.Cover)
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
