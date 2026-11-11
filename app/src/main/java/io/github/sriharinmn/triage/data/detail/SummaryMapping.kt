package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.badgeFromSubstatus
import io.github.sriharinmn.triage.data.issues.bucketCounts
import io.github.sriharinmn.triage.data.issues.toInstantOrNull
import io.github.sriharinmn.triage.remote.AssigneeDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.ReleaseDto
import io.github.sriharinmn.triage.remote.TagDetailDto
import kotlinx.serialization.json.JsonObject

/** At most this many tags get a distribution on screen; each costs one request. */
const val MAX_SHOWN_TAGS = 5

// One key per group, in this order; within a group, the first key the issue has wins ("os" says more than "os.name").
private val PREFERRED_TAG_GROUPS =
  listOf(
    listOf("environment"),
    listOf("release"),
    listOf("os", "os.name"),
    listOf("browser", "browser.name"),
    listOf("device"),
  )

/** The cached inbox row as a summary; the 30-day trend, the releases and the tag keys arrive with the fresh issue. */
fun Issue.toSummary(): IssueSummary =
  IssueSummary(
    id = id,
    shortId = shortId,
    title = title,
    culprit = culprit,
    level = level,
    badge = badge,
    isUnhandled = isUnhandled,
    // Every inbox tab lists unresolved issues.
    status = IssueStatus.Unresolved,
    eventCount = eventCount,
    userCount = userCount,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    permalink = permalink,
    hourlyEvents = hourlyEvents,
    dailyEvents = emptyList(),
    firstRelease = null,
    lastRelease = null,
    tagKeys = emptyList(),
  )

/**
 * Sentry's fresh issue as a summary. Sentry doesn't document `isUnhandled` on this endpoint, so when it's missing the
 * [cached] summary's value is kept.
 */
fun IssueDetailDto.toSummary(cached: IssueSummary?): IssueSummary =
  IssueSummary(
    id = id,
    shortId = shortId,
    title = title,
    culprit = culprit?.takeIf { it.isNotBlank() },
    level = IssueLevel.fromApi(level),
    badge = badgeFromSubstatus(substatus),
    isUnhandled = isUnhandled ?: cached?.isUnhandled ?: false,
    status = IssueStatus.fromApi(status),
    eventCount = count,
    userCount = userCount,
    firstSeen = firstSeen.toInstantOrNull(),
    lastSeen = lastSeen.toInstantOrNull(),
    permalink = permalink,
    hourlyEvents = stats["24h"].bucketCounts(),
    dailyEvents = stats["30d"].bucketCounts(),
    firstRelease = firstRelease?.displayName(),
    lastRelease = lastRelease?.displayName(),
    tagKeys = tags.map { it.key }.distinct(),
    assignedTo = assignedTo?.displayName(),
    isUpForReview = inbox is JsonObject,
    isFresh = true,
  )

private fun ReleaseDto.displayName(): String? = shortVersion ?: version

/** The tag's most common value, or null when Sentry has none for it. */
fun TagDetailDto.toTagSummary(): TagSummary? {
  val top = topValues.maxByOrNull { it.count } ?: return null
  val value = top.value ?: top.name ?: return null
  return TagSummary(key = key, topValue = value.clip(), count = top.count, total = totalValues)
}

/** The tags worth a request: the preferred ones the issue has, then its others in Sentry's order, [MAX_SHOWN_TAGS] at most. */
fun chooseTagKeys(keys: List<String>): List<String> {
  val present = keys.toSet()
  val preferred = PREFERRED_TAG_GROUPS.mapNotNull { group -> group.firstOrNull { it in present } }
  val grouped = PREFERRED_TAG_GROUPS.flatten().toSet()
  return (preferred + keys.filter { it !in grouped }).distinct().take(MAX_SHOWN_TAGS)
}

/** A person by name, or else email; a team as "#name", the way Sentry writes it. */
private fun AssigneeDto.displayName(): String? {
  val label = name?.takeIf { it.isNotBlank() } ?: email ?: return null
  return if (type == "team") "#$label" else label
}
