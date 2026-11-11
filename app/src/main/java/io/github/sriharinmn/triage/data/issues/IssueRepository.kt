package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.SentryApi
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * [InboxError.Storage] instead of throwing. A load that started before a [clear] writes nothing, whoever started it.
 */
class IssueRepository(
  private val api: SentryApi,
  private val cache: IssueCache,
  private val clock: () -> Instant = Instant::now,
  /** Shared app-wide in production, so a 429 here also holds the issue detail's calls. */
  private val gate: RateLimitGate = RateLimitGate(clock),
) {
  val projects: Flow<List<Project>> = cache.observeProjects()

  /**
   * Bumped by [clear]. Loads note it when they start and skip their writes if it has changed, because their data belongs
   * to a forgotten account: a reconnect from the issue detail doesn't stop the inbox's loads.
   */
  private val generation = AtomicLong()

  /** Makes a load's generation check and its writes one step, so a [clear] can't land between them. */
  private val writeLock = Mutex()

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
    val startedIn = generation.get()
    rateLimitFailure()?.let { return it }
    val result = api.listIssues(account.apiBaseUrl, account.token, account.orgSlug, filter.toParams(), cursor = null)
    return when (result) {
      is ApiResult.Success -> {
        val now = clock()
        storing(startedIn) {
          cache.replaceList(filter.listKey, result.value.items.map { it.toIssue() }, result.value.nextCursor, now)
          cache.prune(now - CACHE_MAX_AGE)
        }
      }
      is ApiResult.Failure -> failureAndHoldIfRateLimited(result.error)
    }
  }

  /** Appends the next page of [filter]'s list; does nothing on the last page. */
  suspend fun loadMore(account: Account, filter: InboxFilter): LoadResult {
    val startedIn = generation.get()
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
        storing(startedIn) {
          cache.appendPage(filter.listKey, result.value.items.map { it.toIssue() }, result.value.nextCursor)
        }
      is ApiResult.Failure -> failureAndHoldIfRateLimited(result.error)
    }
  }

  /** Reloads the projects (and their environments) offered by the filters. */
  suspend fun refreshProjects(account: Account): LoadResult {
    val startedIn = generation.get()
    rateLimitFailure()?.let { return it }
    return when (val result = api.listProjects(account.apiBaseUrl, account.token, account.orgSlug)) {
      is ApiResult.Success ->
        storing(startedIn) { cache.replaceProjects(result.value.filter { it.hasAccess }.map { it.toProject() }) }
      is ApiResult.Failure -> failureAndHoldIfRateLimited(result.error)
    }
  }

  /**
   * Takes [issueId] out of the cached lists once Sentry accepted an action on it: every list, or only [tab]'s. Like a
   * load, it writes nothing if the account's data was forgotten meanwhile.
   */
  suspend fun forget(issueId: String, tab: InboxTab? = null): LoadResult {
    val startedIn = generation.get()
    return storing(startedIn) { cache.removeFromLists(issueId, tab?.listKeyPrefix ?: "") }
  }

  /** Forgets every cached list and project and lifts the shared rate-limit hold; used when the account's data is forgotten. */
  suspend fun clear() {
    writeLock.withLock {
      cache.clear()
      // Bumped after the clear: if the clear fails, the account stays connected and its loads may still save. The lock
      // keeps any write from landing in between.
      generation.incrementAndGet()
    }
    gate.clear()
  }

  /**
   * Runs the cache writes of a load that started in generation [startedIn]; a storage failure becomes
   * [InboxError.Storage]. If the account's data was forgotten since, nothing is written and there is nothing to report.
   * It never starts a rate-limit hold: Sentry answered, so the next call may ask again.
   */
  private suspend inline fun storing(startedIn: Long, writes: () -> Unit): LoadResult =
    writeLock.withLock {
      if (generation.get() != startedIn) return LoadResult.Success
      try {
        writes()
        LoadResult.Success
      } catch (_: IOException) {
        LoadResult.Failure(InboxError.Storage)
      }
    }

  private fun rateLimitFailure(): LoadResult.Failure? =
    gate.remainingSeconds()?.let { LoadResult.Failure(InboxError.RateLimited(it)) }

  /** Maps [error] for the screen and, for a 429, also starts holding every call until Sentry's Retry-After passes. */
  private fun failureAndHoldIfRateLimited(error: ApiError): LoadResult.Failure =
    LoadResult.Failure(
      when (error) {
        ApiError.Unauthorized -> InboxError.Unauthorized
        ApiError.Forbidden -> InboxError.Forbidden
        is ApiError.RateLimited -> InboxError.RateLimited(gate.hold(error.retryAfterSeconds))
        is ApiError.Network -> InboxError.Network
        is ApiError.Http,
        ApiError.Parse,
        ApiError.TooLarge -> InboxError.Server
      }
    )

  private companion object {
    val CACHE_MAX_AGE: Duration = Duration.ofDays(7)
  }
}
