package io.github.sriharinmn.triage.ui.inbox

import androidx.annotation.StringRes
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueSort

@StringRes
internal fun InboxTab.labelRes(): Int =
  when (this) {
    InboxTab.ForReview -> R.string.inbox_tab_for_review
    InboxTab.Unresolved -> R.string.inbox_tab_unresolved
    InboxTab.Regressed -> R.string.inbox_tab_regressed
    InboxTab.Escalating -> R.string.inbox_tab_escalating
  }

@StringRes
internal fun InboxTab.emptyRes(): Int =
  when (this) {
    InboxTab.ForReview -> R.string.inbox_empty_for_review
    InboxTab.Unresolved -> R.string.inbox_empty_unresolved
    InboxTab.Regressed -> R.string.inbox_empty_regressed
    InboxTab.Escalating -> R.string.inbox_empty_escalating
  }

@StringRes
internal fun IssueSort.labelRes(): Int =
  when (this) {
    IssueSort.LastSeen -> R.string.inbox_sort_last_seen
    IssueSort.FirstSeen -> R.string.inbox_sort_first_seen
    IssueSort.Events -> R.string.inbox_sort_events
    IssueSort.Users -> R.string.inbox_sort_users
  }

@StringRes
internal fun IssueBadge.labelRes(): Int =
  when (this) {
    IssueBadge.New -> R.string.issue_badge_new
    IssueBadge.Regressed -> R.string.issue_badge_regressed
    IssueBadge.Escalating -> R.string.issue_badge_escalating
  }
