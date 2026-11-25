package com.darryl.unscroll.apps

import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.Geometry
import com.darryl.unscroll.core.NodeSearch
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.core.children

/**
 * X covers the "For you" timeline. Untested: X refuses to sign in on the emulator, so
 * the selectors are best guesses (labels first) and anything unrecognized is left alone.
 */
class XRules : AppRules {
    override fun decide(ctx: ScreenContext): Decision {
        if (!ctx.settings.isOn(Toggle.X_HIDE_FOR_YOU)) return Decision(OTHER, Action.Allow)
        val forYou = ctx.selectors["for_you_tab"]
        val row = ctx.nodes.firstOrNull { node ->
            node.childCount in 2..8 && node.bounds.top < ctx.screen.height / 3 &&
                node.children.any { child -> NodeSearch.find(child, 12) { forYou.matches(it) } != null }
        } ?: return Decision(OTHER, Action.Allow)
        val tabs = row.children.toList()
        val forYouIndex = tabs.indexOfFirst { child -> NodeSearch.find(child, 12) { forYou.matches(it) } != null }
        val selected = row.selectedChildIndex()
        if (selected < 0 || selected != forYouIndex) return Decision(TIMELINE, Action.Allow)

        val nav = ctx.find("bottom_nav") ?: ctx.structuralBottomNav(4..6)
        val bottom = nav?.bounds?.top ?: ctx.screen.bottom
        val band = Geometry.band(ctx.screen, row.bounds.bottom, bottom) ?: return Decision(FOR_YOU, Action.Allow)
        return Decision(FOR_YOU, Action.Cover(band, "For you is hidden. Tap Following."))
    }

    companion object {
        const val FOR_YOU = "X_FOR_YOU"
        const val TIMELINE = "X_TIMELINE"
        const val OTHER = "X_OTHER"
    }
}
