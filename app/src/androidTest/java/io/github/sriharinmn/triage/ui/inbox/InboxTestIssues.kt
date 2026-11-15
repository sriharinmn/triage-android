package io.github.sriharinmn.triage.ui.inbox

import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import java.time.Duration
import java.time.Instant

/** An issue for inbox tests: one error, seen three minutes before [now]. */
internal fun testIssue(id: String, now: Instant) =
  Issue(
    id = id,
    shortId = "DEMO-$id",
    title = "Error $id",
    culprit = "handler in app",
    level = IssueLevel.Error,
    badge = IssueBadge.New,
    isUnhandled = false,
    eventCount = 48,
    userCount = 11,
    firstSeen = now - Duration.ofDays(5),
    lastSeen = now.minusSeconds(180),
    permalink = "https://example-org.sentry.io/issues/$id/",
    projectId = "101",
    projectSlug = "shop-api",
    hourlyEvents = List(24) { it.toLong() },
  )
