package io.github.sriharinmn.triage.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small-caps section label ("ACCOUNT") over a flat section; no cards. TalkBack can jump between them as headings. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  val locale = LocalConfiguration.current.locales[0]
  Text(
    text.uppercase(locale),
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
  )
}
