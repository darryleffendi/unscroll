package com.darryl.unscroll

import com.darryl.unscroll.apps.LinkedInRules
import com.darryl.unscroll.apps.TikTokRules
import com.darryl.unscroll.apps.YouTubeRules
import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRulesTest {
    private val pkg = "com.google.android.youtube"
    private fun decide(screen: String, label: String? = null, settings: Settings = Settings.DEFAULT) =
        Fixtures.decide(YouTubeRules(), pkg, Fixtures.file("youtube", screen, label), settings)

    @Test fun `home is covered between the top tabs and the bottom bar`() {
        val d = decide("home")
        assertEquals(YouTubeRules.HOME, d.screen)
        val cover = d.action as Action.Cover
        assertEquals(384, cover.bounds.top)
        assertEquals(2211, cover.bounds.bottom)
    }

    @Test fun `subscriptions, search and You stay open`() {
        assertEquals(YouTubeRules.SUBSCRIPTIONS, decide("subscriptions").screen)
        assertEquals(Action.Allow, decide("subscriptions").action)
        assertEquals(Action.Allow, decide("search", "results").action)
        assertEquals(Action.Allow, decide("search", "typing").action)
        assertEquals(Action.Allow, decide("search", "results-miniplayer").action)
        assertEquals(Action.Allow, decide("you").action)
    }

    @Test fun `shorts tab is left via Home`() {
        val d = decide("shorts_tab")
        assertEquals(YouTubeRules.SHORTS_TAB, d.screen)
        assertEquals("leave Shorts tab", (d.action as Action.Tap).label)
    }

    @Test fun `a short from search plays once, the next one closes`() {
        val rules = YouTubeRules()
        val first = rules.decide(Fixtures.context(pkg, Fixtures.file("youtube", "shorts_player", "search"), Settings.DEFAULT))
        assertEquals(YouTubeRules.SHORTS_PLAYER, first.screen)
        assertEquals(Action.Allow, first.action)
        val next = rules.decide(Fixtures.context(pkg, Fixtures.file("youtube", "shorts_player", "search-next"), Settings.DEFAULT, nowMs = 110_000))
        assertEquals(Action.Back, next.action)
    }

    @Test fun `strict mode closes any short`() {
        assertEquals(Action.Back, decide("shorts_player", "search", Settings.DEFAULT.with(Toggle.YT_BLOCK_SHORTS_ALL, true)).action)
    }

    @Test fun `up next is covered below the comments teaser`() {
        val d = decide("watch")
        assertEquals(YouTubeRules.WATCH, d.screen)
        val cover = d.action as Action.Cover
        assertEquals(1302, cover.bounds.top)
        assertEquals(2337, cover.bounds.bottom)
    }

    @Test fun `watch page is left alone when up next hiding is off`() {
        assertEquals(Action.Allow, decide("watch", settings = Settings.DEFAULT.with(Toggle.YT_HIDE_UP_NEXT, false)).action)
    }
}

class TikTokRulesTest {
    private val pkg = "com.ss.android.ugc.trill"

    @Test fun `home feed is covered above the bottom bar`() {
        val d = Fixtures.decide(TikTokRules(), pkg, Fixtures.file("tiktok", "for_you"))
        assertEquals(TikTokRules.FEED, d.screen)
        assertEquals(2208, (d.action as Action.Cover).bounds.bottom)
    }

    @Test fun `inbox and messages stay open`() {
        assertEquals(Action.Allow, Fixtures.decide(TikTokRules(), pkg, Fixtures.file("tiktok", "inbox")).action)
        assertEquals(Action.Allow, Fixtures.decide(TikTokRules(), pkg, Fixtures.file("tiktok", "dm_thread")).action)
    }

    @Test fun `a video from a DM plays`() {
        val d = Fixtures.decide(TikTokRules(), pkg, Fixtures.file("tiktok", "video_single", "dm"),
            windowClass = "com.ss.android.ugc.aweme.detail.ui.DetailActivity")
        assertEquals(TikTokRules.VIDEO, d.screen)
        assertEquals(Action.Allow, d.action)
    }

    @Test fun `swiping to another video in the detail view closes it`() {
        val rules = TikTokRules()
        val file = Fixtures.file("tiktok", "video_single", "dm")
        val cls = "com.ss.android.ugc.aweme.detail.ui.DetailActivity"
        rules.decide(Fixtures.context(pkg, file, windowClass = cls, viewerIndex = 0))
        val next = rules.decide(Fixtures.context(pkg, file, windowClass = cls, nowMs = 110_000, viewerIndex = 1))
        assertEquals(Action.Back, next.action)
    }
}

class LinkedInRulesTest {
    private val pkg = "com.linkedin.android"

    @Test fun `home feed is covered between the top bar and the bottom bar`() {
        val d = Fixtures.decide(LinkedInRules(), pkg, Fixtures.file("linkedin", "home"))
        assertEquals(LinkedInRules.FEED, d.screen)
        val cover = d.action as Action.Cover
        assertEquals(248, cover.bounds.top)
        assertEquals(2208, cover.bounds.bottom)
    }

    @Test fun `other tabs stay open`() {
        val d = Fixtures.decide(LinkedInRules(), pkg, Fixtures.file("linkedin", "jobs"))
        assertTrue(d.action is Action.Allow)
    }
}
