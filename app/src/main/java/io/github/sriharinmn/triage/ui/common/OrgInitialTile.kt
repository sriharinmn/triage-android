package io.github.sriharinmn.triage.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** The first letter or digit of [name], uppercased, or "?" when it has neither. */
fun orgInitial(name: String): String = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/** The org's initial in a small rounded tile. Decoration only: the org's name is always next to it. */
@Composable
internal fun OrgInitialTile(name: String, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(32.dp)
      .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
      .clearAndSetSemantics {},
    contentAlignment = Alignment.Center,
  ) {
    Text(orgInitial(name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
  }
}
