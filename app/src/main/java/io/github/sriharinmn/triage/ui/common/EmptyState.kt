package io.github.sriharinmn.triage.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Triage's one pattern for empty, error and offline states: a small icon, a one-line headline, one line of help and at
 * most one action, centred. Used full-screen in the inbox and on the detail screen, and inside the detail's tabs.
 */
@Composable
internal fun EmptyState(
  @DrawableRes icon: Int,
  headline: String,
  help: String?,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: () -> Unit = {},
  actionTag: String? = null,
) {
  Column(
    modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      painterResource(icon),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(32.dp),
    )
    Text(
      headline,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { heading() },
    )
    help?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    if (actionLabel != null) {
      OutlinedButton(
        onClick = onAction,
        modifier = Modifier.padding(top = 8.dp).then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
      ) {
        Text(actionLabel)
      }
    }
  }
}
