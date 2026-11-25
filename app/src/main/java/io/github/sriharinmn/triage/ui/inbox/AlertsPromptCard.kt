package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R

/** The one-time "Get alerts…?" prompt above the list: a flat band, no card and no stripe; it goes once either button is tapped. */
@Composable
internal fun AlertsPromptCard(onTurnOn: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().testTag(InboxTags.ALERTS_CARD)) {
    Column(
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
          painterResource(R.drawable.ic_notifications),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(stringResource(R.string.alerts_card_title), style = MaterialTheme.typography.titleSmall)
          Text(
            stringResource(R.string.alerts_card_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onNotNow, modifier = Modifier.testTag(InboxTags.ALERTS_CARD_NOT_NOW)) {
          Text(stringResource(R.string.alerts_card_not_now))
        }
        TextButton(onClick = onTurnOn, modifier = Modifier.testTag(InboxTags.ALERTS_CARD_TURN_ON)) {
          Text(stringResource(R.string.alerts_card_turn_on))
        }
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}
