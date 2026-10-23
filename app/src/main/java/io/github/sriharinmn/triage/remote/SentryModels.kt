package io.github.sriharinmn.triage.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// coerceInputValues: an explicit null for a field with a default (e.g. "isUnhandled": null) reads as the default.
val SentryJson = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
}

@Serializable
data class OrganizationDto(
  val id: String,
  val slug: String,
  val name: String,
  val links: OrganizationLinksDto? = null,
)

@Serializable
data class OrganizationLinksDto(
  val organizationUrl: String? = null,
  val regionUrl: String? = null,
)

@Serializable
data class ProjectDto(
  val id: String,
  val slug: String,
  val name: String,
  val platform: String? = null,
  val environments: List<String> = emptyList(),
  /** False for projects the user can see listed but can't open (closed membership). */
  val hasAccess: Boolean = true,
)

@Serializable
data class IssueProjectDto(
  val id: String,
  val slug: String,
  val name: String? = null,
)

@Serializable
data class IssueDto(
  val id: String,
  val shortId: String,
  val title: String,
  val culprit: String? = null,
  val level: String? = null,
  val status: String? = null,
  /** new, ongoing, regressed, escalating, or an archived_* value. */
  val substatus: String? = null,
  val isUnhandled: Boolean = false,
  // Sentry sends count as a string and userCount as a number; accept either for both.
  @Serializable(with = FlexibleLongSerializer::class) val count: Long = 0,
  @Serializable(with = FlexibleLongSerializer::class) val userCount: Long = 0,
  val firstSeen: String? = null,
  val lastSeen: String? = null,
  val permalink: String? = null,
  val project: IssueProjectDto,
  /** Event counts keyed by period ("24h"): `[[unixSeconds, count], ...]`, oldest first. */
  val stats: Map<String, List<List<Double>>> = emptyMap(),
)

/** A whole number sent as a JSON number, a numeric string, or null (read as 0). */
object FlexibleLongSerializer : KSerializer<Long> {
  override val descriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

  override fun deserialize(decoder: Decoder): Long {
    val element = (decoder as JsonDecoder).decodeJsonElement()
    if (element is JsonNull) return 0
    val content = (element as? JsonPrimitive)?.content ?: throw SerializationException("Expected a number")
    return content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong() ?: throw SerializationException("Not a number")
  }

  override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}
