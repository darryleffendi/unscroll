package com.darryl.unscroll.core

/** Everything a rule set needs to judge one moment of one app. */
class ScreenContext(
    val packageName: String,
    /** Root of the app's active window. */
    val root: UiNode,
    val screen: Bounds,
    val settings: Settings,
    val selectors: AppSelectors,
    val nowMs: Long,
    /** Class name from the last window-state event of this app, e.g. an Activity. */
    val windowClass: String? = null,
    /** When the app last came to the foreground. */
    val foregroundSinceMs: Long = 0L,
    /** Pager index from the last scroll event inside a full-screen video viewer, if any. */
    val viewerIndex: Int? = null,
    /** Bottom of the status bar: covers that start at the top of the window begin here. */
    val safeTop: Int = screen.top,
) {
    private val cache = HashMap<String, UiNode?>()

    /** First visible node matching the selector; ids are tried in a first pass, labels in a second. */
    fun find(key: String, budget: Int = NodeSearch.DEFAULT_BUDGET): UiNode? = cache.getOrPut(key) {
        val selector = selectors[key]
        if (selector.ids.isNotEmpty()) NodeSearch.find(root, budget) { selector.matchesId(it) }?.let { return@getOrPut it }
        if (selector.descs.isNotEmpty() || selector.texts.isNotEmpty()) NodeSearch.find(root, budget) { selector.matchesLabel(it) } else null
    }

    fun has(key: String) = find(key) != null
}

sealed interface Action {
    data object Allow : Action
    /** Cover the rectangle with the curtain. */
    data class Cover(val bounds: Bounds, val message: String) : Action
    /** Press Back. */
    data object Back : Action
    /** Tap a node (a tab), with Back as the fallback when the tap fails. */
    data class Tap(val node: UiNode, val label: String) : Action
}

/** A classified screen (named for the debug line and logs) and what to do about it. */
data class Decision(val screen: String, val action: Action) {
    companion object {
        /** A screen no rule recognized: the service steps aside after a grace period. */
        const val UNKNOWN = "UNKNOWN"
        fun unknown() = Decision(UNKNOWN, Action.Allow)
    }
}

interface AppRules {
    fun decide(ctx: ScreenContext): Decision
    /** The app left the foreground; drop per-visit state. */
    fun reset() {}
}
