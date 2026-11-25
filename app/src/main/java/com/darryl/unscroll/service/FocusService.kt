package com.darryl.unscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
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
import com.darryl.unscroll.core.TapPrediction
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.core.UiNode
import com.darryl.unscroll.data.SettingsRepository
import com.darryl.unscroll.debug.CrashLog
import com.darryl.unscroll.debug.TreeDumper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    @Volatile private var settings: Settings = Settings.DEFAULT

    /**
     * Screen reads run here. Each node query waits on the app's UI thread, which stalls
     * mid-transition; on the main thread that would also stall the curtain and the
     * tap/Back predictions queued behind it.
     */
    private val worker = HandlerThread("unscroll-scan").apply { start() }
    private val workerHandler = Handler(worker.looper)
    /** Bumped by every prediction: a scan that started before it is stale when it lands. */
    private val epoch = AtomicLong()
    private val evaluateScheduled = AtomicBoolean(false)

    @Volatile private var foregroundPkg: String? = null
    private var foregroundSinceMs = 0L
    private val windowClasses = ConcurrentHashMap<String, String>()
    @Volatile private var viewerIndex: Int? = null
    private var lastExitMs = 0L
    private var unknownSinceMs = -1L
    private var lastLogged = ""
    @Volatile private var lastScreenBounds: Bounds? = null
    private var lastNodeCount = -1
    private var lastScrollBackMs = 0L
    private var lastTapMs = 0L
    private var lastQuickCheckMs = 0L
    private var lastScreen: String? = null
    /** Last cover per app screen, for showing it the moment its tab is tapped. */
    private val lastCovers = HashMap<String, Action.Cover>()
    private var dumpReceiver: BroadcastReceiver? = null
    private var setReceiverRef: BroadcastReceiver? = null

    private val evaluateTask = Runnable {
        evaluateScheduled.set(false)
        safeEvaluate()
    }

    /** Re-checks once a second while one of the apps is in front, to catch leaving it without events. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (foregroundPkg == null) return
            safeEvaluate()
            workerHandler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            curtain = CurtainController(this)
            selectors = Selectors.parse(assets.open("selectors.json").bufferedReader().use { it.readText() })
            SettingsRepository(this).state.onEach {
                settings = it.settings
                lastCovers.clear()
                schedule(0)
            }.launchIn(scope)
            ServiceStatus.connected(true)
            ProtectionMonitor.cancel(this)
            Log.i(TAG, "service connected")
            if (BuildConfig.DEBUG) registerDumpReceiver()
        } catch (e: Throwable) {
            Log.e(TAG, "startup failed", e)
            CrashLog.record(this, "service startup", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::curtain.isInitialized || !::selectors.isInitialized) return
        try {
            handleEvent(event)
        } catch (e: RuntimeException) {
            // OEM builds can throw from node access (e.g. SecurityException); never crash the service.
            Log.w(TAG, "event failed", e)
            CrashLog.record(this, "event handling", e)
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (BuildConfig.DEBUG && TRACE_EVENTS) {
            val src = event.source
            Log.d(TAG, "event ${AccessibilityEvent.eventTypeToString(event.eventType)} cls=${event.className} " +
                "src=${src?.viewIdResourceName?.substringAfter("/")} sel=${src?.isSelected} changes=${event.contentChangeTypes} " +
                "desc=${event.contentDescription} text=${event.text} win=${event.windowId}")
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> if (pkg != null && pkg == foregroundPkg) predictTap(pkg, event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (pkg != null && pkg == foregroundPkg) predictChange(pkg, event)
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
        workerHandler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        scope.cancel()
        if (::curtain.isInitialized) curtain.detach()
        dumpReceiver?.let(::unregisterReceiver)
        dumpReceiver = null
        setReceiverRef?.let(::unregisterReceiver)
        setReceiverRef = null
        ServiceStatus.connected(false)
        super.onDestroy()
    }

    /**
     * React to a tab tap before the app redraws: drop the curtain when leaving a covered
     * screen, or put back the last cover of the screen being opened. The scan that follows
     * corrects any wrong guess within a frame or two.
     */
    private fun predictTap(pkg: String, event: AccessibilityEvent) {
        val source = event.source ?: return
        val screen = lastScreenBounds ?: return
        lastTapMs = SystemClock.uptimeMillis()
        epoch.incrementAndGet()
        val p = rules[pkg]?.predictTap(AccessibilityUiNode(source), screen, selectors.forPackage(pkg))
        if (BuildConfig.DEBUG) Log.d(TAG, "tap ${source.viewIdResourceName} -> $p")
        when (p) {
            TapPrediction.Leaves -> curtain.hide()
            is TapPrediction.Arrives -> lastCovers["$pkg/${p.screen}"]?.let { curtain.show(it.bounds, it.message) }
            else -> {}
        }
    }

    /**
     * Back between main tabs sends no click, only content changes on the tabs. Only
     * watched on screens the rules ask for, and not right after a tap (already predicted).
     */
    private fun predictChange(pkg: String, event: AccessibilityEvent) {
        val appRules = rules[pkg] ?: return
        if (!appRules.watchesChangesOn(lastScreen)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTapMs < TAP_SETTLE_MS || now - lastQuickCheckMs < QUICK_CHECK_INTERVAL_MS) return
        lastQuickCheckMs = now
        workerHandler.postAtFrontOfQueue {
            val root = rootInActiveWindow ?: return@postAtFrontOfQueue
            if (root.packageName?.toString() != pkg) return@postAtFrontOfQueue
            val p = appRules.quickCheck(AccessibilityUiNode(root), pkg, selectors.forPackage(pkg))
            if (p == TapPrediction.Unknown) return@postAtFrontOfQueue
            if (BuildConfig.DEBUG) Log.d(TAG, "quick check -> $p")
            epoch.incrementAndGet()
            handler.post {
                if (p is TapPrediction.Arrives) lastCovers["$pkg/${p.screen}"]?.let { curtain.show(it.bounds, it.message) }
                else if (p == TapPrediction.Leaves) curtain.hide()
                schedule(0)
            }
        }
    }

    /** Throttle, not debounce: a playing video sends content changes nonstop. */
    private fun schedule(delayMs: Long) {
        if (delayMs == 0L) {
            workerHandler.removeCallbacks(evaluateTask)
            evaluateScheduled.set(true)
            workerHandler.post(evaluateTask)
        } else if (evaluateScheduled.compareAndSet(false, true)) {
            workerHandler.postDelayed(evaluateTask, delayMs)
        }
    }

    private fun safeEvaluate() {
        if (!::curtain.isInitialized || !::selectors.isInitialized) return
        try {
            val start = SystemClock.elapsedRealtimeNanos()
            evaluate()
            if (BuildConfig.DEBUG) Log.d(TAG, "evaluate took ${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000} ms screen=${ServiceStatus.snapshot.value.screen} nodes=$lastNodeCount")
        } catch (e: RuntimeException) {
            Log.w(TAG, "evaluate failed", e)
            CrashLog.record(this, "screen check", e)
            handler.post { curtain.hide() }
        }
    }

    /** Runs on the worker; the result is applied on the main thread. */
    private fun evaluate() {
        val startEpoch = epoch.get()
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
            workerHandler.removeCallbacks(watchdog)
            workerHandler.postDelayed(watchdog, WATCHDOG_MS)
        }
        lastScreenBounds = active.bounds
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
        if (BuildConfig.DEBUG) lastNodeCount = ctx.nodesIfBuilt()
        handler.post {
            // A prediction since this scan began knows better; rescan instead.
            if (epoch.get() != startEpoch) schedule(0) else apply(decision, active, SystemClock.uptimeMillis())
        }
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
                if (action.scrollBack == null) lastCovers["${active.pkg}/${decision.screen}"] = action
                else if (now - lastScrollBackMs > SCROLL_BACK_INTERVAL_MS) {
                    lastScrollBackMs = now
                    workerHandler.post { action.scrollBack.scrollBackward() }
                    schedule(SCROLL_BACK_INTERVAL_MS)
                }
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
                    workerHandler.post {
                        if (!action.node.click() && action.label.startsWith("leave")) performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }
        }
        report(active.pkg, decision)
    }

    /** Worker side: drop per-visit state; the curtain itself is touched on the main thread. */
    private fun leaveForeground() {
        foregroundPkg?.let { rules[it]?.reset() }
        foregroundPkg = null
        workerHandler.removeCallbacks(watchdog)
        handler.post {
            if (::curtain.isInitialized) curtain.hide()
            report(null, null)
        }
    }

    private fun report(pkg: String?, decision: Decision?) {
        lastScreen = decision?.screen
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
        val root = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) window.getRoot(AccessibilityUiNode.PREFETCH) else window.root)
            ?: return null
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
        const val THROTTLE_MS = 40L
        /** Debug: log every event (noisy). */
        const val TRACE_EVENTS = false
        const val WATCHDOG_MS = 1_000L
        const val UNKNOWN_GRACE_MS = 300L
        const val EXIT_COOLDOWN_MS = 800L
        const val SCROLL_BACK_INTERVAL_MS = 250L
        const val TAP_SETTLE_MS = 500L
        const val QUICK_CHECK_INTERVAL_MS = 60L
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
