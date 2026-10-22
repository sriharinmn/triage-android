package io.github.sriharinmn.triage.data.issues

/** A Sentry project the user can open, with its environment names (sorted). */
data class Project(
  val id: String,
  val slug: String,
  val name: String,
  val platform: String?,
  val environments: List<String>,
)
