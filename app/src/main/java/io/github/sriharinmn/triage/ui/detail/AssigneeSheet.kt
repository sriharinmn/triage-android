package io.github.sriharinmn.triage.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.members.Member

/**
 * Who to assign the issue to. The first time, the sheet asks "Which one is you?". After that it offers Assign to me,
 * Unassigned, and everyone else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssigneeSheet(
  state: AssignSheetState,
  onMemberChosen: (Member) -> Unit,
  onUnassign: () -> Unit,
  onNotMe: () -> Unit,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.navigationBarsPadding()) {
      when {
        state.isLoading -> {
          val description = stringResource(R.string.assign_loading)
          Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = description })
          }
        }
        state.error != null -> {
          Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(detailErrorMessage(state.error), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.inbox_retry)) }
          }
          // Unassigning needs no member list, so it still works.
          ChoiceRow(stringResource(R.string.assign_unassigned), onClick = onUnassign)
        }
        state.askingWhoIsMe -> {
          SheetTitle(stringResource(R.string.assign_who_are_you))
          Text(
            stringResource(R.string.assign_who_are_you_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
          )
          LazyColumn { items(state.members, key = { it.userId }) { member -> MemberRow(member, onClick = { onMemberChosen(member) }) } }
        }
        else -> {
          SheetTitle(stringResource(R.string.assign_title))
          val me = state.me
          LazyColumn {
            if (me != null) item(key = "me") { ChoiceRow(stringResource(R.string.assign_to_me, me.name), onClick = { onMemberChosen(me) }) }
            item(key = "unassigned") { ChoiceRow(stringResource(R.string.assign_unassigned), onClick = onUnassign) }
            item(key = "divider") { HorizontalDivider() }
            val others = state.members.filter { it.userId != me?.userId }
            if (others.isEmpty() && me == null) {
              item(key = "none") {
                Text(stringResource(R.string.assign_no_members), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
              }
            }
            items(others, key = { it.userId }) { member -> MemberRow(member, onClick = { onMemberChosen(member) }) }
            if (me != null) {
              item(key = "not_me") {
                TextButton(onClick = onNotMe, modifier = Modifier.padding(horizontal = 12.dp).testTag(DetailTags.NOT_ME)) {
                  Text(stringResource(R.string.assign_not_you))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SheetTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
  )
}

@Composable
private fun ChoiceRow(text: String, onClick: () -> Unit) {
  ListItem(headlineContent = { Text(text) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick))
}

@Composable
private fun MemberRow(member: Member, onClick: () -> Unit) {
  ListItem(
    headlineContent = { Text(member.name) },
    supportingContent = member.email?.takeIf { it != member.name }?.let { email -> { Text(email) } },
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
  )
}
