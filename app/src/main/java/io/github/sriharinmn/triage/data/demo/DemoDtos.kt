package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoBreadcrumb
import io.github.sriharinmn.triage.data.demo.content.DemoEventSpec
import io.github.sriharinmn.triage.data.demo.content.DemoException
import io.github.sriharinmn.triage.data.demo.content.DemoFrame
import io.github.sriharinmn.triage.data.demo.content.DemoPerson
import io.github.sriharinmn.triage.data.demo.content.DemoProject
import io.github.sriharinmn.triage.data.demo.content.DemoRelease
import io.github.sriharinmn.triage.data.demo.content.DemoThread
import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import io.github.sriharinmn.triage.data.demo.content.releaseAt
import io.github.sriharinmn.triage.remote.AssigneeDto
import io.github.sriharinmn.triage.remote.EventDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueProjectDto
import io.github.sriharinmn.triage.remote.IssueTagKeyDto
import io.github.sriharinmn.triage.remote.MemberDto
import io.github.sriharinmn.triage.remote.MemberUserDto
import io.github.sriharinmn.triage.remote.ProjectDto
import io.github.sriharinmn.triage.remote.ReleaseDto
import io.github.sriharinmn.triage.remote.TagDetailDto
import io.github.sriharinmn.triage.remote.TagValueDto
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Which of an issue's events: the latest (also Sentry's "recommended") or the oldest. */
internal enum class EventPick {
  Latest,
  Oldest,
}

/** The inbox row, as `GET /organizations/{org}/issues/` sends it. */
internal fun DemoIssueView.toIssueDto(now: Instant): IssueDto =
  IssueDto(
    id = state.id,
    shortId = state.shortId,
    title = spec.title,
    culprit = spec.culprit,
    level = spec.level,
    status = status,
    substatus = substatus,
    isUnhandled = spec.isUnhandled,
    count = count,
    userCount = userCount,
    firstSeen = iso(firstSeen),
    lastSeen = iso(lastSeen),
    permalink = null,
    project = project.toIssueProjectDto(),
    stats = mapOf("24h" to traffic.hourlyBuckets(now)),
  )

/** The issue, as `GET /organizations/{org}/issues/{id}/?expand=inbox` sends it. */
internal fun DemoIssueView.toIssueDetailDto(now: Instant, startedAt: Instant): IssueDetailDto =
  IssueDetailDto(
    id = state.id,
    shortId = state.shortId,
    title = spec.title,
    culprit = spec.culprit,
    level = spec.level,
    status = status,
    substatus = substatus,
    isUnhandled = spec.isUnhandled,
    count = count,
    userCount = userCount,
    firstSeen = iso(firstSeen),
    lastSeen = iso(lastSeen),
    permalink = null,
    project = project.toIssueProjectDto(),
    firstRelease = project.releaseAt(firstSeen, startedAt).toDto(),
    lastRelease = project.releaseAt(lastSeen, startedAt).toDto(),
    stats = mapOf("24h" to traffic.hourlyBuckets(now), "30d" to traffic.dailyBuckets(now)),
    tags = spec.tags.keys.map { IssueTagKeyDto(key = it, name = it, totalValues = count) },
    assignedTo =
      state.assigneeId?.let(DemoWorld::person)?.let { AssigneeDto(type = "user", id = it.userId, name = it.name, email = it.email) },
    // Sentry sends an object while the issue is up for review; its fields aren't documented or read.
    inbox = if (forReview) JsonObject(emptyMap()) else null,
  )

/** The event, as `GET /organizations/{org}/issues/{id}/events/{latest|oldest|recommended}/` sends it. */
internal fun DemoIssueView.toEventDto(pick: EventPick, startedAt: Instant): EventDto {
  val at = if (pick == EventPick.Oldest) firstSeen else lastSeen
  val release = project.releaseAt(at, startedAt)
  val event = spec.event
  val tags = buildList {
    add("environment" to spec.tags["environment"].orEmpty().firstOrNull()?.first.orEmpty())
    add("level" to spec.level)
    add("release" to release.version)
    spec.tags.filterKeys { it != "environment" }.forEach { (key, shares) -> shares.firstOrNull()?.let { add(key to it.first) } }
  }
  return EventDto(
    id = eventId(state.id, at),
    dateCreated = iso(at),
    message = event.message,
    release = buildJsonObject {
      put("version", release.version)
      put("shortVersion", release.shortVersion)
    },
    tags = JsonArray(tags.map { (key, value) -> buildJsonObject { put("key", key); put("value", value) } }),
    contexts = contextsJson(event, release),
    user = event.userIndex?.let(::userJson),
    entries = entries(event, at),
  )
}

