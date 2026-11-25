package com.darryl.unscroll.apps

import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.Geometry
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.core.isSelectedDeep

class LinkedInRules : AppRules {
    override fun decide(ctx: ScreenContext): Decision {
        val homeTab = ctx.find("home_tab") ?: return Decision(OTHER, Action.Allow)
        if (!homeTab.isSelectedDeep()) return Decision(TAB, Action.Allow)
        if (!ctx.settings.isOn(Toggle.LI_HIDE_FEED)) return Decision(FEED, Action.Allow)
        val top = ctx.find("top_bar")?.bounds?.bottom ?: ctx.screen.top
        val bottom = ctx.find("bottom_bar")?.bounds?.top ?: homeTab.bounds.top
        val band = Geometry.band(ctx.screen, top, bottom) ?: return Decision(FEED, Action.Allow)
        return Decision(FEED, Action.Cover(band, "Feed is hidden. Search, messages, jobs and notifications still work."))
    }

    companion object {
        const val FEED = "LI_FEED"
        const val TAB = "LI_TAB"
        const val OTHER = "LI_OTHER"
    }
}
