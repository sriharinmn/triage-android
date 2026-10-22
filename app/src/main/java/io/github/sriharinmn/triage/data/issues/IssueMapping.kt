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
    badge =
      when (substatus) {
        "new" -> IssueBadge.New
        "regressed" -> IssueBadge.Regressed
        "escalating" -> IssueBadge.Escalating
        else -> null
      },
    isUnhandled = isUnhandled,
    eventCount = count,
    userCount = userCount,
    firstSeen = firstSeen.toInstantOrNull(),
    lastSeen = lastSeen.toInstantOrNull(),
    permalink = permalink,
    projectId = project.id,
    projectSlug = project.slug,
    hourlyEvents = stats["24h"].orEmpty().map { bucket -> bucket.getOrNull(1)?.toLong() ?: 0L },
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

/** Sentry timestamps are ISO-8601 with `Z` or an offset, sometimes with microseconds. */
private fun String?.toInstantOrNull(): Instant? =
  try {
    this?.let { OffsetDateTime.parse(it).toInstant() }
  } catch (_: DateTimeParseException) {
    null
  }
