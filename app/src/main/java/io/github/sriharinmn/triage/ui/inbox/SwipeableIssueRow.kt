package io.github.sriharinmn.triage.ui.inbox

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.theme.semanticColors
import java.time.Instant
import kotlinx.coroutines.launch

/** How far a row must travel before letting go resolves or archives it. */
private const val SWIPE_THRESHOLD = 0.4f

/**
 * An issue row that resolves when swiped right and archives when swiped left. The swipe state is snapped back to
 * settled right after the action fires: it is saveable, and a LazyColumn restores saved state by item key, so a row that
 * returns after Undo or a failed send (or that the ViewModel ignored) would otherwise stay in its dismissed position.
 * TalkBack users get the same two actions in the row's actions menu.
 */
@Composable
internal fun SwipeableIssueRow(
  issue: Issue,
  now: Instant,
  onClick: () -> Unit,
  onResolve: () -> Unit,
  onArchive: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val resolveLabel = stringResource(R.string.action_resolve)
  val archiveLabel = stringResource(R.string.action_archive)
  val state = rememberSwipeToDismissBoxState(positionalThreshold = { width -> width * SWIPE_THRESHOLD })
  val scope = rememberCoroutineScope()
  SwipeToDismissBox(
    state = state,
    backgroundContent = { SwipeBackground(state.dismissDirection) },
    onDismiss = { value ->
      when (value) {
        SwipeToDismissBoxValue.StartToEnd -> onResolve()
        SwipeToDismissBoxValue.EndToStart -> onArchive()
        SwipeToDismissBoxValue.Settled -> Unit
      }
      if (value != SwipeToDismissBoxValue.Settled) scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
    },
  ) {
    IssueRow(
      issue = issue,
      now = now,
      onClick = onClick,
      // On the row itself, which merges its texts into one TalkBack item, so the actions belong to that item.
      modifier =
        modifier.semantics {
          customActions =
            listOf(
              CustomAccessibilityAction(resolveLabel) {
                onResolve()
                true
              },
              CustomAccessibilityAction(archiveLabel) {
                onArchive()
                true
              },
            )
        },
    )
  }
}

/** Behind the row: Resolve on the left when swiping right, Archive on the right when swiping left. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
  if (direction == SwipeToDismissBoxValue.Settled) return
  val colors = MaterialTheme.semanticColors
  val resolving = direction == SwipeToDismissBoxValue.StartToEnd
  val background = if (resolving) colors.resolveSwipe else colors.archiveSwipe
  val content = if (resolving) colors.onResolveSwipe else colors.onArchiveSwipe
  Box(
    Modifier.fillMaxSize().background(background).padding(horizontal = 24.dp),
    contentAlignment = if (resolving) Alignment.CenterStart else Alignment.CenterEnd,
  ) {
    SwipeLabel(
      icon = if (resolving) R.drawable.ic_check else R.drawable.ic_archive,
      label = if (resolving) R.string.action_resolve else R.string.action_archive,
      color = content,
    )
  }
}

@Composable
private fun SwipeLabel(@DrawableRes icon: Int, @StringRes label: Int, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(painterResource(icon), contentDescription = null, tint = color)
    Text(stringResource(label), color = color, style = MaterialTheme.typography.labelLarge)
  }
}
