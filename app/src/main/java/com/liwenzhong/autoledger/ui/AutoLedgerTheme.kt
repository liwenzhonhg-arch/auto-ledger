package com.liwenzhong.autoledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CrowInk = Color(0xFF121318)
val CrowIvory = Color(0xFFF4F0E7)
val CrowTeal = Color(0xFF2F6F68)
val CrowAmber = Color(0xFFD39B45)

private val LightColors = lightColorScheme(
    primary = CrowTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E7E2),
    onPrimaryContainer = Color(0xFF143D38),
    secondary = Color(0xFF9A6A24),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4DDB7),
    onSecondaryContainer = Color(0xFF3E2A08),
    background = CrowIvory,
    onBackground = CrowInk,
    surface = Color(0xFFFFFCF6),
    onSurface = CrowInk,
    surfaceDim = Color(0xFFDED8CE),
    surfaceBright = Color(0xFFFFFCF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F4EC),
    surfaceContainer = Color(0xFFF0EAE0),
    surfaceContainerHigh = Color(0xFFEAE4D9),
    surfaceContainerHighest = Color(0xFFE4DED4),
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Color(0xFF5F625F),
    outline = Color(0xFF777C78),
    outlineVariant = Color(0xFFD0CAC0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86C5BA),
    onPrimary = Color(0xFF073732),
    primaryContainer = Color(0xFF214B46),
    onPrimaryContainer = Color(0xFFCAF1E9),
    secondary = Color(0xFFE4B76C),
    onSecondary = Color(0xFF402D08),
    secondaryContainer = Color(0xFF5B431A),
    onSecondaryContainer = Color(0xFFFFDEA5),
    background = Color(0xFF0E1114),
    onBackground = Color(0xFFF1ECE3),
    surface = Color(0xFF171B1F),
    onSurface = Color(0xFFF1ECE3),
    surfaceDim = Color(0xFF0E1114),
    surfaceBright = Color(0xFF343A3E),
    surfaceContainerLowest = Color(0xFF090C0E),
    surfaceContainerLow = Color(0xFF13171B),
    surfaceContainer = Color(0xFF181D21),
    surfaceContainerHigh = Color(0xFF22272C),
    surfaceContainerHighest = Color(0xFF2D3337),
    surfaceVariant = Color(0xFF22282D),
    onSurfaceVariant = Color(0xFFBEC5C1),
    outline = Color(0xFF89918D),
    outlineVariant = Color(0xFF353C40),
)

@Composable
fun AutoLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