/** How often each value of [key] occurs, most common first; null for a tag the issue doesn't have. */
internal fun DemoIssueView.toTagDetailDto(key: String): TagDetailDto? {
  val shares = spec.tags[key] ?: return null
  return TagDetailDto(
    key = key,
    name = key,
    totalValues = count,
    topValues = shares.map { (value, share) -> TagValueDto(value = value, name = value, count = (count * share).roundToLong()) },
  )
}

internal fun DemoProject.toProjectDto(): ProjectDto =
  ProjectDto(id = id, slug = slug, name = name, platform = platform, environments = environments, hasAccess = true)

internal fun DemoPerson.toMemberDto(): MemberDto =
  MemberDto(id = "m$userId", name = name, email = email, user = MemberUserDto(id = userId, name = name, email = email))

private fun DemoProject.toIssueProjectDto() = IssueProjectDto(id = id, slug = slug, name = name)

private fun DemoRelease.toDto() = ReleaseDto(version = version, shortVersion = shortVersion)

private fun iso(time: Instant): String = DateTimeFormatter.ISO_INSTANT.format(time)

/** 32 hex characters, like Sentry's event ids, fixed by the issue and the moment. */
private fun eventId(issueId: String, at: Instant): String {
  val seed = seedOf(issueId)
  return "%016x%016x".format(mix(seed + at.toEpochMilli()), mix(seed xor at.toEpochMilli()))
}

private fun userJson(index: Int): JsonObject = buildJsonObject {
  put("id", "user-$index")
  put("email", "user-$index@example.com")
  put("ip_address", "203.0.113.$index")
}

private fun entries(event: DemoEventSpec, at: Instant): List<JsonElement> = buildList {
  if (event.exceptions.isNotEmpty()) add(entry("exception", buildJsonObject { put("values", JsonArray(event.exceptions.map { it.toJson() })) }))
  if (event.threads.isNotEmpty()) add(entry("threads", buildJsonObject { put("values", JsonArray(event.threads.map { it.toJson() })) }))
  event.message?.let { message -> add(entry("message", buildJsonObject { put("formatted", message) })) }
  if (event.breadcrumbs.isNotEmpty()) {
    add(entry("breadcrumbs", buildJsonObject { put("values", JsonArray(event.breadcrumbs.map { it.toJson(at) })) }))
  }
  event.request?.let { request ->
    add(entry("request", buildJsonObject {
      put("method", request.method)
      put("url", request.url)
    }))
  }
}

private fun entry(type: String, data: JsonObject): JsonObject = buildJsonObject {
  put("type", type)
  put("data", data)
}

private fun DemoException.toJson(): JsonObject = buildJsonObject {
  put("type", type)
  put("value", value)
  module?.let { put("module", it) }
  put("stacktrace", framesJson(frames))
}

private fun DemoThread.toJson(): JsonObject = buildJsonObject {
  put("id", id)
  put("name", name)
  put("crashed", crashed)
  put("current", crashed)
  put("stacktrace", framesJson(frames))
}

private fun framesJson(frames: List<DemoFrame>): JsonObject = buildJsonObject { put("frames", JsonArray(frames.map { it.toJson() })) }

private fun DemoFrame.toJson(): JsonObject = buildJsonObject {
  put("function", function)
  module?.let { put("module", it) }
  filename?.let { put("filename", it) }
  lineNo?.let { put("lineNo", it) }
  colNo?.let { put("colNo", it) }
  put("inApp", inApp)
  if (context.isNotEmpty()) {
    put("context", JsonArray(context.map { (line, text) -> JsonArray(listOf(JsonPrimitive(line), JsonPrimitive(text))) }))
  }
}

private fun DemoBreadcrumb.toJson(eventAt: Instant): JsonObject = buildJsonObject {
  put("timestamp", iso(eventAt.minusSeconds(secondsBefore)))
  put("category", category)
  put("level", level)
  put("type", type)
  message?.let { put("message", it) }
  if (data.isNotEmpty()) put("data", JsonObject(data.mapValues { JsonPrimitive(it.value) }))
}

/** The event's contexts; an `app` context takes its version and build from the event's [release], so they always agree. */
private fun contextsJson(event: DemoEventSpec, release: DemoRelease): JsonObject =
  JsonObject(
    event.contexts.mapValues { (name, fields) ->
      val values = fields.mapValues<String, String, JsonElement> { JsonPrimitive(it.value) }.toMutableMap()
      if (name == "app") {
        // "com.example.shop@2.5.0+250": the version, then the build after the first "+".
        val parts = release.version.substringAfterLast('@').split('+', limit = 2)
        values["app_version"] = JsonPrimitive(parts[0])
        parts.getOrNull(1)?.let { values["app_build"] = JsonPrimitive(it) }
      }
      JsonObject(values)
    },
  )
