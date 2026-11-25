package com.darryl.unscroll.apps

import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppSelectors
import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.TapPrediction
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

    /** Bottom-bar tabs other than Home never show a cover. Home itself may be Subscriptions: let the scan decide. */
    override fun predictTap(tapped: UiNode, screen: Bounds, selectors: AppSelectors): TapPrediction {
        val cell = tapped.bottomBarCell(screen, PIVOT_CELLS)
        return if (cell >= 0 && cell != selectors.number("pivot_home_index", 0)) TapPrediction.Leaves else TapPrediction.Unknown
    }

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
            val same = shortSession.observe(ctx.centerItemIdentity(ctx.find("shorts_pager")), ctx.centerItemIndex(ctx.find("shorts_pager")), ctx.nowMs)
            return Decision(SHORTS_PLAYER, if (same) Action.Allow else Action.Back)
        }
        shortSession.reset()

        val watchList = ctx.find("watch_list")
        if (watchList != null) return decideWatch(ctx, watchList)

        if (pivot != null && pivotTab(ctx, pivot, "home")?.isSelectedDeep() == true && !ctx.has("search_box")) {
            // Subscriptions selected by label (any language listed in selectors.json).
            if (ctx.nodes.any { ctx.selectors["subs_tab"].matchesLabel(it) && it.isSelectedDeep() }) {
                return Decision(SUBSCRIPTIONS, Action.Allow)
            }
            // The Home/Subs/… strip: by id, else by shape, so a renamed id never leaves it covered.
            val tabs = ctx.find("feed_tabs") ?: topTabStrip(ctx, pivot)
            val selectedTab = tabs?.rowWithChildren(2)?.selectedChildIndex() ?: 0
            if (selectedTab != 0) return Decision(SUBSCRIPTIONS, Action.Allow)
            if (!s.isOn(Toggle.YT_HIDE_HOME)) return Decision(HOME, Action.Allow)
            val top = maxOf(ctx.find("app_bar")?.bounds?.bottom ?: ctx.screen.top, tabs?.bounds?.bottom ?: 0)
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

    /**
     * A horizontal strip of tabs/chips in the top third of the screen: at least three
     * clickable children side by side, short and nearly full width.
     */
    private fun topTabStrip(ctx: ScreenContext, pivot: UiNode): UiNode? = ctx.nodes.firstOrNull { n ->
        val b = n.bounds
        if (n === pivot || b.top > ctx.screen.top + ctx.screen.height / 3 || b.height > ctx.screen.height / 10) return@firstOrNull false
        if (b.width < ctx.screen.width * 7 / 10) return@firstOrNull false
        val row = n.rowWithChildren(3, depth = 2) ?: return@firstOrNull false
        val kids = row.children.toList()
        kids.count { it.isClickable || it.children.any { c -> c.isClickable } } >= 3 &&
            kids.zipWithNext().all { (a, c) -> c.bounds.left >= a.bounds.left && kotlin.math.abs(c.bounds.top - a.bounds.top) < 20 }
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
        private const val PIVOT_CELLS = 4
    }
}
