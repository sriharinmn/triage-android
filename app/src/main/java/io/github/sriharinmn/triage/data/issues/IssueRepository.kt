package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.SentryApi
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Why loading the inbox failed, in the terms the screen explains to the user. */
sealed interface InboxError {
  /** 401: the token was revoked or expired. */
  data object Unauthorized : InboxError

  /** 403: the token lacks a permission needed to read issues. */
  data object Forbidden : InboxError

  /** 429: Sentry asked us to wait [retryAfterSeconds]. */
  data class RateLimited(val retryAfterSeconds: Long?) : InboxError

  data object Network : InboxError

  /** Sentry answered with an error, or with something we couldn't read. */
  data object Server : InboxError
}

sealed interface LoadResult {
  data object Success : LoadResult

  data class Failure(val error: InboxError) : LoadResult
}

/** One cached list as the screen shows it. [refreshedAt] is null if the list was never loaded. */
data class InboxSnapshot(val issues: List<Issue>, val refreshedAt: Instant?, val hasMore: Boolean)

/**
 * Loads inbox lists from Sentry into the cache; the screen only ever reads the cache. After a 429 every call fails fast
 * until Sentry's `Retry-After` has passed.
 */
class IssueRepository(
  private val api: SentryApi,
  private val cache: IssueCache,
  private val clock: () -> Instant = Instant::now,
) {
  @Volatile private var rateLimitedUntil: Instant? = null

  val projects: Flow<List<Project>> = cache.observeProjects()

  fun observeInbox(filter: InboxFilter): Flow<InboxSnapshot> =
    combine(cache.observeIssues(filter.listKey), cache.observeListState(filter.listKey)) { issues, state ->
      InboxSnapshot(issues = issues, refreshedAt = state?.refreshedAt, hasMore = state?.nextCursor != null)
    }

  suspend fun lastRefreshed(filter: InboxFilter): Instant? = cache.listState(filter.listKey)?.refreshedAt

  /** Reloads the first page of [filter]'s list, replacing what is cached. */
  suspend fun refresh(account: Account, filter: InboxFilter): LoadResult {
    rateLimitFailure()?.let { return it }
    val result = api.listIssues(account.apiBaseUrl, account.token, account.orgSlug, filter.toParams(), cursor = null)
    return when (result) {
      is ApiResult.Success -> {
        val now = clock()
        cache.replaceList(filter.listKey, result.value.items.map { it.toIssue() }, result.value.nextCursor, now)
        cache.prune(now - CACHE_MAX_AGE)
        LoadResult.Success
      }
      is ApiResult.Failure -> failure(result.error)
    }
  }

  /** Appends the next page of [filter]'s list; does nothing on the last page. */
  suspend fun loadMore(account: Account, filter: InboxFilter): LoadResult {
    val cursor = cache.listState(filter.listKey)?.nextCursor ?: return LoadResult.Success
    rateLimitFailure()?.let { return it }
    val result = api.listIssues(account.apiBaseUrl, account.token, account.orgSlug, filter.toParams(), cursor)
    return when (result) {
      is ApiResult.Success -> {
        cache.appendPage(filter.listKey, result.value.items.map { it.toIssue() }, result.value.nextCursor)
        LoadResult.Success
      }
      is ApiResult.Failure -> failure(result.error)
    }
  }

  /** Reloads the projects (and their environments) offered by the filters. */
  suspend fun refreshProjects(account: Account): LoadResult {
    rateLimitFailure()?.let { return it }
    return when (val result = api.listProjects(account.apiBaseUrl, account.token, account.orgSlug)) {
      is ApiResult.Success -> {
        cache.replaceProjects(result.value.filter { it.hasAccess }.map { it.toProject() })
        LoadResult.Success
      }
      is ApiResult.Failure -> failure(result.error)
    }
  }

  /** Forgets every cached list and project; used on disconnect. */
  suspend fun clear() {
    cache.clear()
    rateLimitedUntil = null
  }

  private fun rateLimitFailure(): LoadResult.Failure? {
    val until = rateLimitedUntil ?: return null
    val remainingMillis = Duration.between(clock(), until).toMillis()
    if (remainingMillis <= 0) {
      rateLimitedUntil = null
      return null
    }
    return LoadResult.Failure(InboxError.RateLimited((remainingMillis + 999) / 1000))
  }

  private fun failure(error: ApiError): LoadResult.Failure =
    LoadResult.Failure(
      when (error) {
        ApiError.Unauthorized -> InboxError.Unauthorized
        ApiError.Forbidden -> InboxError.Forbidden
        is ApiError.RateLimited -> {
          val seconds = error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS
          rateLimitedUntil = clock().plusSeconds(seconds)
          InboxError.RateLimited(seconds)
        }
        is ApiError.Network -> InboxError.Network
        is ApiError.Http,
        ApiError.Parse -> InboxError.Server
      }
    )

  private companion object {
    val CACHE_MAX_AGE: Duration = Duration.ofDays(7)

    /** Used when Sentry sends 429 without a usable Retry-After. */
    const val DEFAULT_RETRY_AFTER_SECONDS = 60L
  }
}
