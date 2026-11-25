package com.darryl.unscroll.core

/**
 * "One video at a time": the first item seen in a viewer is allowed, moving to any
 * other item ends the session. An item is recognized by a content identity (digest of
 * its descriptions) and, when the app reports it, the pager index; either changing
 * counts as moving on.
 *
 * For a short warm-up after opening, changes only refine the baseline: the viewer's
 * descriptions fill in as it loads and the pager settles on its starting item.
 */
class SingleItemSession(private val warmUpMs: Long = WARM_UP_MS) {
    private var active = false
    private var startedAtMs = 0L
    private var identity: String? = null
    private var index: Int? = null

    val isActive get() = active

    /** Returns true while the viewer still shows the item it opened with. */
    fun observe(identity: String?, index: Int?, nowMs: Long): Boolean {
        if (!active) {
            active = true
            startedAtMs = nowMs
            this.identity = identity
            this.index = index
            return true
        }
        if (nowMs - startedAtMs < warmUpMs) {
            if (identity != null) this.identity = identity
            if (index != null) this.index = index
            return true
        }
        if (identity != null) {
            if (this.identity == null) this.identity = identity
            else if (identity != this.identity) return false
        }
        if (index != null) {
            if (this.index == null) this.index = index
            else if (index != this.index) return false
        }
        return true
    }

    fun reset() {
        active = false
        startedAtMs = 0L
        identity = null
        index = null
    }
}

const val WARM_UP_MS = 1_200L

object ContentIdentity {
    private val digits = Regex("\\d")

    /**
     * Digest of the descriptions in a subtree, skipping any with digits (like and view
     * counts, timestamps), so it is stable while one item plays and changes with the item.
     */
    fun of(node: UiNode?, budget: Int = 200): String? {
        node ?: return null
        val parts = NodeSearch.findAll(node, budget) { !it.contentDescription.isNullOrBlank() }
            .mapNotNull { it.contentDescription?.takeIf { d -> !digits.containsMatchIn(d) } }
        if (parts.isEmpty()) return null
        return parts.joinToString("|").hashCode().toString(16)
    }

    /** The page among [candidates] that holds the screen's center point. */
    fun centerPage(candidates: List<UiNode>, screen: Bounds): UiNode? {
        val cx = (screen.left + screen.right) / 2
        val cy = (screen.top + screen.bottom) / 2
        return candidates.firstOrNull { b -> b.bounds.let { cx in it.left until it.right && cy in it.top until it.bottom } }
    }
}
