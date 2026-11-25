package com.darryl.unscroll.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** unscroll's palette: near-black surfaces, warm off-white ink, a coral-to-amber accent. */
object Palette {
    val Background = Color(0xFF0B0C10)
    val Surface = Color(0xFF14161C)
    val SurfaceRaised = Color(0xFF1B1E26)
    val Border = Color(0xFF242733)
    val Ink = Color(0xFFF2F1EE)
    val Muted = Color(0xFF8B8F9A)
    val Faint = Color(0xFF5A5E69)
    val Coral = Color(0xFFFF7A59)
    val Amber = Color(0xFFFFB347)
    val Green = Color(0xFF4ADE80)
    val Red = Color(0xFFFF5D5D)
    val Accent = Brush.linearGradient(listOf(Coral, Amber))
}

@Composable
fun UnscrollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Coral,
            onPrimary = Color(0xFF1A0F0B),
            background = Palette.Background,
            onBackground = Palette.Ink,
            surface = Palette.Surface,
            onSurface = Palette.Ink,
            onSurfaceVariant = Palette.Muted,
            outline = Palette.Border,
            error = Palette.Red,
        ),
        content = content,
    )
}
