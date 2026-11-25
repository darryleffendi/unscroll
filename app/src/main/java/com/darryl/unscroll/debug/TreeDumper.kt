package com.darryl.unscroll.debug

import com.darryl.unscroll.core.UiNode

/**
 * Serializes a node tree in uiautomator's XML shape so fixtures from either source
 * load the same way. Writes whether a node has text, never the text itself.
 */
object TreeDumper {
    fun dump(windows: List<Pair<String, UiNode>>, packageName: String): String = buildString {
        append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>")
        append("<hierarchy rotation=\"0\">")
        for ((type, root) in windows) {
            append("<window type=\"").append(escape(type)).append("\">")
            node(root, packageName, 0)
            append("</window>")
        }
        append("</hierarchy>")
    }

    private fun StringBuilder.node(n: UiNode, pkg: String, depth: Int) {
        if (depth > 80) return
        val b = n.bounds
        append("<node")
        attr("resource-id", n.viewId?.let { "$pkg:id/$it" } ?: "")
        attr("class", n.className ?: "")
        attr("package", pkg)
        attr("content-desc", n.contentDescription ?: "")
        attr("text", if (n.hasText) "<text>" else "")
        attr("clickable", n.isClickable.toString())
        attr("selected", n.isSelected.toString())
        attr("scrollable", n.isScrollable.toString())
        attr("visible-to-user", n.isVisible.toString())
        if (n.itemIndex >= 0) attr("item-index", n.itemIndex.toString())
        attr("bounds", "[${b.left},${b.top}][${b.right},${b.bottom}]")
        append(">")
        for (i in 0 until n.childCount) n.child(i)?.let { node(it, pkg, depth + 1) }
        append("</node>")
    }

    private fun StringBuilder.attr(name: String, value: String) {
        append(' ').append(name).append("=\"").append(escape(value)).append('"')
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("\n", "&#10;")
}
