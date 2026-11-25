package com.darryl.unscroll.apps

import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.Geometry
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.SingleItemSession
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.core.isSelectedDeep
import com.darryl.unscroll.core.UiNode
import com.darryl.unscroll.core.children

class YouTubeRules : AppRules {
    private val shortSession = SingleItemSession()

    override fun reset() = shortSession.reset()

    override fun decide(ctx: ScreenContext): Decision {
        val s = ctx.settings
        val pivot = ctx.find("pivot_bar")

        if (ctx.has("shorts_player")) {
            if (pivot != null && pivotTab(ctx, pivot, "shorts")?.isSelectedDeep() == true) {
                shortSession.reset()
                if (!s.isOn(Toggle.YT_BLOCK_SHORTS_TAB)) return Decision(SHORTS_TAB, Action.Allow)
                val home = pivotTab(ctx, pivot, "home")
                return Decision(SHORTS_TAB, home?.let { Action.Tap(it, "leave Shorts tab") } ?: Action.Back)
            }
            if (s.isOn(Toggle.YT_BLOCK_SHORTS_ALL)) return Decision(SHORTS_PLAYER, Action.Back)
            if (!s.isOn(Toggle.YT_BLOCK_SHORTS_TAB)) return Decision(SHORTS_PLAYER, Action.Allow)
            val same = shortSession.observe(ctx.centerItemIdentity(ctx.find("shorts_pager")), ctx.viewerIndex, ctx.nowMs)
            return Decision(SHORTS_PLAYER, if (same) Action.Allow else Action.Back)
        }
        shortSession.reset()

        val watchList = ctx.find("watch_list")
        if (watchList != null) return decideWatch(ctx, watchList)

        if (pivot != null && pivotTab(ctx, pivot, "home")?.isSelectedDeep() == true && !ctx.has("search_box")) {
            val tabs = ctx.find("feed_tabs")
            val firstTabSelected = tabs?.rowWithChildren(2)?.selectedChildIndex()?.let { it == 0 } ?: true
            if (!firstTabSelected) return Decision(SUBSCRIPTIONS, Action.Allow)
            if (!s.isOn(Toggle.YT_HIDE_HOME)) return Decision(HOME, Action.Allow)
            val top = (tabs ?: ctx.find("app_bar"))?.bounds?.bottom ?: ctx.screen.top
            val bottom = pivot.bounds.top
            return Decision(HOME, cover(ctx, top, bottom, "Home is hidden. Subscriptions, search and your library still work."))
        }
        if (ctx.has("search_box")) return Decision(SEARCH, Action.Allow)
        if (pivot != null) return Decision(BROWSE, Action.Allow)
        return Decision.unknown()
    }

    private fun decideWatch(ctx: ScreenContext, list: UiNode): Decision {
        if (!ctx.settings.isOn(Toggle.YT_HIDE_UP_NEXT)) return Decision(WATCH, Action.Allow)
        // Comments or description open in the engagement panel: leave the page alone.
        val panel = ctx.find("engagement_panel")
        if (panel != null && panel.bounds.height > ctx.screen.height / 4) return Decision(WATCH, Action.Allow)

        val items = list.children.toList()
        val maxMetadata = ctx.selectors.number("watch_metadata_items", 3)
        val scrolledToTop = items.firstOrNull()?.let { it.bounds.top <= list.bounds.top + 4 } ?: false
        val teaser = items.take(maxMetadata + 2).firstOrNull { item ->
            com.darryl.unscroll.core.NodeSearch.find(item, 60) { ctx.selectors["comments_teaser"].matchesLabel(it) } != null
        }
        val top = when {
            teaser != null -> teaser.bounds.bottom
            scrolledToTop && items.size >= maxMetadata -> items[maxMetadata - 1].bounds.bottom
            else -> list.bounds.top
        }
        return Decision(WATCH, cover(ctx, top, list.bounds.bottom, "Up next is hidden."))
    }

    /** A bottom-bar tab: by its description when that matches, else by position. */
    private fun pivotTab(ctx: ScreenContext, pivot: UiNode, name: String): UiNode? {
        val row = pivot.rowWithChildren(3) ?: return null
        val tabs = row.children.toList()
        tabs.firstOrNull { ctx.selectors["pivot_$name"].matchesLabel(it) }?.let { return it }
        return tabs.getOrNull(ctx.selectors.number("pivot_${name}_index", -1))
    }

    private fun cover(ctx: ScreenContext, top: Int, bottom: Int, message: String): Action =
        Geometry.band(ctx.screen, top, bottom)?.let { Action.Cover(it, message) } ?: Action.Allow

    companion object {
        const val HOME = "YT_HOME"
        const val SUBSCRIPTIONS = "YT_SUBSCRIPTIONS"
        const val SEARCH = "YT_SEARCH"
        const val BROWSE = "YT_BROWSE"
        const val WATCH = "YT_WATCH"
        const val SHORTS_TAB = "YT_SHORTS_TAB"
        const val SHORTS_PLAYER = "YT_SHORTS_PLAYER"
    }
}
