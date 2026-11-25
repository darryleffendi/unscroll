package com.darryl.unscroll.apps

import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppSelectors
import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.TapPrediction
import com.darryl.unscroll.core.UiNode
import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.Geometry
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.SingleItemSession
import com.darryl.unscroll.core.Toggle

/**
 * TikTok obfuscates its view ids (they change with every release), so screens are
 * recognized by structure: the five-cell bottom bar and the video detail activity.
 */
class TikTokRules : AppRules {
    private val videoSession = SingleItemSession()

    override fun reset() = videoSession.reset()

    override fun predictTap(tapped: UiNode, screen: Bounds, selectors: AppSelectors): TapPrediction =
        when (tapped.bottomBarCell(screen, 5)) {
            -1 -> TapPrediction.Unknown
            selectors.number("home_tab_index", 0) -> TapPrediction.Arrives(FEED)
            else -> TapPrediction.Leaves
        }

    override fun decide(ctx: ScreenContext): Decision {
        val on = ctx.settings.isOn(Toggle.TT_HIDE_FEEDS)
        val nav = ctx.structuralBottomNav(5..5)
        if (nav != null) {
            videoSession.reset()
            if (nav.selectedChildIndex() != ctx.selectors.number("home_tab_index", 0)) return Decision(TAB, Action.Allow)
            if (!on) return Decision(FEED, Action.Allow)
            val band = Geometry.band(ctx.screen, ctx.safeTop, nav.bounds.top)
                ?: return Decision(FEED, Action.Allow)
            return Decision(FEED, Action.Cover(band, "Feeds are hidden. Messages, search and profiles still work."))
        }

        val detail = ctx.windowClass?.let { cls -> ctx.selectors["video_activity"].ids.any { cls.endsWith(it) } } == true
        if (detail || ctx.has("video_page")) {
            if (!on) return Decision(VIDEO, Action.Allow)
            val same = videoSession.observe(ctx.centerItemIdentity(ctx.find("video_pager") ?: ctx.root), ctx.centerItemIndex(ctx.find("video_pager") ?: ctx.root), ctx.nowMs)
            return Decision(VIDEO, if (same) Action.Allow else Action.Back)
        }
        videoSession.reset()
        return Decision(OTHER, Action.Allow)
    }

    companion object {
        const val FEED = "TT_FEED"
        const val TAB = "TT_TAB"
        const val VIDEO = "TT_VIDEO"
        const val OTHER = "TT_OTHER"
    }
}
