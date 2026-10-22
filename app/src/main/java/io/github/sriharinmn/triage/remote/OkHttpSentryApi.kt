package io.github.sriharinmn.triage.remote

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpSentryApi(
  private val client: OkHttpClient,
  private val userAgent: String,
  private val json: Json = SentryJson,
) : SentryApi {

  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> =
    get(baseUrl, "api/0/organizations/", token) { body -> json.decodeFromString<List<OrganizationDto>>(body) }

  private suspend fun <T> get(baseUrl: String, path: String, token: String, decode: (String) -> T): ApiResult<T> {
    val url =
      "${baseUrl.trimEnd('/')}/$path".toHttpUrlOrNull()
        ?: return ApiResult.Failure(ApiError.Network("Invalid server URL"))
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
        }
      )
    }
  }

  private fun <T> Response.toApiResult(decode: (String) -> T): ApiResult<T> =
    when (code) {
      in 200..299 ->
        try {
          ApiResult.Success(decode(body.string()))
        } catch (e: IllegalArgumentException) {
          // kotlinx SerializationException extends IllegalArgumentException.
          ApiResult.Failure(ApiError.Parse)
        }
      401 -> ApiResult.Failure(ApiError.Unauthorized)
      403 -> ApiResult.Failure(ApiError.Forbidden)
      429 -> ApiResult.Failure(ApiError.RateLimited(header("Retry-After")?.toLongOrNull()))
      else -> ApiResult.Failure(ApiError.Http(code))
    }
}
