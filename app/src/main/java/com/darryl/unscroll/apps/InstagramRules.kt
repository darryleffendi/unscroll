package com.darryl.unscroll.apps

import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.Geometry
import com.darryl.unscroll.core.NodeSearch
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.SingleItemSession
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.core.isSelectedDeep

class InstagramRules : AppRules {
    private val reelSession = SingleItemSession()
    /** Remembered so tapping Home covers at once, before the stories tray has drawn. */
    private var lastFeedTop: Int? = null
    private var lastTrayHeight = 0
    private var redirect = RedirectState()

    override fun reset() {
        reelSession.reset()
        redirect = RedirectState()
    }

    override fun decide(ctx: ScreenContext): Decision {
        if (redirect.foregroundSinceMs != ctx.foregroundSinceMs) redirect = RedirectState(ctx.foregroundSinceMs)
        val decision = classify(ctx)
        // Once the visit has been somewhere other than Home, Open in Messages is done for it.
        if (decision.screen != HOME && decision.screen != Decision.UNKNOWN) redirect.expired = true
        return decision
    }

    private fun classify(ctx: ScreenContext): Decision {
        val s = ctx.settings

        // Instagram calls stories "reels" internally; the story viewer is reel_viewer_root,
        // the Reels (clips) viewer is clips_viewer_view_pager.
        if (ctx.has("story_viewer")) {
            reelSession.reset()
            return Decision(STORY, Action.Allow)
        }

        val clips = ctx.find("clips_pager")
        if (clips != null) return decideReels(ctx, clips)
        reelSession.reset()

        val tabBar = ctx.find("tab_bar")
        val navTop = tabBar?.bounds?.top ?: ctx.screen.bottom

        if (ctx.has("explore_bar") && ctx.find("search_tab")?.isSelectedDeep() == true) {
            if (!s.isOn(Toggle.IG_HIDE_EXPLORE)) return Decision(EXPLORE, Action.Allow)
            val top = ctx.find("explore_bar")!!.bounds.bottom
            return Decision(EXPLORE, cover(ctx, top, navTop, "Explore is hidden. Search still works."))
        }

        val searchTabs = ctx.find("search_result_tabs")
        if (searchTabs != null) {
            // Only the first ("For you"/"Top") results tab mixes in a suggested grid.
            val firstTabSelected = searchTabs.rowWithChildren(2)?.selectedChildIndex() == 0
            if (!s.isOn(Toggle.IG_HIDE_EXPLORE) || !firstTabSelected) return Decision(SEARCH_RESULTS, Action.Allow)
            val userRows = NodeSearch.findAll(ctx.root) { ctx.selectors["search_user_row"].matchesId(it) }
            val top = userRows.maxOfOrNull { it.bounds.bottom } ?: searchTabs.bounds.bottom
            return Decision(SEARCH_RESULTS, cover(ctx, top, navTop, "Suggested posts are hidden. Accounts tab shows people."))
        }

        val homeTab = ctx.find("home_tab")
        val homeSelected = homeTab?.isSelectedDeep() == true
        if (homeSelected && (ctx.has("stories_tray") || ctx.has("home_logo"))) return decideHome(ctx, navTop)
        if (ctx.has("direct_inbox") || ctx.has("direct_thread")) return Decision(DIRECT, Action.Allow)
        if (ctx.has("profile")) return Decision(PROFILE, Action.Allow)
        if (ctx.has("search_field")) return Decision(SEARCH, Action.Allow)
        return Decision.unknown()
    }

