package io.github.sriharinmn.triage.remote

/**
 * Filters for one issue-list request. [query] is Sentry search syntax (e.g. `is:unresolved`), [sort] one of Sentry's sort
 * values (`date`, `new`, `freq`, `user`). An empty [projectIds] means every project the token can see (the inbox's "All
 * projects"); the background check names several. A null [environment] means all of them.
 */
data class IssueListParams(
  val query: String,
  val projectIds: List<String>,
  val environment: String?,
  val sort: String,
  val statsPeriod: String = "14d",
  val limit: Int = 25,
)
