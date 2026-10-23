package io.github.sriharinmn.triage.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `GET /organizations/{org}/issues/{id}/`: the issue with 24h and 30d stats, its releases and its tag keys. */
@Serializable
data class IssueDetailDto(
  val id: String,
  val shortId: String,
  val title: String,
  val culprit: String? = null,
  val level: String? = null,
  /** unresolved, resolved, ignored (Sentry's UI calls it "Archived"), or a value Sentry adds later. */
  val status: String? = null,
  /** new, ongoing, regressed, escalating, or an archived_* value. */
  val substatus: String? = null,
  /** Not in the documented response; null means "unknown", so the cached inbox row's value is kept. */
  val isUnhandled: Boolean? = null,
  // Sentry sends count as a string and userCount as a number; accept either for both.
  @Serializable(with = FlexibleLongSerializer::class) val count: Long = 0,
  @Serializable(with = FlexibleLongSerializer::class) val userCount: Long = 0,
  val firstSeen: String? = null,
  val lastSeen: String? = null,
  val permalink: String? = null,
  val project: IssueProjectDto,
  val firstRelease: ReleaseDto? = null,
  val lastRelease: ReleaseDto? = null,
  /** "24h" (hourly) and "30d" (daily) buckets: `[[unixSeconds, count], ...]`, oldest first. */
  val stats: Map<String, List<List<Double>>> = emptyMap(),
  val tags: List<IssueTagKeyDto> = emptyList(),
)

@Serializable
data class ReleaseDto(
  val version: String? = null,
  val shortVersion: String? = null,
)

/** One tag key of an issue; how its values are spread comes from [TagDetailDto]. */
@Serializable
data class IssueTagKeyDto(
  val key: String,
  val name: String? = null,
  @Serializable(with = FlexibleLongSerializer::class) val totalValues: Long = 0,
)

/**
 * `GET /organizations/{org}/issues/{id}/events/{eventId}/`. Events differ by SDK and platform, so everything below the
 * top level stays raw JSON and is mapped leniently by `data/detail/EventMapping.kt`.
 */
@Serializable
data class EventDto(
  val id: String? = null,
  val dateCreated: String? = null,
  val message: String? = null,
  /** Usually `{version, shortVersion}`; raw because some servers send a plain string. */
  val release: JsonElement? = null,
  /** `[{key, value}, ...]` */
  val tags: JsonElement? = null,
  /** Keyed by context name: device, os, runtime, browser, app, ... */
  val contexts: JsonElement? = null,
  val user: JsonElement? = null,
  /** `[{type, data}, ...]`: exception, threads, message, breadcrumbs, request, and types we ignore. */
  val entries: List<JsonElement> = emptyList(),
)

/** `GET /organizations/{org}/issues/{id}/tags/{key}/`: how often each value of one tag occurs, most common first. */
@Serializable
data class TagDetailDto(
  val key: String,
  val name: String? = null,
  @Serializable(with = FlexibleLongSerializer::class) val totalValues: Long = 0,
  val topValues: List<TagValueDto> = emptyList(),
)

@Serializable
data class TagValueDto(
  val value: String? = null,
  val name: String? = null,
  @Serializable(with = FlexibleLongSerializer::class) val count: Long = 0,
)
