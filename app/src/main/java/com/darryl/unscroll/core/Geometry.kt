package com.darryl.unscroll.core

object Geometry {
    /** A cover rectangle between two horizontal lines across [screen], or null if empty. */
    fun band(screen: Bounds, top: Int, bottom: Int): Bounds? {
        val t = top.coerceIn(screen.top, screen.bottom)
        val b = bottom.coerceIn(screen.top, screen.bottom)
        return if (b - t < MIN_HEIGHT) null else Bounds(screen.left, t, screen.right, b)
    }

    /** Ignore slivers: a cover thinner than this would only look like a glitch. */
    const val MIN_HEIGHT = 24
}
