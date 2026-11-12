package io.github.sriharinmn.triage.remote

import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

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
  /**
   * The server's certificate was refused: one the phone doesn't trust, an expired one, or one for another name. Not a
   * secure connection that merely dropped: that is [Other].
   */
  Tls,
  /** The server's name didn't resolve: a mistyped address, or no connection at all. */
  UnknownHost,
  Other,
}

/**
 * Sorts a connection failure into a [NetworkCause]. Only a certificate problem is [NetworkCause.Tls], by OkHttp's own
 * rule for what retrying can't fix (`RetryAndFollowUpInterceptor.isRecoverable`): a peer that failed verification, or a
 * handshake that failed over a [CertificateException]. Android reports ordinary drops (a reset, an abort, a handshake
 * closed by the peer) as other `SSLException`s; those are [NetworkCause.Other].
 */
internal fun IOException.networkCause(): NetworkCause =
  when {
    this is SSLPeerUnverifiedException -> NetworkCause.Tls
    this is SSLHandshakeException && causedByCertificate() -> NetworkCause.Tls
    this is UnknownHostException -> NetworkCause.UnknownHost
    else -> NetworkCause.Other
  }

/** True if a [CertificateException] is somewhere in this exception's causes, looking only so deep in case they loop. */
private fun Throwable.causedByCertificate(): Boolean =
  generateSequence(cause) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is CertificateException }

private const val MAX_CAUSE_DEPTH = 8
