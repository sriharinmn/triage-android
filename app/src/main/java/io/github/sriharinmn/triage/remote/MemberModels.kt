package io.github.sriharinmn.triage.remote

import kotlinx.serialization.Serializable

/** One entry of `GET /organizations/{org}/members/`; [user] is null for an invite that hasn't been accepted yet. */
@Serializable
data class MemberDto(
  val id: String,
  val name: String? = null,
  val email: String? = null,
  val user: MemberUserDto? = null,
)

/** The Sentry user behind a membership; [id] is what `assignedTo: "user:<id>"` takes. */
@Serializable
data class MemberUserDto(
  val id: String,
  val name: String? = null,
  val email: String? = null,
)
