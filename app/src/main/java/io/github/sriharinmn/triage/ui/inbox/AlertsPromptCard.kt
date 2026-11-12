package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R

/** The one-time "Get alerts…?" card at the top of the inbox; it goes once either button is tapped. */
@Composable
internal fun AlertsPromptCard(onTurnOn: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(InboxTags.ALERTS_CARD)) {
    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
      Text(stringResource(R.string.alerts_card_title), style = MaterialTheme.typography.titleMedium)
      Text(
        stringResource(R.string.alerts_card_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, end = 8.dp),
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onNotNow, modifier = Modifier.testTag(InboxTags.ALERTS_CARD_NOT_NOW)) {
          Text(stringResource(R.string.alerts_card_not_now))
        }
        TextButton(onClick = onTurnOn, modifier = Modifier.testTag(InboxTags.ALERTS_CARD_TURN_ON)) {
          Text(stringResource(R.string.alerts_card_turn_on))
        }
      }
    }
  }
}
