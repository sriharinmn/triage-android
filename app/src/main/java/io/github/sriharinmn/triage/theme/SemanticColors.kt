package io.github.sriharinmn.triage.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors with a fixed meaning (levels, statuses, swipe actions), the same whichever scheme is in use. */
@Immutable
data class SemanticColors(
  val levelError: Color,
  val levelWarning: Color,
  val levelInfo: Color,
  val statusEscalating: Color,
  val statusRegressed: Color,
  val statusNew: Color,
  val resolveSwipe: Color,
  val onResolveSwipe: Color,
  val archiveSwipe: Color,
  val onArchiveSwipe: Color,
)

private fun SemanticHex.toColors() =
  SemanticColors(
    levelError = Color(levelError),
    levelWarning = Color(levelWarning),
    levelInfo = Color(levelInfo),
    statusEscalating = Color(statusEscalating),
    statusRegressed = Color(statusRegressed),
    statusNew = Color(statusNew),
    resolveSwipe = Color(resolveSwipe),
    onResolveSwipe = Color(onResolveSwipe),
    archiveSwipe = Color(archiveSwipe),
    onArchiveSwipe = Color(onArchiveSwipe),
  )

internal val LightSemanticColors = SemanticPalette.light.toColors()

internal val DarkSemanticColors = SemanticPalette.dark.toColors()

/** Light by default, so a composable shown without [TriageTheme] (in a test) still has colors. */
internal val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** The current theme's semantic colors, next to `MaterialTheme.colorScheme`. */
val MaterialTheme.semanticColors: SemanticColors
  @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
