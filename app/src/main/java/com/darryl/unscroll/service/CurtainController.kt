package com.darryl.unscroll.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.darryl.unscroll.R
import com.darryl.unscroll.core.Bounds

/**
 * The "Hidden by unscroll" curtain: a touchable accessibility overlay (no
 * SYSTEM_ALERT_WINDOW needed) that swallows touches, so the content under it can't
 * scroll or be tapped.
 */
class CurtainController(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: CurtainView? = null
    private var params: WindowManager.LayoutParams? = null
    private var shown: Bounds? = null

    val isShowing get() = view != null

    fun show(bounds: Bounds, message: String) {
        val current = view
        if (current != null && shown == bounds) {
            current.setMessage(message)
            return
        }
        val lp = params ?: layoutParams().also { params = it }
        lp.x = bounds.left
        lp.y = bounds.top
        lp.width = bounds.width
        lp.height = bounds.height
        if (current == null) {
            val v = CurtainView(service)
            v.setMessage(message)
            try {
                wm.addView(v, lp)
                view = v
            } catch (e: RuntimeException) {
                // The service can be mid-teardown; the next event retries.
                return
            }
        } else {
            current.setMessage(message)
            current.setCompact(bounds.height < COMPACT_HEIGHT)
            wm.updateViewLayout(current, lp)
        }
        view?.setCompact(bounds.height < COMPACT_HEIGHT)
        shown = bounds
    }

    fun applyTheme(config: Configuration) {
        view?.applyTheme(config)
    }

    fun hide() {
        val v = view ?: return
        view = null
        shown = null
        try {
            wm.removeViewImmediate(v)
        } catch (_: RuntimeException) {
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "unscroll curtain"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Without this the curtain glides after the content when it moves.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setCanPlayMoveAnimation(false)
    }

    companion object {
        /** Below this height only the title is shown. */
        const val COMPACT_HEIGHT = 420
    }
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class CurtainView(context: Context) : LinearLayout(context) {
    private val badge = ImageView(context).apply {
        setImageResource(R.drawable.ic_launcher_fg)
        layoutParams = LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(18) }
    }
    private val title = TextView(context).apply {
        text = context.getString(R.string.curtain_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        gravity = Gravity.CENTER
    }
    private val message = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.2f)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(16), dp(32), dp(16))
        addView(badge)
        addView(title)
        addView(message)
        applyTheme(context.resources.configuration)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        // Swallow every touch so the covered content can't scroll or be tapped.
        setOnTouchListener { _, _ -> true }
    }

    /** Follows the system light/dark setting, including a switch while the curtain is up. */
    fun applyTheme(config: Configuration) {
        val dark = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (dark) intArrayOf(0xFF111318.toInt(), 0xFF181B22.toInt()) else intArrayOf(0xFFF7F3EA.toInt(), 0xFFEFE9DC.toInt()),
        )
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (dark) 0xFF22252E.toInt() else 0xFF1F2430.toInt())
        }
        title.setTextColor(if (dark) 0xFFF2F1EE.toInt() else 0xFF1F2430.toInt())
        message.setTextColor(if (dark) 0xFF8B8F9A.toInt() else 0xFF5C6270.toInt())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(newConfig)
    }

    fun setMessage(text: String) {
        if (message.text != text) message.text = text
    }

    fun setCompact(compact: Boolean) {
        val vis = if (compact) View.GONE else View.VISIBLE
        badge.visibility = vis
        message.visibility = vis
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
