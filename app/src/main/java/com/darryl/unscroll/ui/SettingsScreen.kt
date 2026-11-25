package com.darryl.unscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.service.ServiceStatus

private data class AppSection(val name: String, val toggles: List<Pair<Toggle, String>>, val note: String? = null)

private val sections = listOf(
    AppSection("Instagram", listOf(
        Toggle.IG_HIDE_FEED to "Hide feed — posts below the stories",
        Toggle.IG_HIDE_HOME to "Hide home screen — the whole Home tab",
        Toggle.IG_HIDE_EXPLORE to "Hide Explore — grid and suggested search results",
        Toggle.IG_BLOCK_REELS_TAB to "Block Reels tab — one Reel from a DM or profile still plays",
        Toggle.IG_BLOCK_REELS_ALL to "Block Reels entirely — including ones sent in DMs",
        Toggle.IG_OPEN_MESSAGES to "Open in Messages — start in your inbox",
    )),
    AppSection("YouTube", listOf(
        Toggle.YT_HIDE_HOME to "Hide Home — Subscriptions and search stay",
        Toggle.YT_BLOCK_SHORTS_TAB to "Block Shorts tab — one Short from search or a link still plays",
        Toggle.YT_BLOCK_SHORTS_ALL to "Block Shorts entirely",
        Toggle.YT_HIDE_UP_NEXT to "Hide Up next — suggestions under the video",
    )),
    AppSection("TikTok", listOf(
        Toggle.TT_HIDE_FEEDS to "Hide feeds — For You and Following; one video from a DM or link plays",
    )),
    AppSection("X", listOf(
        Toggle.X_HIDE_FOR_YOU to "Hide For you — Following stays",
    ), note = "Not verified on a device yet."),
    AppSection("LinkedIn", listOf(
        Toggle.LI_HIDE_FEED to "Hide feed — search, messages and jobs stay",
    )),
)

@Composable
fun SettingsScreen(
    settings: Settings,
    locked: Boolean,
    status: ServiceStatus.Snapshot,
    onToggle: (Toggle, Boolean) -> Unit,
    onLock: (String) -> Unit,
    onUnlock: (String) -> Unit,
    lockError: String?,
    onOpenAccessibility: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("unscroll", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        StatusCard(status, onOpenAccessibility)
        LockCard(locked, onLock, onUnlock, lockError)

        for (section in sections) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(section.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    section.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    for ((toggle, label) in section.toggles) {
                        val implied = settings.isImplied(toggle)
                        ToggleRow(
                            label = label,
                            checked = settings.isOn(toggle),
                            enabled = !locked && !implied,
                            hint = if (implied) "Included by a stricter switch" else null,
                            onChange = { onToggle(toggle, it) },
                        )
                    }
                }
            }
        }
        Text(
            "Now: " + (status.packageName?.let { "${it.substringAfterLast('.')} · ${status.screen} · ${status.action}" } ?: "—"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(status: ServiceStatus.Snapshot, onOpenAccessibility: () -> Unit) {
    val on = status.connected
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (on) "Protection is on" else "Protection is off", style = MaterialTheme.typography.titleMedium)
            if (!on) {
                Text(
                    "Turn on unscroll in Accessibility settings. If the switch is greyed out: Settings › Apps › unscroll › ⋮ › Allow restricted settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenAccessibility) { Text("Open Accessibility settings") }
            }
        }
    }
}

@Composable
private fun LockCard(locked: Boolean, onLock: (String) -> Unit, onUnlock: (String) -> Unit, error: String?) {
    var password by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(if (locked) "Settings are locked" else "Lock settings", style = MaterialTheme.typography.titleMedium)
            Text(
                if (locked) "Enter the password to change anything." else "Locking freezes every switch until the same password is entered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val button = @Composable { Text(if (locked) "Unlock" else "Lock") }
                val click = {
                    if (password.isNotEmpty()) {
                        if (locked) onUnlock(password) else onLock(password)
                        password = ""
                    }
                }
                if (locked) OutlinedButton(onClick = click) { button() } else Button(onClick = click) { button() }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, hint: String?, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
