package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.remote.IssueListParams
import java.net.URLEncoder

/** The inbox tabs, with the same search queries Sentry's web app uses for them. */
enum class InboxTab(val query: String) {
  ForReview("is:unresolved is:for_review assigned_or_suggested:[me, my_teams, none]"),
  Unresolved("is:unresolved"),
  Regressed("is:regressed"),
  Escalating("is:escalating"),
}

/** The inbox sort orders, with the value Sentry's API expects for each. */
enum class IssueSort(val apiValue: String) {
  LastSeen("date"),
  FirstSeen("new"),
  Events("freq"),
  Users("user"),
}

/** What the inbox shows. Each distinct filter is cached as its own list. */
data class InboxFilter(
  val tab: InboxTab = InboxTab.Unresolved,
  /** Null means all projects. */
  val projectId: String? = null,
  /** Null means all environments. */
  val environment: String? = null,
  val sort: IssueSort = IssueSort.LastSeen,
) {
  /**
   * The cache key: `tab|project|environment|sort`. "All" is `*`; a chosen value is `=` plus its URL-encoding, so no
   * project id or environment name can make two filters share a key.
   */
  val listKey: String
    get() = listOf(tab.name, projectId.keyPart(), environment.keyPart(), sort.name).joinToString("|")

  /** The request parameters for this filter's first page. */
  fun toParams(): IssueListParams =
    IssueListParams(query = tab.query, projectId = projectId, environment = environment, sort = sort.apiValue)

  private fun String?.keyPart(): String = if (this == null) "*" else "=" + URLEncoder.encode(this, "UTF-8")
}
