package com.darryl.unscroll

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.darryl.unscroll.admin.UninstallGuard
import com.darryl.unscroll.data.SettingsRepository
import com.darryl.unscroll.debug.CrashLog
import com.darryl.unscroll.data.StoredState
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.service.ProtectionMonitor
import com.darryl.unscroll.service.ServiceStatus
import com.darryl.unscroll.ui.SettingsScreen
import com.darryl.unscroll.ui.UnscrollTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Whether the service is switched on in Accessibility settings, refreshed on resume. */
    private var serviceEnabled by mutableStateOf(false)
    private var guardActive by mutableStateOf(false)
    private var lastError by mutableStateOf<String?>(null)
    private var batteryUnrestricted by mutableStateOf(true)

    override fun onResume() {
        super.onResume()
        serviceEnabled = ProtectionMonitor.isEnabled(this)
        guardActive = UninstallGuard.isActive(this)
        lastError = CrashLog.read(this)
        batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * Hide or show this task's recents card. Hidden, there's nothing to swipe away (a
     * force-stop on ColorOS/MIUI); shown, the card can be locked against "Clear all".
     */
    private fun applyRecentsVisibility(hide: Boolean) {
        getSystemService(ActivityManager::class.java).appTasks.forEach { it.setExcludeFromRecents(hide) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark UI: light status/navigation bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Needed for the "Protection is not running" notice.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        val repo = SettingsRepository(applicationContext)
        setContent {
            UnscrollTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val stored by repo.state.collectAsState(initial = StoredState(Settings.DEFAULT, null))
                    val hideFromRecents = stored.settings.isOn(Toggle.HIDE_FROM_RECENTS)
                    LaunchedEffect(hideFromRecents) { applyRecentsVisibility(hideFromRecents) }
                    val status by ServiceStatus.snapshot.collectAsState()
                    val scope = rememberCoroutineScope()
                    var lockError by remember { mutableStateOf<String?>(null) }
                    SettingsScreen(
                        settings = stored.settings,
                        locked = stored.locked,
                        status = status,
                        serviceEnabled = serviceEnabled,
                        onToggle = { t, on -> scope.launch { repo.setToggle(t, on) } },
                        onLock = { pw -> lockError = null; scope.launch { withContext(Dispatchers.Default) { repo.lock(pw) } } },
                        onUnlock = { pw ->
                            val secret = stored.lock
                            if (secret != null) scope.launch {
                                val ok = withContext(Dispatchers.Default) { repo.unlock(pw, secret) }
                                lockError = if (ok) null else "Wrong password"
                            }
                        },
                        lockError = lockError,
                        onOpenAccessibility = { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        lastError = lastError,
                        batteryUnrestricted = batteryUnrestricted,
                        onAllowBackground = {
                            startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                            )
                        },
                        onOpenAppSettings = {
                            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        },
                        onClearError = { CrashLog.clear(this); lastError = null },
                        guardActive = guardActive,
                        onGuardChange = { on ->
                            if (on) startActivity(UninstallGuard.enableIntent(this))
                            else { UninstallGuard.deactivate(this); guardActive = false }
                        },
                    )
                }
            }
        }
    }
}
