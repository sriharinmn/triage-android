package io.github.sriharinmn.triage.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.sriharinmn.triage.data.appearance.ThemeMode
import io.github.sriharinmn.triage.data.appearance.isDark

private val CobaltLightScheme = CobaltPalette.light.toColorScheme()

private val CobaltDarkScheme = CobaltPalette.dark.toColorScheme()

/**
 * Triage's theme: the cobalt scheme, light or dark per [themeMode], or Android 12+'s wallpaper colors when
 * [useWallpaperColors] is on. The semantic colors (levels, statuses, swipes) stay the same whichever scheme is in use.
 */
@Composable
fun TriageTheme(
  themeMode: ThemeMode = ThemeMode.System,
  useWallpaperColors: Boolean = false,
  content: @Composable () -> Unit,
) {
  val dark = themeMode.isDark(isSystemInDarkTheme())
  val colorScheme =
    when {
      // Wallpaper colors exist from Android 12; older phones keep cobalt (Settings hides the switch there).
      useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      dark -> CobaltDarkScheme
      else -> CobaltLightScheme
    }
  CompositionLocalProvider(LocalSemanticColors provides if (dark) DarkSemanticColors else LightSemanticColors) {
    MaterialTheme(colorScheme = colorScheme, typography = TriageTypography, content = content)
  }
}

private fun SchemeHex.toColorScheme(): ColorScheme =
  ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(surfaceTint),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    surfaceDim = Color(surfaceDim),
    primaryFixed = Color(primaryFixed),
    primaryFixedDim = Color(primaryFixedDim),
    onPrimaryFixed = Color(onPrimaryFixed),
    onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
    secondaryFixed = Color(secondaryFixed),
    secondaryFixedDim = Color(secondaryFixedDim),
    onSecondaryFixed = Color(onSecondaryFixed),
    onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
    tertiaryFixed = Color(tertiaryFixed),
    tertiaryFixedDim = Color(tertiaryFixedDim),
    onTertiaryFixed = Color(onTertiaryFixed),
    onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
  )
