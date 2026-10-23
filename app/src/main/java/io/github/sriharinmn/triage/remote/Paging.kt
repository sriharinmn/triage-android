package io.github.sriharinmn.triage.remote

/** One page of a cursor-paginated Sentry list; [nextCursor] is null on the last page. */
data class Page<T>(val items: List<T>, val nextCursor: String?)

// One `<url>; key="value"; ...` entry of a Link header. The URL is skipped whole, so commas inside it don't split entries.
private val LINK_ENTRY = Regex("""<[^>]*>((?:\s*;\s*[A-Za-z]+\s*=\s*"[^"]*")*)""")
private val LINK_PARAM = Regex("""([A-Za-z]+)\s*=\s*"([^"]*)"""")

/**
 * The cursor of the next page from Sentry's `Link` header, or null on the last page. Sentry always sends a `rel="next"`
 * entry; only `results="true"` means that page has data.
 */
fun nextCursorFromLink(header: String?): String? {
  if (header.isNullOrBlank()) return null
  for (entry in LINK_ENTRY.findAll(header)) {
    val params = LINK_PARAM.findAll(entry.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
    if (params["rel"] != "next") continue
    return if (params["results"] == "true") params["cursor"]?.takeIf { it.isNotEmpty() } else null
  }
  return null
}
