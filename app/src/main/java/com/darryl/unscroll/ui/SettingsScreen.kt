package com.darryl.unscroll.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darryl.unscroll.R
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.Toggle
import com.darryl.unscroll.service.ServiceStatus

private data class Rule(val toggle: Toggle, val title: String, val detail: String)
private data class AppSection(val name: String, val initial: String, val tint: Color, val rules: List<Rule>, val note: String? = null)

private val sections = listOf(
    AppSection("Instagram", "I", Color(0xFFE5537C), listOf(
        Rule(Toggle.IG_HIDE_FEED, "Hide feed", "Posts below the stories"),
        Rule(Toggle.IG_HIDE_HOME, "Hide home screen", "The whole Home tab, stories included"),
        Rule(Toggle.IG_HIDE_EXPLORE, "Hide Explore", "Grid and suggested search results"),
        Rule(Toggle.IG_BLOCK_REELS_TAB, "Block Reels tab", "One Reel from a DM or profile still plays"),
        Rule(Toggle.IG_BLOCK_REELS_ALL, "Block Reels entirely", "Including ones sent in DMs"),
        Rule(Toggle.IG_OPEN_MESSAGES, "Open in Messages", "Start in your inbox"),
    )),
    AppSection("YouTube", "Y", Color(0xFFFF5A4E), listOf(
        Rule(Toggle.YT_HIDE_HOME, "Hide Home", "Subscriptions and search stay"),
        Rule(Toggle.YT_BLOCK_SHORTS_TAB, "Block Shorts tab", "One Short from search or a link still plays"),
        Rule(Toggle.YT_BLOCK_SHORTS_ALL, "Block Shorts entirely", "Wherever they open"),
        Rule(Toggle.YT_HIDE_UP_NEXT, "Hide Up next", "Suggestions under the video"),
    )),
    AppSection("TikTok", "T", Color(0xFF3DD9D6), listOf(
        Rule(Toggle.TT_HIDE_FEEDS, "Hide feeds", "For You and Following. One video from a DM or link plays"),
    )),
    AppSection("X", "X", Color(0xFFB8BCC6), listOf(
        Rule(Toggle.X_HIDE_FOR_YOU, "Hide For you", "Following stays"),
    ), note = "Not verified on a device yet"),
    AppSection("LinkedIn", "in", Color(0xFF4C8DF6), listOf(
        Rule(Toggle.LI_HIDE_FEED, "Hide feed", "Search, messages and jobs stay"),
    )),
)

