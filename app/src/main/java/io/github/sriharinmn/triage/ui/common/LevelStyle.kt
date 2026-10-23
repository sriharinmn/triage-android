package io.github.sriharinmn.triage.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.IssueLevel

/** Red-to-blue by severity, with lighter tones in dark mode; deliberately not Sentry's purple. */
@Composable
internal fun levelColor(level: IssueLevel): Color {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  return when (level) {
    IssueLevel.Fatal -> if (dark) Color(0xFFFF8A80) else Color(0xFF8E1B1B)
    IssueLevel.Error -> if (dark) Color(0xFFEF5350) else Color(0xFFD32F2F)
    IssueLevel.Warning -> if (dark) Color(0xFFFFB74D) else Color(0xFFB26A00)
    IssueLevel.Info -> if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    IssueLevel.Debug,
    IssueLevel.Unknown -> if (dark) Color(0xFFBDBDBD) else Color(0xFF757575)
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
