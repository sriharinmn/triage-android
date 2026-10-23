package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.SentryApi
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Why part of the detail screen couldn't load, in the terms the screen explains to the user. */
sealed interface DetailError {
  /** 404: the issue (or this kind of event) no longer exists, e.g. it was merged or deleted. */
  data object NotFound : DetailError

  /** 401: the token was revoked or expired. */
  data object Unauthorized : DetailError

  /** 403: the token can't read this issue. */
  data object Forbidden : DetailError

  /** A 429, or a call made inside an earlier 429's hold: wait [retryAfterSeconds]. */
  data class RateLimited(val retryAfterSeconds: Long) : DetailError

  data object Network : DetailError

  /** Sentry answered with an error, or with something we couldn't read. */
  data object Server : DetailError

  /** The response was bigger than the app will read. */
  data object TooLarge : DetailError
}

/** A loaded value, or why it couldn't be loaded. */
sealed interface DetailResult<out T> {
  data class Success<T>(val value: T) : DetailResult<T>

  data class Failure(val error: DetailError) : DetailResult<Nothing>
}

/**
 * Loads the detail screen's parts straight from Sentry. Nothing is written to disk, because events can carry end users'
 * personal data; only the summary's first draft comes from the inbox cache. Every call honours the app-wide [gate].
 */
class IssueDetailRepository(
  private val api: SentryApi,
  private val cache: IssueCache,
  private val gate: RateLimitGate,
) {
  /** The cached inbox row as a summary, for an instant (and offline) header; null if it isn't cached or can't be read. */
  suspend fun cachedSummary(issueId: String): IssueSummary? =
    try {
      cache.issue(issueId)?.toSummary()
    } catch (_: IOException) {
      null
    }

  /** The fresh issue; [cached] fills in what Sentry's response leaves out. */
  suspend fun summary(account: Account, issueId: String, cached: IssueSummary?): DetailResult<IssueSummary> =
    call { api.getIssue(account.apiBaseUrl, account.token, account.orgSlug, issueId) }.map { it.toSummary(cached) }

  suspend fun event(account: Account, issueId: String, kind: EventKind): DetailResult<IssueEvent> =
    call { api.getIssueEvent(account.apiBaseUrl, account.token, account.orgSlug, issueId, kind.apiId) }
      .map { it.toIssueEvent() }

  /**
   * The top value of each tag that [chooseTagKeys] picks from [keys], loaded in parallel and kept in that order. Tags
   * Sentry can't give are left out; the call fails only if every request failed.
   */
  suspend fun tags(account: Account, issueId: String, keys: List<String>): DetailResult<List<TagSummary>> {
    val chosen = chooseTagKeys(keys)
    if (chosen.isEmpty()) return DetailResult.Success(emptyList())
    val results = coroutineScope {
      chosen
        .map { key -> async { call { api.getIssueTag(account.apiBaseUrl, account.token, account.orgSlug, issueId, key) } } }
        .awaitAll()
    }
    val failures = results.filterIsInstance<DetailResult.Failure>()
    if (failures.size == results.size) return failures.first()
    return DetailResult.Success(
      results.mapNotNull { result ->
        when (result) {
          is DetailResult.Success -> result.value.toTagSummary()
          is DetailResult.Failure -> null
        }
      }
    )
  }

  /** Fails fast inside a 429's hold, so Sentry isn't asked again before its `Retry-After` has passed. */
  private suspend fun <T> call(request: suspend () -> ApiResult<T>): DetailResult<T> {
    gate.remainingSeconds()?.let { return DetailResult.Failure(DetailError.RateLimited(it)) }
    return when (val result = request()) {
      is ApiResult.Success -> DetailResult.Success(result.value)
      is ApiResult.Failure -> DetailResult.Failure(result.error.toDetailError())
    }
  }

  /** Maps this error for the screen and, for a 429, starts the app-wide hold. */
  private fun ApiError.toDetailError(): DetailError =
    when (this) {
      ApiError.Unauthorized -> DetailError.Unauthorized
      ApiError.Forbidden -> DetailError.Forbidden
      is ApiError.RateLimited -> DetailError.RateLimited(gate.hold(retryAfterSeconds))
      is ApiError.Http -> if (code == 404) DetailError.NotFound else DetailError.Server
      is ApiError.Network -> DetailError.Network
      ApiError.Parse -> DetailError.Server
      ApiError.TooLarge -> DetailError.TooLarge
    }
}

private inline fun <T, R> DetailResult<T>.map(transform: (T) -> R): DetailResult<R> =
  when (this) {
    is DetailResult.Success -> DetailResult.Success(transform(value))
    is DetailResult.Failure -> this
  }
