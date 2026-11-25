package com.darryl.unscroll.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.UiNode

class AccessibilityUiNode(private val info: AccessibilityNodeInfo) : UiNode {
    override val viewId: String? by lazy { info.viewIdResourceName?.substringAfter(":id/") }
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val className: String? get() = info.className?.toString()
    override val hasText: Boolean get() = !info.text.isNullOrEmpty()
    override val text: String? get() = info.text?.toString()
    override val bounds: Bounds by lazy {
        val r = Rect()
        info.getBoundsInScreen(r)
        Bounds(r.left, r.top, r.right, r.bottom)
    }
    override val isClickable: Boolean get() = info.isClickable
    override val isSelected: Boolean get() = info.isSelected
    override val isScrollable: Boolean get() = info.isScrollable
    override val isVisible: Boolean get() = info.isVisibleToUser
    override val itemIndex: Int get() = info.collectionItemInfo?.let { maxOf(it.rowIndex, it.columnIndex) } ?: -1
    override val childCount: Int get() = info.childCount
    override fun child(index: Int): UiNode? = info.getChild(index)?.let(::AccessibilityUiNode)

    override fun click(): Boolean {
        var node: AccessibilityNodeInfo? = info
        repeat(4) {
            val current = node ?: return false
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node = current.parent
        }
        return false
    }
}
