package com.minews1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF2F7D70)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFB6ECDF)
private val Secondary = Color(0xFF4B8FD1)
private val Tertiary = Color(0xFF3B78B5)
private val Background = Color(0xFFF3F4F6)
private val Surface = Color(0xFFFFFFFF)
private val OnSurfaceVariant = Color(0xFF66666E)
private val OutlineVariant = Color(0xFFE2E4E8)

private val MinewColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Background,
    surface = Surface,
    onSurfaceVariant = OnSurfaceVariant,
    outlineVariant = OutlineVariant,
)

@Composable
fun MinewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MinewColors, content = content)
}
