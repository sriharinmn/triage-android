package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.remote.SentryApi
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Why a detail-screen load or an issue action failed, in the terms the screens explain to the user. */
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
 * Responses are turned into the screen's models on [mappingDispatcher], off the caller's (main) thread, because a big
 * event takes a while to map.
 */
class IssueDetailRepository(
  private val api: SentryApi,
  private val cache: IssueCache,
  private val gate: RateLimitGate,
  private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    gate.call { api.getIssue(account.apiBaseUrl, account.token, account.orgSlug, issueId) }.mapped { it.toSummary(cached) }

  suspend fun event(account: Account, issueId: String, kind: EventKind): DetailResult<IssueEvent> =
    gate.call { api.getIssueEvent(account.apiBaseUrl, account.token, account.orgSlug, issueId, kind.apiId) }
      .mapped { it.toIssueEvent() }

  /**
   * The top value of each tag that [chooseTagKeys] picks from [keys], loaded in parallel and kept in that order. Tags
   * Sentry can't give are left out; the call fails only if every request failed.
   */
  suspend fun tags(account: Account, issueId: String, keys: List<String>): DetailResult<List<TagSummary>> {
    val chosen = chooseTagKeys(keys)
    if (chosen.isEmpty()) return DetailResult.Success(emptyList())
    val results = coroutineScope {
      chosen
        .map { key -> async { gate.call { api.getIssueTag(account.apiBaseUrl, account.token, account.orgSlug, issueId, key) } } }
        .awaitAll()
    }
    val failures = results.filterIsInstance<DetailResult.Failure>()
    if (failures.size == results.size) return failures.first()
    return DetailResult.Success(
      withContext(mappingDispatcher) {
        results.mapNotNull { result ->
          when (result) {
            is DetailResult.Success -> result.value.toTagSummary()
            is DetailResult.Failure -> null
          }
        }
      }
    )
  }

  /** A loaded value as the screen's model, mapped on [mappingDispatcher]; a failure passes through without a switch. */
  private suspend fun <T, R> DetailResult<T>.mapped(transform: (T) -> R): DetailResult<R> =
    when (this) {
      is DetailResult.Success -> DetailResult.Success(withContext(mappingDispatcher) { transform(value) })
      is DetailResult.Failure -> this
    }
}
