package io.github.sriharinmn.triage.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A small text tag such as "Unhandled" or "Resolved": a hairline box, no fill, so taps fall through to the row. */
@Composable
internal fun StatusLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
  )
}
