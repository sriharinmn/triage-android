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

/** The first path segments of Sentry's API; a pasted API address keeps only the part before them. */
private val API_ROOT = listOf("api", "0")

/** Path segments of Sentry's web app; a pasted page address keeps only the part before the first of them. */
private val WEB_APP_SECTIONS = setOf("organizations", "settings", "auth")

/**
 * Turns whatever the user typed into `https://host[:port][/path]` with no trailing slash, or null if it isn't a usable
 * https URL. So that pasting any Sentry address works, the path is cut before the first `api/0` segment pair (an API
 * address) or the first `organizations`, `settings` or `auth` segment (a page of Sentry's web app: an issue list, the
 * token page, the login page), whichever comes first; what is left is the server's own subpath, if any. A scheme typed
 * without its `://` (such as `https:/host`) is rejected rather than read as a host name. The host comes out lowercased;
 * credentials, query and fragment are dropped.
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

  val segments = url.pathSegments.filter { it.isNotEmpty() }
  val cuts = listOf(segments.windowed(2).indexOf(API_ROOT), segments.indexOfFirst { it in WEB_APP_SECTIONS })
  val kept = cuts.filter { it >= 0 }.minOrNull() ?: segments.size
  val path = segments.take(kept).joinToString(separator = "") { "/$it" }
  val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
  // OkHttp strips the brackets from IPv6 literals; put them back so the result is a valid URL.
  val host = if (':' in url.host) "[${url.host}]" else url.host
  return "https://$host$port$path"
}
