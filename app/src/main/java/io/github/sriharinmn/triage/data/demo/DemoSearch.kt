package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.issues.toInstantOrNull
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.remote.Page
import java.time.Duration
import java.time.Instant

/** The parts of Sentry's search syntax the app sends. */
internal data class DemoQuery(
  val unresolved: Boolean = false,
  val forReview: Boolean = false,
  val regressed: Boolean = false,
  val escalating: Boolean = false,
  /** `assigned_or_suggested:[me, my_teams, none]`: unassigned, or assigned to the tester. */
  val mineOrUnassigned: Boolean = false,
  val firstSeenAfter: Instant? = null,
)

/** A query's terms, split on spaces outside `[...]`. */
internal fun searchTerms(query: String): List<String> {
  val terms = mutableListOf<String>()
  val current = StringBuilder()
  var depth = 0
  for (c in query) {
    when {
      c == '[' -> {
        depth++
        current.append(c)
      }
      c == ']' -> {
        depth = maxOf(0, depth - 1)
        current.append(c)
      }
      c.isWhitespace() && depth == 0 -> {
        if (current.isNotEmpty()) terms += current.toString()
        current.clear()
      }
      else -> current.append(c)
    }
  }
  if (current.isNotEmpty()) terms += current.toString()
  return terms
}

/** Reads [query]; terms the demo doesn't know are ignored. */
internal fun parseDemoQuery(query: String): DemoQuery =
  searchTerms(query).fold(DemoQuery()) { q, term ->
    when {
      term == "is:unresolved" -> q.copy(unresolved = true)
      term == "is:for_review" -> q.copy(forReview = true)
      term == "is:regressed" -> q.copy(regressed = true)
      term == "is:escalating" -> q.copy(escalating = true)
      term.startsWith("assigned_or_suggested:") -> q.copy(mineOrUnassigned = true)
      term.startsWith(FIRST_SEEN_AFTER) -> q.copy(firstSeenAfter = term.removePrefix(FIRST_SEEN_AFTER).toInstantOrNull())
      else -> q
    }
  }

/** The issues matching [params] at [now], in its sort order (newest or largest first, then by id). */
internal fun searchIssues(views: List<DemoIssueView>, params: IssueListParams, now: Instant, meId: String): List<DemoIssueView> {
  val query = parseDemoQuery(params.query)
  val since = period(params.statsPeriod)?.let { now - it }
  return views
    .filter { params.projectIds.isEmpty() || it.project.id in params.projectIds }
    .filter { params.environment == null || params.environment in it.environments }
    .filter { since == null || it.lastSeen >= since }
    .filter { query.matches(it, meId) }
    .sortedWith(sortOrder(params.sort))
}

/** One page of [items] from [cursor] (`demo:<offset>`; anything else starts at the beginning). */
internal fun <T> pageOf(items: List<T>, limit: Int, cursor: String?): Page<T> {
  val offset =
    cursor?.takeIf { it.startsWith(CURSOR_PREFIX) }?.removePrefix(CURSOR_PREFIX)?.toIntOrNull()?.coerceIn(0, items.size) ?: 0
  val end = minOf(items.size, offset + limit.coerceAtLeast(1))
  return Page(items.subList(offset, end), if (end < items.size) "$CURSOR_PREFIX$end" else null)
}

private fun DemoQuery.matches(issue: DemoIssueView, meId: String): Boolean {
  val needsUnresolved = unresolved || forReview || regressed || escalating
  val assignee = issue.state.assigneeId
  return when {
    needsUnresolved && issue.status != "unresolved" -> false
    forReview && !issue.forReview -> false
    regressed && issue.substatus != "regressed" -> false
    escalating && issue.substatus != "escalating" -> false
    mineOrUnassigned && assignee != null && assignee != meId -> false
    firstSeenAfter != null && !issue.firstSeen.isAfter(firstSeenAfter) -> false
    else -> true
  }
}

private fun sortOrder(sort: String): Comparator<DemoIssueView> {
  val primary: Comparator<DemoIssueView> =
    when (sort) {
      "new" -> compareByDescending { it.firstSeen }
      "freq" -> compareByDescending { it.count }
      "user" -> compareByDescending { it.userCount }
      else -> compareByDescending { it.lastSeen }
    }
  return primary.thenBy { it.state.id }
}

/** "14d", "24h", "90m" or "2w"; null for anything else (no period filter). */
private fun period(value: String): Duration? {
  val match = Regex("""^(\d+)([mhdw])$""").matchEntire(value) ?: return null
  val n = match.groupValues[1].toLong()
  return when (match.groupValues[2]) {
    "m" -> Duration.ofMinutes(n)
    "h" -> Duration.ofHours(n)
    "d" -> Duration.ofDays(n)
    else -> Duration.ofDays(7 * n)
  }
}

private const val FIRST_SEEN_AFTER = "firstSeen:>"
private const val CURSOR_PREFIX = "demo:"