@Composable
fun SettingsScreen(
    settings: Settings,
    locked: Boolean,
    status: ServiceStatus.Snapshot,
    serviceEnabled: Boolean,
    onToggle: (Toggle, Boolean) -> Unit,
    onLock: (String) -> Unit,
    onUnlock: (String) -> Unit,
    lockError: String?,
    onOpenAccessibility: () -> Unit,
    guardActive: Boolean,
    onGuardChange: (Boolean) -> Unit,
) {
    val protecting = status.connected || serviceEnabled
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(protecting)
        StatusCard(protecting, status, onOpenAccessibility)
        LockCard(locked, onLock, onUnlock, lockError)

        SectionLabel("Apps")
        for (section in sections) AppCard(section, settings, locked, onToggle)

        SectionLabel("Extra friction")
        Panel {
            ToggleRow(
                title = "Make uninstalling harder",
                detail = "Device admin must be turned off before unscroll can be uninstalled",
                checked = guardActive,
                enabled = !locked,
                onChange = onGuardChange,
            )
        }

        Text(
            "No internet permission · nothing leaves this phone",
            color = Palette.Faint,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Header(protecting: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("unscroll", color = Palette.Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Text("Same apps. Less feed.", color = Palette.Muted, fontSize = 14.sp)
        }
        StatusPill(protecting)
    }
}

@Composable
private fun StatusPill(on: Boolean) {
    val color = if (on) Palette.Green else Palette.Red
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(if (on) "Active" else "Off", color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusCard(on: Boolean, status: ServiceStatus.Snapshot, onOpenAccessibility: () -> Unit) {
    if (!on) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Palette.Accent)
                .padding(20.dp),
        ) {
            Column {
                Text("Protection is off", color = Color(0xFF1A0F0B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Turn on unscroll in Accessibility settings. Greyed out? Settings › Apps › unscroll › ⋮ › Allow restricted settings.",
                    color = Color(0xCC1A0F0B),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Open Accessibility settings",
                    color = Palette.Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF14161C))
                        .clickable(onClick = onOpenAccessibility)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
        return
    }
    Panel {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_shield, Palette.Green)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Protecting 5 apps", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val now = status.packageName?.let { "${status.screen} · ${status.action}" } ?: "Waiting for an app"
                Text(now, color = Palette.Faint, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LockCard(locked: Boolean, onLock: (String) -> Unit, onUnlock: (String) -> Unit, error: String?) {
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (password.isNotEmpty()) {
            if (locked) onUnlock(password) else onLock(password)
            password = ""
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    val tint = if (locked) Palette.Amber else Palette.Muted
    Panel(highlight = if (locked) Palette.Amber else null) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open, tint)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (locked) "Settings locked" else "Lock settings", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (locked) "Enter the password to change anything" else "Freeze every switch behind a password",
                        color = Palette.Muted,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = Palette.Faint) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Coral,
                        unfocusedBorderColor = Palette.Border,
                        focusedContainerColor = Palette.SurfaceRaised,
                        unfocusedContainerColor = Palette.SurfaceRaised,
                        cursorColor = Palette.Coral,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                val enabled = password.isNotEmpty()
                Box(
                    Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) Palette.Accent else Brush.linearGradient(listOf(Palette.SurfaceRaised, Palette.SurfaceRaised)))
                        .clickable(enabled = enabled) { submit() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (locked) "Unlock" else "Lock",
                        color = if (enabled) Color(0xFF1A0F0B) else Palette.Faint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Palette.Red, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AppCard(section: AppSection, settings: Settings, locked: Boolean, onToggle: (Toggle, Boolean) -> Unit) {
    val onCount = section.rules.count { settings.isOn(it.toggle) }
    Panel {
        Column {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(section.tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(section.initial, color = section.tint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.name, color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    section.note?.let { Text(it, color = Palette.Amber, fontSize = 12.sp) }
                }
                Text("$onCount/${section.rules.size}", color = Palette.Faint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            section.rules.forEachIndexed { i, rule ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Palette.Border, thickness = 0.5.dp)
                val implied = settings.isImplied(rule.toggle)
                ToggleRow(
                    title = rule.title,
                    detail = if (implied) "Included by a stricter switch" else rule.detail,
                    checked = settings.isOn(rule.toggle),
                    enabled = !locked && !implied,
                    onChange = { onToggle(rule.toggle, it) },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val titleColor by animateColorAsState(if (enabled || checked) Palette.Ink else Palette.Muted, label = "title")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Palette.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.Coral,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Palette.Muted,
                uncheckedTrackColor = Palette.SurfaceRaised,
                uncheckedBorderColor = Palette.Border,
                disabledCheckedThumbColor = Palette.Ink.copy(alpha = 0.7f),
                disabledCheckedTrackColor = Palette.Coral.copy(alpha = 0.35f),
                disabledUncheckedThumbColor = Palette.Faint,
                disabledUncheckedTrackColor = Palette.Surface,
                disabledUncheckedBorderColor = Palette.Border,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Palette.Faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

@Composable
private fun IconBadge(icon: Int, tint: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Panel(highlight: Color? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .border(1.dp, highlight?.copy(alpha = 0.45f) ?: Palette.Border, RoundedCornerShape(22.dp)),
    ) { content() }
}
