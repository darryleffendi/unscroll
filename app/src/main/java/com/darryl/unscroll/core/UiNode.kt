package com.darryl.unscroll.core

/** Screen rectangle in pixels; a plain JVM type so classifiers run in unit tests. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val isEmpty get() = width <= 0 || height <= 0
    fun contains(other: Bounds) =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom
    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/**
 * The slice of a view node the classifiers look at. Backed by AccessibilityNodeInfo
 * on device and by parsed XML dumps in tests. Screen text is never exposed, only
 * whether a node has any.
 */
interface UiNode {
    /** Resource entry name without the package prefix, e.g. "feed_tab". */
    val viewId: String?
    val contentDescription: String?
    val className: String?
    val hasText: Boolean
    /**
     * Visible text, for in-memory matching only (X's tabs carry no description).
     * Never logged or written; null in fixtures.
     */
    val text: String?
    val bounds: Bounds
    val isClickable: Boolean
    val isSelected: Boolean
    val isScrollable: Boolean
    val isVisible: Boolean
    /** Position within a list or pager (CollectionItemInfo row), or -1. */
    val itemIndex: Int
    val childCount: Int
    fun child(index: Int): UiNode?
    /** Tap this node (or its nearest clickable ancestor). Returns false when it couldn't. */
    fun click(): Boolean = false
    /** Scroll a scrollable list back by a page. */
    fun scrollBackward(): Boolean = false
}

val UiNode.children: Sequence<UiNode>
    get() = (0 until childCount).asSequence().mapNotNull { child(it) }

/** Selected itself or through a shallow child: tabs often mark only their icon as selected. */
fun UiNode.isSelectedDeep(depth: Int = 2): Boolean =
    isSelected || (depth > 0 && children.any { it.isSelectedDeep(depth - 1) })
