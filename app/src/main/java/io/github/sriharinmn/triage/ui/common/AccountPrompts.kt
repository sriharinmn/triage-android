package io.github.sriharinmn.triage.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R

/** A strip above the content when Sentry rejects the token: only reconnecting with a new one helps. */
@Composable
internal fun ReconnectBanner(message: String, onReconnect: () -> Unit, buttonTag: String, modifier: Modifier = Modifier) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      TextButton(onClick = onReconnect, modifier = Modifier.testTag(buttonTag)) {
        Text(stringResource(R.string.inbox_reconnect))
      }
    }
  }
}

/** Asks before forgetting the connection: Sentry shows a token only once, so this can't be undone from the app. */
@Composable
internal fun DisconnectDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.inbox_disconnect_title)) },
    text = { Text(stringResource(R.string.inbox_disconnect_body)) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.inbox_disconnect_confirm)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.inbox_disconnect_cancel)) } },
  )
}
