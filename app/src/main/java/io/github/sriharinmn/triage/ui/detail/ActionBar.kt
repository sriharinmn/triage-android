package io.github.sriharinmn.triage.ui.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.sriharinmn.triage.R

/** Resolve, Archive, Assign and, while the issue is up for review, Reviewed: each one tap, each with Undo. */
@Composable
internal fun DetailActionBar(
  canMarkReviewed: Boolean,
  onResolve: () -> Unit,
  onArchive: () -> Unit,
  onAssign: () -> Unit,
  onMarkReviewed: () -> Unit,
) {
  BottomAppBar {
    ActionButton(R.drawable.ic_check, R.string.action_resolve, onResolve, DetailTags.RESOLVE)
    ActionButton(R.drawable.ic_archive, R.string.action_archive, onArchive, DetailTags.ARCHIVE)
    ActionButton(R.drawable.ic_person, R.string.action_assign, onAssign, DetailTags.ASSIGN)
    if (canMarkReviewed) ActionButton(R.drawable.ic_done_all, R.string.action_mark_reviewed, onMarkReviewed, DetailTags.MARK_REVIEWED)
  }
}

@Composable
private fun RowScope.ActionButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit, tag: String) {
  TextButton(onClick = onClick, modifier = Modifier.weight(1f).testTag(tag)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      // The text below names the button; the icon is decoration.
      Icon(painterResource(icon), contentDescription = null)
      Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
