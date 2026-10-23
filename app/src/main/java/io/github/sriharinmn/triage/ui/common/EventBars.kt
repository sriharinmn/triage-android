package io.github.sriharinmn.triage.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Event counts as bars, oldest first, scaled to the busiest bucket. Empty buckets get a hairline so the shape still
 * reads as a timeline. With no counts at all it draws nothing and says nothing, rather than announcing "0 events".
 */
@Composable
internal fun EventBars(counts: List<Long>, color: Color, description: String, modifier: Modifier = Modifier) {
  if (counts.isEmpty()) {
    Spacer(modifier)
    return
  }
  Canvas(modifier.semantics { contentDescription = description }) {
    val peak = counts.max().coerceAtLeast(1)
    val slot = size.width / counts.size
    val barWidth = (slot * 0.7f).coerceAtLeast(1f)
    val minBarHeight = 1.dp.toPx()
    counts.forEachIndexed { index, count ->
      val barHeight = (count.toFloat() / peak * size.height).coerceAtLeast(minBarHeight)
      drawRect(
        color = color,
        topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - barHeight),
        size = Size(barWidth, barHeight),
      )
    }
  }
}
