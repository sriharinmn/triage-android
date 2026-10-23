package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.SentryApi
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
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

  /** The phone couldn't save or read the cache, for example because its storage is full. */
  data object Storage : InboxError
}

/** Whether a load reached Sentry and updated the cache; failures say why in terms the screen explains. */
sealed interface LoadResult {
  data object Success : LoadResult

  data class Failure(val error: InboxError) : LoadResult
}

/** One cached list as the screen shows it. [refreshedAt] is null if the list was never loaded. */
data class InboxSnapshot(val issues: List<Issue>, val refreshedAt: Instant?, val hasMore: Boolean)

/**
 * Loads inbox lists from Sentry into the cache; the screen only ever reads the cache. After a 429 every call fails fast
 * until Sentry's `Retry-After` has passed. A cache that can't be written or read (a full phone) fails the call with
 * [InboxError.Storage] instead of throwing.
 */
class IssueRepository(
  private val api: SentryApi,
  private val cache: IssueCache,
  private val clock: () -> Instant = Instant::now,
) {
  /** Until when calls are held after a 429; updated atomically because loads run concurrently. */
  private val rateLimitedUntil = AtomicReference<Instant?>(null)

  val projects: Flow<List<Project>> = cache.observeProjects()

  fun observeInbox(filter: InboxFilter): Flow<InboxSnapshot> =
    combine(cache.observeIssues(filter.listKey), cache.observeListState(filter.listKey)) { issues, state ->
      InboxSnapshot(issues = issues, refreshedAt = state?.refreshedAt, hasMore = state?.nextCursor != null)
    }

  /** Null if the list was never loaded or the cache can't be read; either way the caller treats the list as stale. */
  suspend fun lastRefreshed(filter: InboxFilter): Instant? =
    try {
      cache.listState(filter.listKey)?.refreshedAt
    } catch (_: IOException) {
      // The refresh that a stale list triggers then reports the storage failure itself.
      null
    }

  /** Reloads the first page of [filter]'s list, replacing what is cached. */
  suspend fun refresh(account: Account, filter: InboxFilter): LoadResult {
    rateLimitFailure()?.let { return it }
    val result = api.listIssues(account.apiBaseUrl, account.token, account.orgSlug, filter.toParams(), cursor = null)
    return when (result) {
      is ApiResult.Success -> {
        val now = clock()
        storing {
          cache.replaceList(filter.listKey, result.value.items.map { it.toIssue() }, result.value.nextCursor, now)
          cache.prune(now - CACHE_MAX_AGE)
        }
      }
      is ApiResult.Failure -> failureAndHoldIfRateLimited(result.error)
    }
  }

  /** Appends the next page of [filter]'s list; does nothing on the last page. */
  suspend fun loadMore(account: Account, filter: InboxFilter): LoadResult {
    val listState =
      try {
        cache.listState(filter.listKey)
      } catch (_: IOException) {
        return LoadResult.Failure(InboxError.Storage)
      }
    val cursor = listState?.nextCursor ?: return LoadResult.Success
    rateLimitFailure()?.let { return it }
    val result = api.listIssues(account.apiBaseUrl, account.token, account.orgSlug, filter.toParams(), cursor)
    return when (result) {
      is ApiResult.Success ->
        storing { cache.appendPage(filter.listKey, result.value.items.map { it.toIssue() }, result.value.nextCursor) }
      is ApiResult.Failure -> failureAndHoldIfRateLimited(result.error)
    }
  }

  /** Reloads the projects (and their environments) offered by the filters. */
  suspend fun refreshProjects(account: Account): LoadResult {
    rateLimitFailure()?.let { return it }
    return when (val result = api.listProjects(account.apiBaseUrl, account.token, account.orgSlug)) {
      is ApiResult.Success ->
        storing { cache.replaceProjects(result.value.filter { it.hasAccess }.map { it.toProject() }) }
      is ApiResult.Failure -> failureAndHoldIfRateLimited(result.error)
    }
  }

  /** Forgets every cached list and project; used on disconnect. */
  suspend fun clear() {
    cache.clear()
    rateLimitedUntil.set(null)
  }

  /**
   * Runs cache writes; a storage failure becomes [InboxError.Storage]. It never starts a rate-limit hold: Sentry
   * answered, so the next call may ask again.
   */
  private inline fun storing(writes: () -> Unit): LoadResult =
    try {
      writes()
      LoadResult.Success
    } catch (_: IOException) {
      LoadResult.Failure(InboxError.Storage)
    }

  private fun rateLimitFailure(): LoadResult.Failure? {
    val until = rateLimitedUntil.get() ?: return null
    val remainingMillis = Duration.between(clock(), until).toMillis()
    if (remainingMillis <= 0) {
      // Clear only the hold we read, so a newer 429 that landed meanwhile isn't erased.
      rateLimitedUntil.compareAndSet(until, null)
      return null
    }
    return LoadResult.Failure(InboxError.RateLimited((remainingMillis + 999) / 1000))
  }

  /** Maps [error] for the screen and, for a 429, also starts holding every call until Sentry's Retry-After passes. */
  private fun failureAndHoldIfRateLimited(error: ApiError): LoadResult.Failure =
    LoadResult.Failure(
      when (error) {
        ApiError.Unauthorized -> InboxError.Unauthorized
        ApiError.Forbidden -> InboxError.Forbidden
        is ApiError.RateLimited -> {
          val seconds =
            (error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS)
              .coerceIn(MIN_RETRY_AFTER_SECONDS, MAX_RETRY_AFTER_SECONDS)
          holdUntilAtLeast(clock().plusSeconds(seconds))
          InboxError.RateLimited(seconds)
        }
        is ApiError.Network -> InboxError.Network
        is ApiError.Http,
        ApiError.Parse -> InboxError.Server
      }
    )

  /** Concurrent 429s never shorten an existing hold: the later instant wins. */
  private fun holdUntilAtLeast(candidate: Instant) {
    rateLimitedUntil.updateAndGet { current -> if (current == null || candidate > current) candidate else current }
  }

  private companion object {
    val CACHE_MAX_AGE: Duration = Duration.ofDays(7)

    /** Used when Sentry sends 429 without a usable Retry-After. */
    const val DEFAULT_RETRY_AFTER_SECONDS = 60L

    // Bounds on any Retry-After we obey: a server can't lock the app for longer than an hour, and 0 would mean no
    // back-off at all.
    const val MIN_RETRY_AFTER_SECONDS = 1L
    const val MAX_RETRY_AFTER_SECONDS = 3600L
  }
}
