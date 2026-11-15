package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoStart
import io.github.sriharinmn.triage.data.demo.content.DemoStartingIssue
import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import java.time.Duration
import java.time.Instant

/** The demo as it stands when someone taps "Try with sample data" at [now]. */
internal fun freshDemoState(now: Instant): DemoState {
  // Sentry numbers a project's issues in the order it first saw them.
  val shortIds = mutableMapOf<String, String>()
  val nextShortNumber = mutableMapOf<String, Int>()
  for (project in DemoWorld.projects) {
    val oldestFirst = DemoWorld.startingIssues.filter { it.spec.projectSlug == project.slug }.sortedByDescending { it.firstSeenAgo }
    oldestFirst.forEachIndexed { k, issue -> shortIds[issue.spec.key] = "${project.shortIdPrefix}-${project.firstShortNumber + k}" }
    nextShortNumber[project.slug] = project.firstShortNumber + oldestFirst.size
  }
  return DemoState(
    startedAt = now.epochMs,
    processedUntil = now.epochMs,
    nextShortNumber = nextShortNumber,
    issues =
      DemoWorld.startingIssues.mapIndexed { i, issue ->
        issue.toState(id = (STARTING_ID_BASE + i).toString(), shortId = shortIds.getValue(issue.spec.key), now = now)
      },
  )
}

private fun DemoStartingIssue.toState(id: String, shortId: String, now: Instant): DemoIssueState {
  val firstSeen = now - firstSeenAgo
  val base = DemoIssueState(id = id, shortId = shortId, contentKey = spec.key, firstSeen = firstSeen.epochMs, assigneeId = assigneeId)
  val started =
    when (val start = start) {
      DemoStart.Unresolved -> base
      is DemoStart.Regressed -> {
        val at = now - start.ago
        val resolvedFrom = maxOf(firstSeen, at - RESOLVED_BEFORE_REGRESSION)
        base.copy(regressedAt = at.epochMs, quietSpans = listOf(DemoQuietSpan(from = resolvedFrom.epochMs, to = at.epochMs)))
      }
      is DemoStart.Escalating -> {
        val at = now - start.ago
        base.copy(escalatedAt = at.epochMs, bursts = listOf(at.epochMs))
      }
      is DemoStart.Archived -> base.copy(status = DemoStatus.Archived, archivedAt = (now - start.ago).epochMs)
      is DemoStart.Resolved -> base.copy(status = DemoStatus.Resolved, quietSpans = listOf(DemoQuietSpan(from = (now - start.ago).epochMs)))
    }
  // Up for review: new, regressed or escalating within the last week, and still unresolved.
  val lastChange = listOfNotNull(started.firstSeen, started.regressedAt, started.escalatedAt).max()
  val forReview = started.status == DemoStatus.Unresolved && lastChange > (now - DEMO_WEEK).epochMs
  return started.copy(inboxSince = if (forReview) lastChange else null)
}

/** How long a regressed starting issue had been resolved before it came back. */
private val RESOLVED_BEFORE_REGRESSION: Duration = Duration.ofDays(3)
