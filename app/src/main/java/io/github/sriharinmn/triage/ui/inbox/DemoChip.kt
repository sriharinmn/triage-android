package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.sriharinmn.triage.R

/** Test tags for the demo's chip and note. */
object DemoTags {
  const val CHIP = "demo_chip"
  const val CONNECT = "demo_connect"
  const val KEEP_EXPLORING = "demo_keep_exploring"
}

/** "Demo", next to the org's name while the sample data is showing. */
@Composable
internal fun DemoChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val description = stringResource(R.string.demo_chip_description)
  AssistChip(
    onClick = onClick,
    label = { Text(stringResource(R.string.demo_chip)) },
    modifier = modifier.testTag(DemoTags.CHIP).semantics { contentDescription = description },
  )
}

/** What the sample data is, with Connect Sentry (leaves the demo for Connect) and Keep exploring. */
@Composable
internal fun DemoNoteDialog(onConnect: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.demo_note_title)) },
    text = { Text(stringResource(R.string.demo_note_body)) },
    confirmButton = {
      TextButton(onClick = onConnect, modifier = Modifier.testTag(DemoTags.CONNECT)) { Text(stringResource(R.string.demo_note_connect)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag(DemoTags.KEEP_EXPLORING)) {
        Text(stringResource(R.string.demo_note_keep_exploring))
      }
    },
  )
}
