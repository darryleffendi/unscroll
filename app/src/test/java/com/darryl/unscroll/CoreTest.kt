package com.darryl.unscroll

import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.Geometry
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.SingleItemSession
import com.darryl.unscroll.core.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleItemSessionTest {
    private val T = 10_000L

    @Test fun `changes during warm-up refine the baseline instead of closing`() {
        val s = SingleItemSession(warmUpMs = 1_000)
        assertTrue(s.observe(null, null, 0))
        assertTrue(s.observe("loading", null, 300))
        assertTrue(s.observe("loaded", 0, 800))
        assertTrue(s.observe("loaded", 0, 5_000))
        assertFalse(s.observe("other", 0, 6_000))
    }

    @Test fun `first item is allowed and stays allowed`() {
        val s = SingleItemSession(warmUpMs = 0)
        assertTrue(s.observe("a", null, T))
        assertTrue(s.observe("a", null, T))
        assertTrue(s.observe("a", 3, T)) // index learned late
        assertTrue(s.observe("a", 3, T))
    }

    @Test fun `a new identity ends the session`() {
        val s = SingleItemSession(warmUpMs = 0)
        s.observe("a", 0, T)
        assertFalse(s.observe("b", 0, T))
    }

    @Test fun `a new pager index ends the session even with the same identity`() {
        val s = SingleItemSession(warmUpMs = 0)
        s.observe("same-author", 0, T)
        assertFalse(s.observe("same-author", 1, T))
    }

    @Test fun `reset starts over`() {
        val s = SingleItemSession(warmUpMs = 0)
        s.observe("a", 0, T)
        s.reset()
        assertTrue(s.observe("b", 5, T))
    }
}

class SettingsTest {
    @Test fun `defaults`() {
        val s = Settings.DEFAULT
        assertTrue(s.isOn(Toggle.IG_HIDE_FEED))
        assertFalse(s.isOn(Toggle.IG_HIDE_HOME))
        assertFalse(s.isOn(Toggle.IG_BLOCK_REELS_ALL))
        assertFalse(s.isOn(Toggle.IG_OPEN_MESSAGES))
    }

    @Test fun `hiding the home screen implies hiding the feed`() {
        val s = Settings.DEFAULT.with(Toggle.IG_HIDE_FEED, false).with(Toggle.IG_HIDE_HOME, true)
        assertTrue(s.isImplied(Toggle.IG_HIDE_FEED))
        assertTrue(s.isOn(Toggle.IG_HIDE_FEED))
        assertFalse(s.stored(Toggle.IG_HIDE_FEED))
    }

    @Test fun `blocking all reels implies blocking the tab, same for shorts`() {
        val s = Settings.DEFAULT.with(Toggle.IG_BLOCK_REELS_TAB, false).with(Toggle.IG_BLOCK_REELS_ALL, true)
            .with(Toggle.YT_BLOCK_SHORTS_TAB, false).with(Toggle.YT_BLOCK_SHORTS_ALL, true)
        assertTrue(s.isOn(Toggle.IG_BLOCK_REELS_TAB))
        assertTrue(s.isOn(Toggle.YT_BLOCK_SHORTS_TAB))
    }
}

class GeometryTest {
    private val screen = Bounds(0, 0, 1080, 2400)

    @Test fun `band is clamped to the screen`() {
        assertEquals(Bounds(0, 0, 1080, 2400), Geometry.band(screen, -50, 3000))
    }

    @Test fun `slivers are dropped`() {
        assertNull(Geometry.band(screen, 1000, 1010))
        assertNull(Geometry.band(screen, 1000, 900))
    }
}

class PasswordLockTest {
    @Test fun `right password verifies, wrong one doesn't`() {
        val secret = com.darryl.unscroll.data.PasswordLock.create("paper sheet 42")
        assertTrue(com.darryl.unscroll.data.PasswordLock.verify("paper sheet 42", secret))
        assertFalse(com.darryl.unscroll.data.PasswordLock.verify("paper sheet 43", secret))
        assertFalse(com.darryl.unscroll.data.PasswordLock.verify("", secret))
    }

    @Test fun `same password gets a different salt each time`() {
        val a = com.darryl.unscroll.data.PasswordLock.create("x")
        val b = com.darryl.unscroll.data.PasswordLock.create("x")
        assertTrue(a.salt != b.salt && a.hash != b.hash)
    }
}
