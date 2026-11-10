package io.github.sriharinmn.triage.remote

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody

class OkHttpSentryApi(
  private val client: OkHttpClient,
  private val userAgent: String,
  /** Refuse to send the token over anything but https. Only tests against a plain-http MockWebServer turn this off. */
  private val requireHttps: Boolean = true,
  private val json: Json = SentryJson,
) : SentryApi {

  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> =
    getAllPages(apiUrl(baseUrl, "organizations"), token) { body -> json.decodeFromString<List<OrganizationDto>>(body) }

  override suspend fun listProjects(baseUrl: String, token: String, orgSlug: String): ApiResult<List<ProjectDto>> =
    getAllPages(apiUrl(baseUrl, "organizations", orgSlug, "projects"), token) { body ->
      json.decodeFromString<List<ProjectDto>>(body)
    }

  override suspend fun listIssues(
    baseUrl: String,
    token: String,
    orgSlug: String,
    params: IssueListParams,
    cursor: String?,
  ): ApiResult<Page<IssueDto>> {
    val url =
      apiUrl(baseUrl, "organizations", orgSlug, "issues")
        ?.newBuilder()
        ?.apply {
          addQueryParameter("query", params.query)
          addQueryParameter("project", params.projectId ?: ALL_PROJECTS)
          params.environment?.let { addQueryParameter("environment", it) }
          addQueryParameter("sort", params.sort)
          addQueryParameter("statsPeriod", params.statsPeriod)
          addQueryParameter("groupStatsPeriod", "24h")
          addQueryParameter("limit", params.limit.toString())
          // The inbox shows neither lifetime nor filtered stats; leaving them out makes the request cheaper for Sentry.
          addQueryParameter("collapse", "lifetime")
          addQueryParameter("collapse", "filtered")
        }
        ?.build()
    return getPage(url?.withCursor(cursor), token) { body -> json.decodeFromString<List<IssueDto>>(body) }
  }

  override suspend fun getIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
  ): ApiResult<IssueDetailDto> {
    // expand=inbox adds the "up for review" state the detail screen needs for Mark reviewed.
    val url = apiUrl(baseUrl, "organizations", orgSlug, "issues", issueId)?.newBuilder()?.addQueryParameter("expand", "inbox")?.build()
    return send(url, token) { body, _ -> json.decodeFromString<IssueDetailDto>(body) }
  }

  override suspend fun getIssueEvent(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    eventId: String,
  ): ApiResult<EventDto> =
    send(apiUrl(baseUrl, "organizations", orgSlug, "issues", issueId, "events", eventId), token) { body, _ ->
      json.decodeFromString<EventDto>(body)
    }

  override suspend fun getIssueTag(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    key: String,
  ): ApiResult<TagDetailDto> =
    send(apiUrl(baseUrl, "organizations", orgSlug, "issues", issueId, "tags", key), token) { body, _ ->
      json.decodeFromString<TagDetailDto>(body)
    }

  override suspend fun updateIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    changes: JsonObject,
  ): ApiResult<Unit> =
    send(apiUrl(baseUrl, "organizations", orgSlug, "issues", issueId), token, method = "PUT", jsonBody = changes.toString()) { _, _ -> }

  override suspend fun listMembers(baseUrl: String, token: String, orgSlug: String): ApiResult<List<MemberDto>> =
    getAllPages(apiUrl(baseUrl, "organizations", orgSlug, "members"), token) { body ->
      json.decodeFromString<List<MemberDto>>(body)
    }

  /**
   * `{baseUrl}/api/0/{segments}/`, each segment percent-encoded. Null if [baseUrl] isn't a URL, or if a segment is empty,
   * `.` or `..`: OkHttp resolves those, so a value taken from a response (a tag key) could point the token at another
   * endpoint.
   */
  private fun apiUrl(baseUrl: String, vararg segments: String): HttpUrl? {
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
    return baseUrl
      .trim()
      .toHttpUrlOrNull()
      ?.newBuilder()
      ?.apply {
        addPathSegments("api/0")
        segments.forEach { addPathSegment(it) }
        addPathSegment("") // Sentry's endpoints end with a slash.
      }
      ?.build()
  }

  /**
   * Follows next-page cursors until the last page, at most [MAX_PAGES] pages. Each page request is rebuilt from
   * [firstPage] plus the cursor, so the token never goes to a URL taken from a response.
   */
  private suspend fun <T> getAllPages(
    firstPage: HttpUrl?,
    token: String,
    decode: (String) -> List<T>,
  ): ApiResult<List<T>> {
    val items = mutableListOf<T>()
    var cursor: String? = null
    repeat(MAX_PAGES) {
      when (val page = getPage(firstPage?.withCursor(cursor), token, decode)) {
        is ApiResult.Failure -> return page
        is ApiResult.Success -> {
          items += page.value.items
          cursor = page.value.nextCursor ?: return ApiResult.Success(items)
        }
      }
    }
    return ApiResult.Success(items)
  }

  private suspend fun <T> getPage(url: HttpUrl?, token: String, decode: (String) -> List<T>): ApiResult<Page<T>> =
    send(url, token) { body, link -> Page(decode(body), nextCursorFromLink(link)) }

  /**
   * The one place a token leaves the app: https only, cancellable, and HTTP or network problems become values. A
   * [jsonBody] is sent as `application/json` (writes); reads send none.
   */
  private suspend fun <T> send(
    url: HttpUrl?,
    token: String,
    method: String = "GET",
    jsonBody: String? = null,
    decode: (body: String, link: String?) -> T,
  ): ApiResult<T> {
    if (url == null) return ApiResult.Failure(ApiError.Network("Invalid server URL"))
    if (requireHttps && !url.isHttps) return ApiResult.Failure(ApiError.Network("Server URL must use https"))
    val request =
      try {
        Request.Builder()
          .url(url)
          .header("Authorization", "Bearer $token")
          .header("Accept", "application/json")
          .header("User-Agent", userAgent)
          .method(method, jsonBody?.toRequestBody(JSON_MEDIA_TYPE))
          .build()
      } catch (_: IllegalArgumentException) {
        // A token with characters that are illegal in a header (newline, control, non-ASCII) can never be valid, so
        // report it as a rejected token. Nothing from the exception is kept: its message may echo the header value.
        return ApiResult.Failure(ApiError.Unauthorized)
      }
    return suspendCancellableCoroutine { continuation ->
      val call = client.newCall(request)
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onResponse(call: Call, response: Response) {
            // OkHttp swallows anything thrown from here, which would leave the caller suspended forever, so every
            // outcome must resume the continuation.
            try {
              continuation.resume(response.use { it.toApiResult(decode) })
            } catch (e: IOException) {
              continuation.resume(ApiResult.Failure(ApiError.Network(e.message)))
            } catch (e: Exception) {
              continuation.resumeWithException(e)
            }
          }

          override fun onFailure(call: Call, e: IOException) {
            continuation.resume(ApiResult.Failure(ApiError.Network(e.message)))
          }
        },
      )
    }
  }

  private fun <T> Response.toApiResult(decode: (body: String, link: String?) -> T): ApiResult<T> =
    when (code) {
      in 200..299 -> {
        val text = body.readAtMost(MAX_RESPONSE_BYTES)
        if (text == null) {
          ApiResult.Failure(ApiError.TooLarge)
        } else {
          try {
            ApiResult.Success(decode(text, header("Link")))
          } catch (e: IllegalArgumentException) {
            // kotlinx SerializationException extends IllegalArgumentException; an empty body lands here too.
            ApiResult.Failure(ApiError.Parse)
          }
        }
      }
      401 -> ApiResult.Failure(ApiError.Unauthorized)
      403 -> ApiResult.Failure(ApiError.Forbidden)
      429 -> ApiResult.Failure(ApiError.RateLimited(parseRetryAfter(header("Retry-After"))))
      else -> ApiResult.Failure(ApiError.Http(code))
    }

  private fun HttpUrl.withCursor(cursor: String?): HttpUrl =
    if (cursor == null) this else newBuilder().setQueryParameter("cursor", cursor).build()

  private companion object {
    /** 10 pages of 100 organizations or projects is far beyond any account we expect; it stops a runaway loop. */
    const val MAX_PAGES = 10

    /** Sentry's `project` value for "every project this token can access". */
    const val ALL_PROJECTS = "-1"

    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}

/** Responses bigger than this aren't read. Real issue events are far smaller; this only stops a pathological one. */
internal const val MAX_RESPONSE_BYTES: Long = 8L * 1024 * 1024

/** The body as UTF-8 text, or null if it is longer than [limit] bytes; never buffers more than [limit] + 1 bytes. */
private fun ResponseBody.readAtMost(limit: Long): String? {
  if (contentLength() > limit) return null
  val source = source()
  // request() is true once the buffer holds limit + 1 bytes; false means the body ended within the limit.
  if (source.request(limit + 1)) return null
  return source.buffer.readUtf8()
}
