package com.darryl.unscroll.apps

import com.darryl.unscroll.core.ContentIdentity
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.UiNode
import com.darryl.unscroll.core.children
import com.darryl.unscroll.core.isSelectedDeep

/** Index of the first selected child, or -1. */
internal fun UiNode.selectedChildIndex(): Int {
    children.forEachIndexed { i, child -> if (child.isSelectedDeep()) return i }
    return -1
}

/** First descendant (breadth-first, shallow) with at least [min] children: a tab strip's row. */
internal fun UiNode.rowWithChildren(min: Int, depth: Int = 3): UiNode? {
    if (childCount >= min) return this
    if (depth == 0) return null
    return children.firstNotNullOfOrNull { it.rowWithChildren(min, depth - 1) }
}

/**
 * Position of the full-screen page under the screen center, from the pager's
 * CollectionItemInfo; falls back to the index reported by scroll events.
 */
internal fun ScreenContext.centerItemIndex(pager: UiNode?): Int? {
    pager ?: return viewerIndex
    val cx = (screen.left + screen.right) / 2
    val cy = (screen.top + screen.bottom) / 2
    val page = com.darryl.unscroll.core.NodeSearch.find(pager, budget = 40) { n ->
        n.itemIndex >= 0 && n.bounds.height > screen.height / 2 &&
            cx in n.bounds.left until n.bounds.right && cy in n.bounds.top until n.bounds.bottom
    }
    return page?.itemIndex ?: viewerIndex
}

/** Identity of the full-screen item under the screen center inside a pager. */
internal fun ScreenContext.centerItemIdentity(pager: UiNode?): String? {
    pager ?: return null
    val page = ContentIdentity.centerPage(pager.children.toList(), screen) ?: pager
    return ContentIdentity.of(page)
}

/**
 * A bottom navigation bar found by shape alone, for apps with obfuscated ids:
 * a full-width row near the bottom whose children are all clickable cells of similar width.
 */
internal fun ScreenContext.structuralBottomNav(cells: IntRange): UiNode? =
    nodes.firstOrNull { node ->
        val b = node.bounds
        if (node.childCount !in cells) return@firstOrNull false
        if (b.width < screen.width * 9 / 10 || b.bottom < screen.top + screen.height * 85 / 100) return@firstOrNull false
        if (b.height > screen.height / 6) return@firstOrNull false
        val kids = node.children.toList()
        if (kids.size != node.childCount || kids.any { !it.isClickable }) return@firstOrNull false
        val widths = kids.map { it.bounds.width }
        widths.min() * 10 >= widths.max() * 7
    }

/** Which cell of a full-width bottom bar with [cells] cells a tapped node is, or -1 if it isn't in one. */
internal fun UiNode.bottomBarCell(screen: com.darryl.unscroll.core.Bounds, cells: Int): Int {
    val b = bounds
    if (b.top < screen.top + screen.height * 85 / 100 || b.height > screen.height / 6) return -1
    if (b.width > screen.width / cells * 3 / 2) return -1
    return ((b.left + b.right) / 2 - screen.left) * cells / screen.width
}
