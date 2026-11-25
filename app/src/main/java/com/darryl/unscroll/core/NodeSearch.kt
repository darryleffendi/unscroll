package com.darryl.unscroll.core

/**
 * Breadth-first search with a node budget, so a scan of a long feed or a DM list
 * stays cheap. Invisible subtrees are skipped.
 */
object NodeSearch {
    const val DEFAULT_BUDGET = 400

    fun find(root: UiNode, budget: Int = DEFAULT_BUDGET, predicate: (UiNode) -> Boolean): UiNode? {
        val queue = ArrayDeque<UiNode>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < budget) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisible) continue
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.child(i)?.let(queue::add)
        }
        return null
    }

    fun findAll(root: UiNode, budget: Int = DEFAULT_BUDGET, predicate: (UiNode) -> Boolean): List<UiNode> {
        val result = mutableListOf<UiNode>()
        val queue = ArrayDeque<UiNode>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < budget) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisible) continue
            if (predicate(node)) result.add(node)
            for (i in 0 until node.childCount) node.child(i)?.let(queue::add)
        }
        return result
    }
}
