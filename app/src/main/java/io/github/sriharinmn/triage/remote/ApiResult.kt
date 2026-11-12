package io.github.sriharinmn.triage.remote

import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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

  /** No usable answer: offline, a timeout, or a connection that couldn't be made; [cause] says which, when it can tell. */
  data class Network(val message: String?, val cause: NetworkCause = NetworkCause.Other) : ApiError

  /** 2xx response whose body didn't match the expected JSON. */
  data object Parse : ApiError

  /** 2xx response bigger than [MAX_RESPONSE_BYTES]; it is not read, so a huge event can't exhaust memory. */
  data object TooLarge : ApiError
}

/** Why a request got no answer, as far as the connection can tell. */
enum class NetworkCause {
  /** The secure connection failed: a certificate the phone doesn't trust, an expired one, or one for another name. */
  Tls,
  /** The server's name didn't resolve: a mistyped address, or no connection at all. */
  UnknownHost,
  Other,
}

/** Sorts a connection failure into a [NetworkCause]. */
internal fun IOException.networkCause(): NetworkCause =
  when (this) {
    is SSLException -> NetworkCause.Tls
    is UnknownHostException -> NetworkCause.UnknownHost
    else -> NetworkCause.Other
  }
