package io.github.sriharinmn.triage.data.actions

import io.github.sriharinmn.triage.data.members.Member
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What the user asked Sentry to do with one issue. */
sealed interface IssueAction {
  /** Marks it fixed; a recurrence shows up as regressed. */
  data object Resolve : IssueAction

  /** Archives it until it escalates (Sentry's default "Archive"): it comes back by itself if it spikes. */
  data object Archive : IssueAction

  /** Assigns it to [member], or unassigns it when [member] is null. */
  data class Assign(val member: Member?) : IssueAction

  /** Takes it out of For review without resolving it. */
  data object MarkReviewed : IssueAction
}

/** The body of Sentry's "Update an Issue" call for this action. */
internal fun IssueAction.toChanges(): JsonObject = buildJsonObject {
  when (val action = this@toChanges) {
    IssueAction.Resolve -> put("status", "resolved")
    IssueAction.Archive -> {
      put("status", "ignored")
      put("substatus", "archived_until_escalating")
      putJsonObject("statusDetails") {}
    }
    is IssueAction.Assign -> put("assignedTo", action.member?.let { "user:${it.userId}" } ?: "")
    IssueAction.MarkReviewed -> put("inbox", false)
  }
}
