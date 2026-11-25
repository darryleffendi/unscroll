package com.darryl.unscroll.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.UiNode

class AccessibilityUiNode(private val info: AccessibilityNodeInfo) : UiNode {
    companion object {
        /**
         * Each uncached node fetch waits on the app's UI thread, which is busy exactly when
         * screens change. Prefetch breadth-first in full batches so one round trip brings
         * back the next layer of the tree, in the order the scan walks it.
         */
        val PREFETCH = AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST or
            AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE
    }

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
    override val isVisible: Boolean by lazy { info.isVisibleToUser }
    override val itemIndex: Int get() = info.collectionItemInfo?.let { maxOf(it.rowIndex, it.columnIndex) } ?: -1
    override val childCount: Int get() = info.childCount

    // getChild is a cross-process call; rules revisit children (tab rows, pagers), so keep them.
    private val kids = arrayOfNulls<AccessibilityUiNode>(info.childCount)
    private val fetched = BooleanArray(info.childCount)

    override fun child(index: Int): UiNode? {
        if (index !in kids.indices) return null
        if (!fetched[index]) {
            val child = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) info.getChild(index, PREFETCH) else info.getChild(index)
            kids[index] = child?.let(::AccessibilityUiNode)
            fetched[index] = true
        }
        return kids[index]
    }

    override fun scrollBackward(): Boolean = info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

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
