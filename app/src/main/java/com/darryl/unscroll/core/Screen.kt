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

    /**
     * Every visible node, breadth-first, from a single walk of the tree. Each child access
     * is a cross-process call into the app, so rules query this instead of walking again.
     */
    private val nodesLazy = lazy { NodeSearch.findAll(root, SNAPSHOT_BUDGET) { true } }
    val nodes: List<UiNode> by nodesLazy

    /** Snapshot size, or -1 if no rule needed it (debug logging). */
    fun nodesIfBuilt(): Int = if (nodesLazy.isInitialized()) nodesLazy.value.size else -1

    private val byId: Map<String, List<UiNode>> by lazy {
        val map = HashMap<String, MutableList<UiNode>>()
        for (n in nodes) n.viewId?.let { map.getOrPut(it) { mutableListOf() }.add(n) }
        map
    }

    /** First visible node matching the selector: its ids in order, then its labels. */
    fun find(key: String): UiNode? = cache.getOrPut(key) {
        val selector = selectors[key]
        for (id in selector.ids) byId[id]?.firstOrNull()?.let { return@getOrPut it }
        if (selector.descs.isNotEmpty() || selector.texts.isNotEmpty()) nodes.firstOrNull { selector.matchesLabel(it) } else null
    }

    /** All visible nodes with one of the selector's ids. */
    fun findAllById(key: String): List<UiNode> = selectors[key].ids.flatMap { byId[it].orEmpty() }

    fun has(key: String) = find(key) != null

    companion object {
        const val SNAPSHOT_BUDGET = 900
    }
}

sealed interface Action {
    data object Allow : Action
    /**
     * Cover the rectangle with the curtain; with [scrollBack], also scroll that list
     * backwards (Instagram's feed, to bring the stories tray back into view).
     */
    data class Cover(val bounds: Bounds, val message: String, val scrollBack: UiNode? = null) : Action
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

/** What a tap on a navigation tab is about to do, known before the app redraws. */
sealed interface TapPrediction {
    data object Unknown : TapPrediction
    /** Leaving for a screen that is never covered: drop the curtain now. */
    data object Leaves : TapPrediction
    /** Going to [screen]: show its last cover now, the next scan corrects it. */
    data class Arrives(val screen: String) : TapPrediction
}

interface AppRules {
    fun decide(ctx: ScreenContext): Decision

    fun predictTap(tapped: UiNode, screen: Bounds, selectors: AppSelectors): TapPrediction = TapPrediction.Unknown

    /** The app left the foreground; drop per-visit state. */
    fun reset() {}
}
