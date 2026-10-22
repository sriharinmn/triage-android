package io.github.sriharinmn.triage.data.account

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the user's Sentry lives. */
sealed interface SentryHost {
  val baseUrl: String

  val tokenPageUrl: String
    get() = "$baseUrl/settings/account/api/auth-tokens/"

  data object Cloud : SentryHost {
    override val baseUrl = "https://sentry.io"
  }

  data class SelfHosted(override val baseUrl: String) : SentryHost
}

/**
 * Turns whatever the user typed into `https://host[:port][/path]` with no trailing slash, or null
 * if it isn't a usable https URL. A pasted `/api/0` suffix is removed.
 */
fun normalizeBaseUrl(input: String): String? {
  var text = input.trim()
  if (text.isEmpty() || text.any { it.isWhitespace() }) return null
  if (!text.contains("://")) text = "https://$text"
  val url = text.toHttpUrlOrNull() ?: return null
  if (url.scheme != "https") return null

  val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
  if (segments.takeLast(2) == listOf("api", "0")) {
    segments.removeAt(segments.lastIndex)
    segments.removeAt(segments.lastIndex)
  }
  val path = if (segments.isEmpty()) "" else segments.joinToString(separator = "/", prefix = "/")
  val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
  return "https://${url.host}$port$path"
}
