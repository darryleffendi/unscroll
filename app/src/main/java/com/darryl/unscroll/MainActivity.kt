package com.darryl.unscroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.darryl.unscroll.admin.UninstallGuard
import com.darryl.unscroll.data.SettingsRepository
import com.darryl.unscroll.data.StoredState
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.service.ProtectionMonitor
import com.darryl.unscroll.service.ServiceStatus
import com.darryl.unscroll.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Whether the service is switched on in Accessibility settings, refreshed on resume. */
    private var serviceEnabled by mutableStateOf(false)
    private var guardActive by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        serviceEnabled = ProtectionMonitor.isEnabled(this)
        guardActive = UninstallGuard.isActive(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Needed for the "Protection is not running" notice.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        val repo = SettingsRepository(applicationContext)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(primary = Color(0xFFE07A5F), background = Color(0xFF1B1F27), surface = Color(0xFF1B1F27))
            else lightColorScheme(primary = Color(0xFFC4573B), background = Color(0xFFF7F3EA), surface = Color(0xFFF7F3EA))
            MaterialTheme(colorScheme = colors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val stored by repo.state.collectAsState(initial = StoredState(Settings.DEFAULT, null))
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
