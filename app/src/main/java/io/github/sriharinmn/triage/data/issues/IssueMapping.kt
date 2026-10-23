package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.ProjectDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** The inbox's view of a Sentry issue; blank culprits and unparseable times become null. */
fun IssueDto.toIssue(): Issue =
  Issue(
    id = id,
    shortId = shortId,
    title = title,
    culprit = culprit?.takeIf { it.isNotBlank() },
    level = IssueLevel.fromApi(level),
    badge = badgeFromSubstatus(substatus),
    isUnhandled = isUnhandled,
    eventCount = count,
    userCount = userCount,
    firstSeen = firstSeen.toInstantOrNull(),
    lastSeen = lastSeen.toInstantOrNull(),
    permalink = permalink,
    projectId = project.id,
    projectSlug = project.slug,
    hourlyEvents = stats["24h"].bucketCounts(),
  )

/** A project with its environment names sorted, de-duplicated and non-blank. */
fun ProjectDto.toProject(): Project =
  Project(
    id = id,
    slug = slug,
    name = name,
    platform = platform,
    environments = environments.filter { it.isNotBlank() }.distinct().sorted(),
  )

/** The row's status label; ongoing and archived issues have none. */
internal fun badgeFromSubstatus(substatus: String?): IssueBadge? =
  when (substatus) {
    "new" -> IssueBadge.New
    "regressed" -> IssueBadge.Regressed
    "escalating" -> IssueBadge.Escalating
    else -> null
  }

/** The counts of Sentry's `[[unixSeconds, count], ...]` stats buckets, oldest first; a malformed bucket counts as 0. */
internal fun List<List<Double>>?.bucketCounts(): List<Long> =
  orEmpty().map { bucket -> bucket.getOrNull(1)?.toLong() ?: 0L }

/** Sentry timestamps are ISO-8601 with `Z` or an offset, sometimes with microseconds; anything else is null. */
internal fun String?.toInstantOrNull(): Instant? =
  try {
    this?.let { OffsetDateTime.parse(it).toInstant() }
  } catch (_: DateTimeParseException) {
    null
  }
