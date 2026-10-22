package io.github.sriharinmn.triage.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val SentryJson = Json { ignoreUnknownKeys = true }

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
