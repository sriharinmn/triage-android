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

private val MALFORMED_SCHEME = Regex("(?i)^https?[:/]")

/**
 * Turns whatever the user typed into `https://host[:port][/path]` with no trailing slash, or null
 * if it isn't a usable https URL. A pasted `/api/0` suffix is removed. A scheme typed without its
 * `://` (such as `https:/host`) is rejected rather than read as a host name.
 */
fun normalizeBaseUrl(input: String): String? {
  var text = input.trim()
  if (text.isEmpty() || text.any { it.isWhitespace() }) return null
  if (!text.contains("://")) {
    if (MALFORMED_SCHEME.containsMatchIn(text)) return null
    text = "https://$text"
  }
  val url = text.toHttpUrlOrNull() ?: return null
  if (url.scheme != "https") return null

  val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
  if (segments.takeLast(2) == listOf("api", "0")) {
    segments.removeAt(segments.lastIndex)
    segments.removeAt(segments.lastIndex)
  }
  val path = if (segments.isEmpty()) "" else segments.joinToString(separator = "/", prefix = "/")
  val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
  // OkHttp strips the brackets from IPv6 literals; put them back so the result is a valid URL.
  val host = if (':' in url.host) "[${url.host}]" else url.host
  return "https://$host$port$path"
}
