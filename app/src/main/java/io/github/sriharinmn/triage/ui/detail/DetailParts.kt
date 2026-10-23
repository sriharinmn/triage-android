package io.github.sriharinmn.triage.ui.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A section's heading, e.g. "Stack trace"; TalkBack can jump between headings. */
@Composable
internal fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
  )
}

/** Plain text inside a section. */
@Composable
internal fun SectionText(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
}

/** "Device   Google Pixel 8": a fixed-width label and its value, read by TalkBack as one item. */
@Composable
internal fun KeyValueRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics(mergeDescendants = true) {}) {
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(88.dp),
    )
    Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
  }
}
