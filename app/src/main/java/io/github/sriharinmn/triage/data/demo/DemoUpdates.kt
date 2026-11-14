package io.github.sriharinmn.triage.data.demo

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The state after Sentry's "Update an Issue" with [changes] on [issueId] at [now], or null if there's no such issue. Reads
 * the fields the app sends: `status` (`resolved`, or `ignored` for Archive), `assignedTo` (`user:<id>` or `""`) and
 * `inbox: false` (Mark reviewed). Other fields are ignored.
 */
internal fun DemoState.updated(issueId: String, changes: JsonObject, now: Instant): DemoState? {
  var issue = issues.firstOrNull { it.id == issueId } ?: return null
  val t = now.epochMs
  when (changes.string("status")) {
    "resolved" ->
      if (issue.status != DemoStatus.Resolved) {
        issue = issue.copy(status = DemoStatus.Resolved, archivedAt = null, inboxSince = null, quietSpans = issue.quietSpans + DemoQuietSpan(from = t))
      }
    "ignored" -> issue = issue.copy(status = DemoStatus.Archived, archivedAt = t, inboxSince = null)
  }
  changes.string("assignedTo")?.let { value ->
    issue = issue.copy(assigneeId = value.removePrefix(USER_PREFIX).takeIf { value.startsWith(USER_PREFIX) && it.isNotEmpty() })
  }
  if ((changes["inbox"] as? JsonPrimitive)?.booleanOrNull == false) issue = issue.copy(inboxSince = null)
  val changed = issue
  return copy(issues = issues.map { if (it.id == issueId) changed else it })
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private const val USER_PREFIX = "user:"
