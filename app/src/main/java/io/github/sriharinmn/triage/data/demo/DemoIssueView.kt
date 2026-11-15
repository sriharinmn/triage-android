package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoIssueSpec
import io.github.sriharinmn.triage.data.demo.content.DemoProject
import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import java.time.Instant

/** One demo issue as it looks at a moment: its saved facts, its content, and everything worked out from them. */
internal data class DemoIssueView(
  val state: DemoIssueState,
  val spec: DemoIssueSpec,
  val project: DemoProject,
  val traffic: IssueTraffic,
  val count: Long,
  val userCount: Long,
  val firstSeen: Instant,
  val lastSeen: Instant,
  /** Sentry's status: unresolved, resolved or ignored (Archived). */
  val status: String,
  /** Sentry's substatus: new, ongoing, regressed, escalating, archived_until_escalating, or null when resolved. */
  val substatus: String?,
  val forReview: Boolean,
) {
  /** The environments its events come from. */
  val environments: List<String>
    get() = spec.tags["environment"].orEmpty().map { it.first }
}

/** This issue at [now]; null if its content is gone (a demo saved by an older version of the app). */
internal fun DemoIssueState.view(now: Instant): DemoIssueView? {
  val spec = DemoWorld.spec(contentKey) ?: return null
  val traffic =
    IssueTraffic(
      seed = seedOf(id),
      ratePerHour = spec.ratePerHour,
      firstSeen = instant(firstSeen),
      extraEvents = listOfNotNull(regressedAt).map(::instant),
      quiet = quietSpans.map { QuietSpan(instant(it.from), it.to?.let(::instant)) },
      bursts = bursts.map(::instant),
    )
  val summary = traffic.summary(now)
  val (apiStatus, apiSubstatus) =
    when (status) {
      DemoStatus.Resolved -> "resolved" to null
      DemoStatus.Archived -> "ignored" to "archived_until_escalating"
      DemoStatus.Unresolved -> "unresolved" to unresolvedSubstatus(now)
    }
  return DemoIssueView(
    state = this,
    spec = spec,
    project = DemoWorld.project(spec.projectSlug),
    traffic = traffic,
    count = summary.count,
    userCount = userCount(summary.count, spec.userShare),
    firstSeen = instant(firstSeen),
    lastSeen = summary.lastSeen,
    status = apiStatus,
    substatus = apiSubstatus,
    forReview = status == DemoStatus.Unresolved && inboxSince != null && inboxSince > (now - DEMO_WEEK).epochMs,
  )
}

/** Whichever of escalating, regressed or new happened most recently within a week; otherwise ongoing. */
private fun DemoIssueState.unresolvedSubstatus(now: Instant): String {
  val weekAgo = (now - DEMO_WEEK).epochMs
  return listOfNotNull(escalatedAt?.let { it to "escalating" }, regressedAt?.let { it to "regressed" }, firstSeen to "new")
    .filter { (time, _) -> time > weekAgo && time <= now.epochMs }
    .maxByOrNull { (time, _) -> time }
    ?.second ?: "ongoing"
}

internal fun DemoState.views(now: Instant): List<DemoIssueView> = issues.mapNotNull { it.view(now) }

internal fun DemoState.view(issueId: String, now: Instant): DemoIssueView? = issues.firstOrNull { it.id == issueId }?.view(now)