    private fun decideReels(ctx: ScreenContext, pager: com.darryl.unscroll.core.UiNode): Decision {
        val s = ctx.settings
        val reelsTabSelected = ctx.find("reels_tab")?.isSelectedDeep() == true && ctx.has("tab_bar")
        if (reelsTabSelected) {
            reelSession.reset()
            if (!s.isOn(Toggle.IG_BLOCK_REELS_TAB)) return Decision(REELS_TAB, Action.Allow)
            val target = ctx.find(if (s.isOn(Toggle.IG_OPEN_MESSAGES)) "direct_tab" else "home_tab")
            return Decision(REELS_TAB, target?.let { Action.Tap(it, "leave Reels tab") } ?: Action.Back)
        }
        if (s.isOn(Toggle.IG_BLOCK_REELS_ALL)) return Decision(REEL_VIEWER, Action.Back)
        if (!s.isOn(Toggle.IG_BLOCK_REELS_TAB)) return Decision(REEL_VIEWER, Action.Allow)
        val sameReel = reelSession.observe(ctx.centerItemIdentity(pager), ctx.centerItemIndex(pager), ctx.nowMs)
        return Decision(REEL_VIEWER, if (sameReel) Action.Allow else Action.Back)
    }

    private fun decideHome(ctx: ScreenContext, navTop: Int): Decision {
        val s = ctx.settings

        if (s.isOn(Toggle.IG_OPEN_MESSAGES)) redirectToMessages(ctx)?.let { return Decision(HOME, it) }

        val header = ctx.find("home_header")
        val headerBottom = header?.bounds?.bottom
        if (s.isOn(Toggle.IG_HIDE_HOME)) {
            val top = maxOf(header?.bounds?.top ?: ctx.screen.top, ctx.safeTop)
            return Decision(HOME, cover(ctx, top, navTop, "Home is hidden. Messages, search and your profile still work."))
        }
        if (!s.isOn(Toggle.IG_HIDE_FEED)) return Decision(HOME, Action.Allow)

        val tray = ctx.find("stories_tray")
        val top = when {
            tray != null -> {
                lastTrayHeight = tray.bounds.height
                // Cap at header + tray height so a pull-to-refresh never exposes a post.
                val cap = headerBottom?.let { it + lastTrayHeight } ?: tray.bounds.bottom
                minOf(tray.bounds.bottom, cap)
            }
            headerBottom != null -> lastFeedTop?.coerceAtMost(headerBottom + lastTrayHeight) ?: headerBottom
            else -> lastFeedTop ?: ctx.screen.top
        }
        lastFeedTop = top
        return Decision(HOME, cover(ctx, top, navTop, "Feed is hidden. Stories stay above."))
    }

    /**
     * Open in Messages: when Instagram comes to the foreground and its first screen is
     * Home, tap the Messages tab (retried a few times; the Home covers apply if it fails).
     */
    private fun redirectToMessages(ctx: ScreenContext): Action? {
        val fresh = ctx.nowMs - ctx.foregroundSinceMs <= REDIRECT_WINDOW_MS
        if (!fresh || redirect.expired || redirect.attempts >= MAX_REDIRECT_ATTEMPTS) return null
        if (ctx.nowMs - redirect.lastAttemptMs < REDIRECT_RETRY_MS) return null
        val direct = ctx.find("direct_tab") ?: return null
        redirect.attempts++
        redirect.lastAttemptMs = ctx.nowMs
        return Action.Tap(direct, "open Messages")
    }

    private fun cover(ctx: ScreenContext, top: Int, bottom: Int, message: String): Action =
        Geometry.band(ctx.screen, top, bottom)?.let { Action.Cover(it, message) } ?: Action.Allow

    private class RedirectState(val foregroundSinceMs: Long = -1L) {
        var attempts = 0
        var expired = false
        var lastAttemptMs = Long.MIN_VALUE / 2
    }

    companion object {
        const val HOME = "IG_HOME"
        const val EXPLORE = "IG_EXPLORE"
        const val SEARCH = "IG_SEARCH"
        const val SEARCH_RESULTS = "IG_SEARCH_RESULTS"
        const val REELS_TAB = "IG_REELS_TAB"
        const val REEL_VIEWER = "IG_REEL_VIEWER"
        const val STORY = "IG_STORY"
        const val DIRECT = "IG_DIRECT"
        const val PROFILE = "IG_PROFILE"

        const val REDIRECT_WINDOW_MS = 8_000L
        const val REDIRECT_RETRY_MS = 600L
        const val MAX_REDIRECT_ATTEMPTS = 3
    }
}
