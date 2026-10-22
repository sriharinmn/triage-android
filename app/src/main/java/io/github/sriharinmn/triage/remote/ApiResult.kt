package io.github.sriharinmn.triage.remote

/** Outcome of a Sentry API call. HTTP and network problems are values, not exceptions. */
sealed interface ApiResult<out T> {
  data class Success<T>(val value: T) : ApiResult<T>

  data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
  /** 401: token missing, revoked, or for a different server. */
  data object Unauthorized : ApiError

  /** 403: token lacks a required scope. */
  data object Forbidden : ApiError

  /** 429: back off; [retryAfterSeconds] comes from the Retry-After header when present. */
  data class RateLimited(val retryAfterSeconds: Long?) : ApiError

  data class Http(val code: Int) : ApiError

  data class Network(val message: String?) : ApiError

  /** 2xx response whose body didn't match the expected JSON. */
  data object Parse : ApiError
}
