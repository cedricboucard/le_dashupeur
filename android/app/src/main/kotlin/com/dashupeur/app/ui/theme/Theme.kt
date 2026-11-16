package com.dashupeur.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DashupeurDarkColorScheme = darkColorScheme(
  primary = Primary,
  onPrimary = OnPrimary,
  primaryContainer = PrimaryContainer,
  onPrimaryContainer = OnPrimaryContainer,
  secondary = Secondary,
  onSecondary = OnSecondary,
  secondaryContainer = SecondaryContainer,
  onSecondaryContainer = OnSecondaryContainer,
  tertiary = Tertiary,
  onTertiary = OnTertiary,
  tertiaryContainer = TertiaryContainer,
  background = SurfaceBg,
  onBackground = OnSurface,
  surface = SurfaceBg,
  onSurface = OnSurface,
  surfaceVariant = SurfaceContainerHighest,
  onSurfaceVariant = OnSurfaceVariant,
  surfaceContainerLowest = SurfaceContainerLowest,
  surfaceContainerLow = SurfaceContainerLow,
  surfaceContainer = SurfaceContainer,
  surfaceContainerHigh = SurfaceContainerHigh,
  surfaceContainerHighest = SurfaceContainerHighest,
  outline = Outline,
  outlineVariant = OutlineVariant,
  error = ErrorColor,
  errorContainer = ErrorContainer,
  onErrorContainer = OnErrorContainer,
)

@Composable
fun LeDashupeurTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DashupeurDarkColorScheme,
    typography = Typography,
    content = content,
  )
}
