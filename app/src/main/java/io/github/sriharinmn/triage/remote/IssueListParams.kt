package io.github.sriharinmn.triage.remote

/**
 * Filters for one issue-list request. [query] is Sentry search syntax (e.g. `is:unresolved`), [sort] one of Sentry's sort
 * values (`date`, `new`, `freq`, `user`). A null [projectId] or [environment] means all of them.
 */
data class IssueListParams(
  val query: String,
  val projectId: String?,
  val environment: String?,
  val sort: String,
  val statsPeriod: String = "14d",
  val limit: Int = 25,
)
