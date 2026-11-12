package io.github.sriharinmn.triage.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.theme.semanticColors
import io.github.sriharinmn.triage.ui.inbox.labelRes

/** The mark before a status word: ● new, ↺ regressed, ↗ escalating. */
val IssueBadge.symbol: String
  get() =
    when (this) {
      IssueBadge.New -> "●"
      IssueBadge.Regressed -> "↺"
      IssueBadge.Escalating -> "↗"
    }

@Composable
@ReadOnlyComposable
internal fun statusColor(badge: IssueBadge): Color {
  val semantic = MaterialTheme.semanticColors
  return when (badge) {
    IssueBadge.New -> semantic.statusNew
    IssueBadge.Regressed -> semantic.statusRegressed
    IssueBadge.Escalating -> semantic.statusEscalating
  }
}

/** "↗ Escalating" in its status color. The mark is decoration; the word is what TalkBack reads and what colour-blind eyes see. */
@Composable
internal fun StatusWord(badge: IssueBadge, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelSmall) {
  val color = statusColor(badge)
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(badge.symbol, style = style, color = color, modifier = Modifier.clearAndSetSemantics {})
    Text(stringResource(badge.labelRes()), style = style.copy(fontWeight = FontWeight.SemiBold), color = color)
  }
}
