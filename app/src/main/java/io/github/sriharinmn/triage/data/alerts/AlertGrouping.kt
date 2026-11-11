package io.github.sriharinmn.triage.data.alerts

/** What a check shows in the notification shade. */
sealed interface AlertPost {
  /** One notification per issue, strongest first; at most [MAX_SINGLES]. */
  data class Singles(val findings: List<AlertFinding>) : AlertPost

  /**
   * One notification for many issues: how many of each kind, and the project they all belong to (null when they span
   * projects or include issues held back during quiet hours, whose projects aren't kept). Tapping it opens the inbox on
   * the [strongest] kind's tab.
   */
  data class Summary(val counts: Map<AlertKind, Int>, val projectSlug: String?) : AlertPost {
    val strongest: AlertKind
      get() = counts.keys.max()
  }
}

/** What a check posts now ([post], or nothing), and the quiet-hours backlog to keep. */
data class GroupedAlerts(val post: AlertPost?, val backlog: Map<String, AlertKind>)

/** Up to this many issues found in one check get a notification each; more become one summary. */
const val MAX_SINGLES = 3

/**
 * How one check's [findings] are shown. Inside [quiet] hours nothing is posted: they join the [backlog]. After the window,
 * the backlog and any new findings are one summary. Otherwise up to [MAX_SINGLES] findings get a notification each and
 * more become a summary, as does a kind whose answer filled the whole first page ([capped]): its count can't be trusted
 * one by one.
 */
fun groupAlerts(
  findings: List<AlertFinding>,
  capped: Set<AlertKind>,
  backlog: Map<String, AlertKind>,
  quiet: Boolean,
): GroupedAlerts {
  val everything = LinkedHashMap(backlog)
  findings.forEach { finding -> everything.merge(finding.issueId, finding.kind) { held, found -> maxOf(held, found) } }
  if (quiet) return GroupedAlerts(post = null, backlog = everything)
  if (everything.isEmpty()) return GroupedAlerts(post = null, backlog = emptyMap())
  val summarize = backlog.isNotEmpty() || findings.size > MAX_SINGLES || findings.any { it.kind in capped }
  if (!summarize) return GroupedAlerts(AlertPost.Singles(findings), backlog = emptyMap())
  val fromThisCheck = backlog.keys.all { id -> findings.any { it.issueId == id } }
  val project = findings.map { it.projectSlug }.distinct().singleOrNull()?.takeIf { fromThisCheck }
  return GroupedAlerts(AlertPost.Summary(everything.values.groupingBy { it }.eachCount(), project), backlog = emptyMap())
}
