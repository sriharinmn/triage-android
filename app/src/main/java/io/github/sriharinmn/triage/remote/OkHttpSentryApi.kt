package io.github.sriharinmn.triage.remote

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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

  /** `{baseUrl}/api/0/{segments}/`, each segment percent-encoded, or null if [baseUrl] isn't a URL. */
  private fun apiUrl(baseUrl: String, vararg segments: String): HttpUrl? =
    baseUrl
      .trim()
      .toHttpUrlOrNull()
      ?.newBuilder()
      ?.apply {
        addPathSegments("api/0")
        segments.forEach { addPathSegment(it) }
        addPathSegment("") // Sentry's endpoints end with a slash.
      }
      ?.build()

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
    get(url, token) { body, link -> Page(decode(body), nextCursorFromLink(link)) }

  /** The one place a token leaves the app: https only, cancellable, and HTTP or network problems become values. */
  private suspend fun <T> get(url: HttpUrl?, token: String, decode: (body: String, link: String?) -> T): ApiResult<T> {
    if (url == null) return ApiResult.Failure(ApiError.Network("Invalid server URL"))
    if (requireHttps && !url.isHttps) return ApiResult.Failure(ApiError.Network("Server URL must use https"))
    val request =
      try {
        Request.Builder()
          .url(url)
          .header("Authorization", "Bearer $token")
          .header("Accept", "application/json")
          .header("User-Agent", userAgent)
          .get()
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
      in 200..299 ->
        try {
          ApiResult.Success(decode(body.string(), header("Link")))
        } catch (e: IllegalArgumentException) {
          // kotlinx SerializationException extends IllegalArgumentException; an empty body lands here too.
          ApiResult.Failure(ApiError.Parse)
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
  }
}
