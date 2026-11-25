package com.darryl.unscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.darryl.unscroll.BuildConfig
import com.darryl.unscroll.apps.RulesRegistry
import com.darryl.unscroll.core.Action
import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.Selectors
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.core.UiNode
import com.darryl.unscroll.data.SettingsRepository
import com.darryl.unscroll.debug.TreeDumper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

/**
 * One loop per (throttled) event: find the active app window, classify it with that
 * app's rules, then cover, exit, tap or allow. Anything it can't recognize, and any
 * window that isn't one of the five apps (system dialogs, permission prompts, the
 * launcher), makes the curtain step aside.
 */
class FocusService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rules: Map<String, AppRules> = RulesRegistry.create()
    private lateinit var curtain: CurtainController
    private lateinit var selectors: Selectors
    private var settings: Settings = Settings.DEFAULT

    private var foregroundPkg: String? = null
    private var foregroundSinceMs = 0L
    private val windowClasses = HashMap<String, String>()
    private var viewerIndex: Int? = null
    private var lastExitMs = 0L
    private var unknownSinceMs = -1L
    private var lastLogged = ""
    private var evaluateScheduled = false
    private var dumpReceiver: BroadcastReceiver? = null
    private var setReceiverRef: BroadcastReceiver? = null

    private val evaluateTask = Runnable {
        evaluateScheduled = false
        evaluate()
    }

    /** Re-checks once a second while one of the apps is in front, to catch leaving it without events. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (foregroundPkg == null) return
            evaluate()
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        curtain = CurtainController(this)
        selectors = Selectors.parse(assets.open("selectors.json").bufferedReader().use { it.readText() })
        SettingsRepository(this).state.onEach {
            settings = it.settings
            schedule(0)
        }.launchIn(scope)
        ServiceStatus.connected(true)
        ProtectionMonitor.cancel(this)
        Log.i(TAG, "service connected")
        if (BuildConfig.DEBUG) registerDumpReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> if (pkg in TARGET_PACKAGES) {
                event.className?.let { windowClasses[pkg!!] = it.toString() }
                viewerIndex = null
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (event.fromIndex >= 0 && event.itemCount > 0) {
                // A full-screen pager (Reels, Shorts, TikTok) reports its current page here.
                val source = event.source
                if (source != null) {
                    val r = Rect()
                    source.getBoundsInScreen(r)
                    if (r.height() > resources.displayMetrics.heightPixels * 2 / 3) viewerIndex = event.fromIndex
                }
            }
        }
        schedule(if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 0 else THROTTLE_MS)
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::curtain.isInitialized) curtain.applyTheme(newConfig)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Turned off in Accessibility settings (or by the system): say so.
        Log.i(TAG, "service unbound")
        ProtectionMonitor.notifyOff(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (::curtain.isInitialized) curtain.hide()
        dumpReceiver?.let(::unregisterReceiver)
        dumpReceiver = null
        setReceiverRef?.let(::unregisterReceiver)
        setReceiverRef = null
        ServiceStatus.connected(false)
        super.onDestroy()
    }

    /** Throttle, not debounce: a playing video sends content changes nonstop. */
    private fun schedule(delayMs: Long) {
        if (delayMs == 0L) {
            handler.removeCallbacks(evaluateTask)
            evaluateScheduled = true
            handler.post(evaluateTask)
        } else if (!evaluateScheduled) {
            evaluateScheduled = true
            handler.postDelayed(evaluateTask, delayMs)
        }
    }

    private fun evaluate() {
        val now = SystemClock.uptimeMillis()
        val active = activeAppWindow()
        val appRules = active?.let { rules[it.pkg] }
        if (active == null || appRules == null) {
            leaveForeground()
            return
        }
        if (active.pkg != foregroundPkg) {
            foregroundPkg?.let { rules[it]?.reset() }
            foregroundPkg = active.pkg
            foregroundSinceMs = now
            viewerIndex = null
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, WATCHDOG_MS)
        }
        val ctx = ScreenContext(
            packageName = active.pkg,
            root = active.root,
            screen = active.bounds,
            settings = settings,
            selectors = selectors.forPackage(active.pkg),
            nowMs = now,
            windowClass = windowClasses[active.pkg],
            foregroundSinceMs = foregroundSinceMs,
            viewerIndex = viewerIndex,
            safeTop = maxOf(active.bounds.top, active.statusBarBottom),
        )
        val decision = try {
            appRules.decide(ctx)
        } catch (e: RuntimeException) {
            // Nodes can go stale mid-scan; never let that crash the service.
            Log.w(TAG, "rules failed for ${active.pkg}", e)
            Decision.unknown()
        }
        apply(decision, active, now)
    }

    private fun apply(decision: Decision, active: ActiveWindow, now: Long) {
        val action = decision.action
        if (decision.screen != Decision.UNKNOWN) unknownSinceMs = -1L
        when (action) {
            is Action.Cover -> {
                // Never cover the keyboard.
                val bottom = minOf(action.bounds.bottom, active.imeTop ?: Int.MAX_VALUE)
                if (bottom - action.bounds.top < 24) curtain.hide()
                else curtain.show(action.bounds.copy(bottom = bottom), action.message)
            }
            Action.Allow -> if (decision.screen == Decision.UNKNOWN && curtain.isShowing) {
                // Mid-transition screens are often unrecognized for a moment: keep the
                // curtain briefly, then step aside if it stays unknown.
                if (unknownSinceMs < 0) unknownSinceMs = now
                val left = UNKNOWN_GRACE_MS - (now - unknownSinceMs)
                if (left <= 0) curtain.hide() else handler.postDelayed({ schedule(0) }, left)
            } else {
                curtain.hide()
            }
            Action.Back -> {
                curtain.hide()
                if (now - lastExitMs > EXIT_COOLDOWN_MS) {
                    lastExitMs = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            is Action.Tap -> {
                curtain.hide()
                if (now - lastExitMs > EXIT_COOLDOWN_MS) {
                    lastExitMs = now
                    if (!action.node.click() && action.label.startsWith("leave")) performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
        report(active.pkg, decision)
    }

    private fun leaveForeground() {
        foregroundPkg?.let { rules[it]?.reset() }
        foregroundPkg = null
        handler.removeCallbacks(watchdog)
        if (::curtain.isInitialized) curtain.hide()
        report(null, null)
    }

    private fun report(pkg: String?, decision: Decision?) {
        val actionName = decision?.action?.let { a ->
            when (a) {
                is Action.Cover -> "COVER[${a.bounds.top},${a.bounds.bottom}]"
                Action.Allow -> "ALLOW"
                Action.Back -> "BACK"
                is Action.Tap -> "TAP(${a.label})"
            }
        }
        ServiceStatus.update(pkg, decision?.screen, actionName)
        val line = "pkg=$pkg screen=${decision?.screen} action=$actionName"
        if (line != lastLogged) {
            lastLogged = line
            Log.i(TAG, line)
        }
    }

    private class ActiveWindow(val pkg: String, val root: UiNode, val bounds: Bounds, val imeTop: Int?, val statusBarBottom: Int)

    /** The focused application window, if it belongs to one of the five apps. */
    private fun activeAppWindow(): ActiveWindow? {
        val all = try {
            windows
        } catch (e: RuntimeException) {
            return null
        }
        var imeTop: Int? = null
        var statusBarBottom = 0
        var app: AccessibilityWindowInfo? = null
        val r = Rect()
        for (w in all) {
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                w.getBoundsInScreen(r)
                imeTop = r.top
            }
            if (w.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                // The status bar: a thin system window along the top edge.
                w.getBoundsInScreen(r)
                if (r.top == 0 && r.height() in 1 until resources.displayMetrics.heightPixels / 8) statusBarBottom = maxOf(statusBarBottom, r.bottom)
            }
            if (app == null && (w.isActive || w.isFocused) && w.type == AccessibilityWindowInfo.TYPE_APPLICATION) app = w
        }
        val window = app ?: return null
        val root = window.root ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (pkg !in TARGET_PACKAGES) return null
        window.getBoundsInScreen(r)
        return ActiveWindow(pkg, AccessibilityUiNode(root), Bounds(r.left, r.top, r.right, r.bottom), imeTop, statusBarBottom)
    }

    /** Debug builds only: `adb shell am broadcast -a com.darryl.unscroll.DUMP --es name <file>`. */
    private fun registerDumpReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.getStringExtra("name") ?: "dump"
                val file = File(getExternalFilesDir("dumps"), "$name.xml")
                val (pkg, windows) = collectAppWindows()
                file.writeText(TreeDumper.dump(windows, pkg ?: "unknown"))
                Log.i(TAG, "dumped ${windows.size} windows of $pkg to ${file.absolutePath}")
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(ACTION_DUMP), ContextCompat.RECEIVER_EXPORTED)
        dumpReceiver = receiver
        // `adb shell am broadcast -a com.darryl.unscroll.SET --es key <toggle> --ez on <bool>` for emulator tests.
        val setReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val toggle = Toggle.byKey(intent.getStringExtra("key") ?: return) ?: return
                val on = intent.getBooleanExtra("on", true)
                scope.launch { SettingsRepository(this@FocusService).setToggle(toggle, on) }
            }
        }
        ContextCompat.registerReceiver(this, setReceiver, IntentFilter(ACTION_SET), ContextCompat.RECEIVER_EXPORTED)
        setReceiverRef = setReceiver
    }

    private fun collectAppWindows(): Pair<String?, List<Pair<String, UiNode>>> {
        var pkg: String? = null
        val result = mutableListOf<Pair<String, UiNode>>()
        for (window in windows) {
            val root = window.root ?: continue
            val rootPkg = root.packageName?.toString()
            if (rootPkg !in TARGET_PACKAGES) continue
            pkg = pkg ?: rootPkg
            result += windowTypeName(window.type) to AccessibilityUiNode(root)
        }
        return pkg to result
    }

    private fun windowTypeName(type: Int) = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "input_method"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "accessibility_overlay"
        else -> "other"
    }

    companion object {
        const val TAG = "Unscroll"
        const val ACTION_DUMP = "com.darryl.unscroll.DUMP"
        const val ACTION_SET = "com.darryl.unscroll.SET"
        const val THROTTLE_MS = 80L
        const val WATCHDOG_MS = 1_000L
        const val UNKNOWN_GRACE_MS = 600L
        const val EXIT_COOLDOWN_MS = 800L
        val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.twitter.android",
            "com.linkedin.android",
        )
    }
}
