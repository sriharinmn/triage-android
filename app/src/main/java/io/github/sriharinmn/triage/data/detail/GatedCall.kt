package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult

/**
 * Runs one Sentry call for the detail screen, an issue action or the member list. Inside a 429's hold it fails fast
 * without asking Sentry. A 429 starts the app-wide hold, and other failures map to what the screens explain.
 */
internal suspend fun <T> RateLimitGate.call(request: suspend () -> ApiResult<T>): DetailResult<T> {
  remainingSeconds()?.let { return DetailResult.Failure(DetailError.RateLimited(it)) }
  return when (val result = request()) {
    is ApiResult.Success -> DetailResult.Success(result.value)
    is ApiResult.Failure -> DetailResult.Failure(result.error.toDetailError(this))
  }
}

private fun ApiError.toDetailError(gate: RateLimitGate): DetailError =
  when (this) {
    ApiError.Unauthorized -> DetailError.Unauthorized
    ApiError.Forbidden -> DetailError.Forbidden
    is ApiError.RateLimited -> DetailError.RateLimited(gate.hold(retryAfterSeconds))
    is ApiError.Http -> if (code == 404) DetailError.NotFound else DetailError.Server
    is ApiError.Network -> DetailError.Network
    ApiError.Parse -> DetailError.Server
    ApiError.TooLarge -> DetailError.TooLarge
  }
