package io.github.sriharinmn.triage.data.account

/**
 * A connected Sentry organization.
 *
 * [apiBaseUrl] is where org-scoped API calls go (e.g. https://de.sentry.io for EU orgs); [webBaseUrl]
 * is where "Open in browser" links point.
 */
data class Account(
  val apiBaseUrl: String,
  val webBaseUrl: String,
  val token: String,
  val orgSlug: String,
  val orgName: String,
) {
  override fun toString(): String =
    "Account(apiBaseUrl=$apiBaseUrl, webBaseUrl=$webBaseUrl, token=<redacted>, orgSlug=$orgSlug, orgName=$orgName)"
}
