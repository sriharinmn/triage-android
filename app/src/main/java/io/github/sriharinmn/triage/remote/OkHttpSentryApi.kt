package io.github.sriharinmn.triage.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpSentryApi(
  private val client: OkHttpClient,
  private val userAgent: String,
  private val json: Json = SentryJson,
) : SentryApi {

  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> =
    get(baseUrl, "api/0/organizations/", token) { body -> json.decodeFromString<List<OrganizationDto>>(body) }

  private suspend fun <T> get(baseUrl: String, path: String, token: String, decode: (String) -> T): ApiResult<T> =
    withContext(Dispatchers.IO) {
      val url =
        "${baseUrl.trimEnd('/')}/$path".toHttpUrlOrNull()
          ?: return@withContext ApiResult.Failure(ApiError.Network("Invalid server URL"))
      val request =
        Request.Builder()
          .url(url)
          .header("Authorization", "Bearer $token")
          .header("Accept", "application/json")
          .header("User-Agent", userAgent)
          .get()
          .build()
      try {
        client.newCall(request).execute().use { response ->
          when (response.code) {
            in 200..299 ->
              try {
                ApiResult.Success(decode(response.body.string()))
              } catch (e: IllegalArgumentException) {
                // kotlinx SerializationException extends IllegalArgumentException.
                ApiResult.Failure(ApiError.Parse)
              }
            401 -> ApiResult.Failure(ApiError.Unauthorized)
            403 -> ApiResult.Failure(ApiError.Forbidden)
            429 -> ApiResult.Failure(ApiError.RateLimited(response.header("Retry-After")?.toLongOrNull()))
            else -> ApiResult.Failure(ApiError.Http(response.code))
          }
        }
      } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network(e.message))
      }
    }
}
