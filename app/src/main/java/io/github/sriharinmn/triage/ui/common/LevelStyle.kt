package io.github.sriharinmn.triage.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.theme.semanticColors

/** The shape of a level's glyph, so a level is never told by color alone. */
enum class LevelShape {
  Square,
  Triangle,
  Dot,
  Ring,
}

/** ■ error and fatal, ▲ warning, ● info, ○ debug or a level Sentry adds later. */
val IssueLevel.shape: LevelShape
  get() =
    when (this) {
      IssueLevel.Fatal,
      IssueLevel.Error -> LevelShape.Square
      IssueLevel.Warning -> LevelShape.Triangle
      IssueLevel.Info -> LevelShape.Dot
      IssueLevel.Debug,
      IssueLevel.Unknown -> LevelShape.Ring
    }

/** The glyph's color. Deliberately not Sentry's purple; info is cyan so it never reads as the brand blue. */
@Composable
@ReadOnlyComposable
internal fun levelColor(level: IssueLevel): Color {
  val semantic = MaterialTheme.semanticColors
  return when (level) {
    IssueLevel.Fatal,
    IssueLevel.Error -> semantic.levelError
    IssueLevel.Warning -> semantic.levelWarning
    IssueLevel.Info -> semantic.levelInfo
    IssueLevel.Debug,
    IssueLevel.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
  }
}

/** The level as a word, so it is never told by colour alone. */
@StringRes
internal fun IssueLevel.labelRes(): Int =
  when (this) {
    IssueLevel.Fatal -> R.string.level_fatal
    IssueLevel.Error -> R.string.level_error
    IssueLevel.Warning -> R.string.level_warning
    IssueLevel.Info -> R.string.level_info
    IssueLevel.Debug -> R.string.level_debug
    IssueLevel.Unknown -> R.string.level_unknown
  }

/**
 * The level's glyph, named for TalkBack. Next to the level's word, pass `Modifier.clearAndSetSemantics {}` so the level
 * isn't read twice; rows that read as one sentence clear it anyway.
 */
@Composable
internal fun LevelGlyph(level: IssueLevel, modifier: Modifier = Modifier) {
  val color = levelColor(level)
  val name = stringResource(level.labelRes())
  Canvas(modifier.size(GLYPH_SIZE).semantics { contentDescription = name }) {
    val w = size.width
    val h = size.height
    when (level.shape) {
      LevelShape.Square -> drawRoundRect(color = color, cornerRadius = CornerRadius(2.dp.toPx()))
      LevelShape.Triangle -> {
        val triangle =
          Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
          }
        drawPath(triangle, color = color)
      }
      LevelShape.Dot -> drawCircle(color = color)
      LevelShape.Ring -> {
        val stroke = 1.5.dp.toPx()
        drawCircle(color = color, radius = (minOf(w, h) - stroke) / 2f, style = Stroke(width = stroke))
      }
    }
  }
}

private val GLYPH_SIZE = 10.dp
