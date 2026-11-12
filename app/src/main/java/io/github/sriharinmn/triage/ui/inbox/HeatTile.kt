package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.theme.tabular
import io.github.sriharinmn.triage.ui.format.compactCount

/** At least this big; with large text the tile grows rather than clipping its count. */
private val TILE_SIZE = 50.dp

/** A row's heat tile: the issue's event count on a rounded square that is brighter the busier the issue is now. */
@Composable
internal fun HeatTile(count: Long, tier: HeatTier, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val (background, content) =
    when (tier) {
      HeatTier.Hot -> scheme.primary to scheme.onPrimary
      HeatTier.Warm -> scheme.primaryContainer to scheme.onPrimaryContainer
      HeatTier.Quiet -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
  val locale = LocalConfiguration.current.locales[0]
  Column(
    modifier
      .defaultMinSize(minWidth = TILE_SIZE, minHeight = TILE_SIZE)
      .background(background, RoundedCornerShape(14.dp))
      .padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      compactCount(count),
      style = MaterialTheme.typography.titleMedium.tabular().copy(fontWeight = FontWeight.Bold),
      color = content,
      maxLines = 1,
    )
    Text(
      stringResource(R.string.inbox_tile_events).uppercase(locale),
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
      color = content,
      maxLines = 1,
    )
  }
}
