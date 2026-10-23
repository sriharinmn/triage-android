package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import io.github.sriharinmn.triage.testing.projectDto
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueRepositoryTest {
  private val api = FakeSentryApi()
  private val cache = InMemoryIssueCache()
  private val clock = TestClock()
  private val repository = IssueRepository(api, cache, clock::now)
  private val filter = InboxFilter()

  private fun page(vararg ids: String, next: String? = null): ApiResult<Page<IssueDto>> =
    ApiResult.Success(Page(ids.map { issueDto(it) }, next))

  private suspend fun shownIds() = repository.observeInbox(filter).first().issues.map { it.id }

  @Test
  fun refresh_storesTheFirstPageWithItsCursorAndTime() = runTest {
    api.issuesHandler = { page("1", "2", next = "c2") }

    assertEquals(LoadResult.Success, repository.refresh(TEST_ACCOUNT, filter))

    val snapshot = repository.observeInbox(filter).first()
    assertEquals(listOf("1", "2"), snapshot.issues.map { it.id })
    assertEquals(clock.now(), snapshot.refreshedAt)
    assertTrue(snapshot.hasMore)
    assertEquals(clock.now(), repository.lastRefreshed(filter))
  }

  @Test
  fun refresh_sendsTheAccountAndFilter() = runTest {
    val regressedInProduction = InboxFilter(InboxTab.Regressed, "102", "production", IssueSort.Events)

    repository.refresh(TEST_ACCOUNT, regressedInProduction)

    val call = api.issueCalls.single()
    assertEquals("https://de.sentry.io", call.baseUrl)
    assertEquals("sntryu_test", call.token)
    assertEquals("example-org", call.orgSlug)
    assertEquals(regressedInProduction.toParams(), call.params)
    assertNull(call.cursor)
  }

  @Test
  fun refresh_replacesThePreviousList() = runTest {
    api.issuesHandler = { page("1", "2") }
    repository.refresh(TEST_ACCOUNT, filter)
    api.issuesHandler = { page("3") }

    repository.refresh(TEST_ACCOUNT, filter)

    assertEquals(listOf("3"), shownIds())
  }

  @Test
  fun loadMore_appendsTheNextPageUsingTheStoredCursor() = runTest {
    api.issuesHandler = { call -> if (call.cursor == null) page("1", "2", next = "c2") else page("2", "3") }
    repository.refresh(TEST_ACCOUNT, filter)

    assertEquals(LoadResult.Success, repository.loadMore(TEST_ACCOUNT, filter))

    assertEquals("c2", api.issueCalls.last().cursor)
    assertEquals(listOf("1", "2", "3"), shownIds())
    assertFalse(repository.observeInbox(filter).first().hasMore)
  }

  @Test
  fun loadMore_onTheLastPage_doesNotCallSentry() = runTest {
    api.issuesHandler = { page("1") }
    repository.refresh(TEST_ACCOUNT, filter)

    assertEquals(LoadResult.Success, repository.loadMore(TEST_ACCOUNT, filter))

    assertEquals(1, api.issueCalls.size)
  }

  @Test
  fun refreshFailure_keepsTheCachedList() = runTest {
    api.issuesHandler = { page("1") }
    repository.refresh(TEST_ACCOUNT, filter)
    api.issuesHandler = { ApiResult.Failure(ApiError.Network("offline")) }

    assertEquals(LoadResult.Failure(InboxError.Network), repository.refresh(TEST_ACCOUNT, filter))

    assertEquals(listOf("1"), shownIds())
  }

  @Test
  fun apiErrors_mapToInboxErrors() = runTest {
    val cases =
      mapOf(
        ApiError.Unauthorized to InboxError.Unauthorized,
        ApiError.Forbidden to InboxError.Forbidden,
        ApiError.Network("x") to InboxError.Network,
        ApiError.Http(502) to InboxError.Server,
        ApiError.Parse to InboxError.Server,
      )
    for ((apiError, expected) in cases) {
      api.issuesHandler = { ApiResult.Failure(apiError) }
      assertEquals("for $apiError", LoadResult.Failure(expected), repository.refresh(TEST_ACCOUNT, filter))
    }
  }

  @Test
  fun rateLimited_holdsEveryCallUntilRetryAfterPasses() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(30)) }
    assertEquals(LoadResult.Failure(InboxError.RateLimited(30)), repository.refresh(TEST_ACCOUNT, filter))

    clock.advance(Duration.ofSeconds(10))
    api.issuesHandler = { page("1") }
    assertEquals(LoadResult.Failure(InboxError.RateLimited(20)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(LoadResult.Failure(InboxError.RateLimited(20)), repository.refreshProjects(TEST_ACCOUNT))
    assertEquals(1, api.issueCalls.size)
    assertTrue(api.projectCalls.isEmpty())

    clock.advance(Duration.ofSeconds(20))
    assertEquals(LoadResult.Success, repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(2, api.issueCalls.size)
  }

  @Test
  fun rateLimitedWithoutRetryAfter_waitsSixtySeconds() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(null)) }
    assertEquals(LoadResult.Failure(InboxError.RateLimited(60)), repository.refresh(TEST_ACCOUNT, filter))

    clock.advance(Duration.ofSeconds(59))
    assertEquals(LoadResult.Failure(InboxError.RateLimited(1)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(1, api.issueCalls.size)
  }

  @Test
  fun refreshProjects_keepsOnlyProjectsTheUserCanOpen() = runTest {
    api.projectsResult =
      ApiResult.Success(listOf(projectDto("101", listOf("production")), projectDto("103", hasAccess = false)))

    assertEquals(LoadResult.Success, repository.refreshProjects(TEST_ACCOUNT))

    assertEquals(listOf("example-org"), api.projectCalls)
    assertEquals(listOf("101"), repository.projects.first().map { it.id })
  }

  @Test
  fun refresh_prunesListsNotRefreshedForAWeek() = runTest {
    val oldFilter = filter.copy(tab = InboxTab.Escalating)
    api.issuesHandler = { page("1") }
    repository.refresh(TEST_ACCOUNT, oldFilter)

    clock.advance(Duration.ofDays(8))
    repository.refresh(TEST_ACCOUNT, filter)

    assertNull(repository.lastRefreshed(oldFilter))
    assertTrue(repository.observeInbox(oldFilter).first().issues.isEmpty())
    assertEquals(listOf("1"), shownIds())
  }

  @Test
  fun loadMore_isHeldWhileRateLimited() = runTest {
    api.issuesHandler = { page("1", next = "c2") }
    repository.refresh(TEST_ACCOUNT, filter)
    api.projectsResult = ApiResult.Failure(ApiError.RateLimited(30))
    assertEquals(LoadResult.Failure(InboxError.RateLimited(30)), repository.refreshProjects(TEST_ACCOUNT))

    assertEquals(LoadResult.Failure(InboxError.RateLimited(30)), repository.loadMore(TEST_ACCOUNT, filter))

    assertEquals(1, api.issueCalls.size)
  }

  @Test
  fun aRateLimitedLoadMore_holdsEveryOtherCall() = runTest {
    api.issuesHandler = { call ->
      if (call.cursor == null) page("1", next = "c2") else ApiResult.Failure(ApiError.RateLimited(20))
    }
    repository.refresh(TEST_ACCOUNT, filter)

    assertEquals(LoadResult.Failure(InboxError.RateLimited(20)), repository.loadMore(TEST_ACCOUNT, filter))

    assertEquals(LoadResult.Failure(InboxError.RateLimited(20)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(LoadResult.Failure(InboxError.RateLimited(20)), repository.refreshProjects(TEST_ACCOUNT))
    assertEquals(2, api.issueCalls.size)
    assertTrue(api.projectCalls.isEmpty())
  }

  @Test
  fun aFailedLoadMore_keepsTheListAndItsCursor() = runTest {
    api.issuesHandler = { page("1", "2", next = "c2") }
    repository.refresh(TEST_ACCOUNT, filter)
    api.issuesHandler = { ApiResult.Failure(ApiError.Network("offline")) }

    assertEquals(LoadResult.Failure(InboxError.Network), repository.loadMore(TEST_ACCOUNT, filter))

    assertEquals(listOf("1", "2"), shownIds())
    assertTrue(repository.observeInbox(filter).first().hasMore)

    api.issuesHandler = { page("3") }
    assertEquals(LoadResult.Success, repository.loadMore(TEST_ACCOUNT, filter))
    assertEquals("c2", api.issueCalls.last().cursor)
    assertEquals(listOf("1", "2", "3"), shownIds())
  }

  @Test
  fun retryAfter_isClampedBetweenOneSecondAndAnHour() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(0)) }
    assertEquals(LoadResult.Failure(InboxError.RateLimited(1)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(LoadResult.Failure(InboxError.RateLimited(1)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(1, api.issueCalls.size)

    repository.clear()
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(Long.MAX_VALUE)) }
    assertEquals(LoadResult.Failure(InboxError.RateLimited(3600)), repository.refresh(TEST_ACCOUNT, filter))

    api.issuesHandler = { page("1") }
    clock.advance(Duration.ofSeconds(3599))
    assertEquals(LoadResult.Failure(InboxError.RateLimited(1)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(2, api.issueCalls.size)

    clock.advance(Duration.ofSeconds(1))
    assertEquals(LoadResult.Success, repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(3, api.issueCalls.size)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun aShorterRateLimit_doesNotShortenALongerHold() = runTest {
    // Two calls pass the gate together; their 429s then arrive longest first, as concurrent loads can.
    val longHold = CompletableDeferred<ApiResult<Page<IssueDto>>>()
    val shortHold = CompletableDeferred<ApiResult<Page<IssueDto>>>()
    val responses = ArrayDeque(listOf(longHold, shortHold))
    api.issuesHandler = { responses.removeFirst().await() }
    val escalating = filter.copy(tab = InboxTab.Escalating)
    launch { repository.refresh(TEST_ACCOUNT, filter) }
    launch { repository.refresh(TEST_ACCOUNT, escalating) }
    runCurrent()
    assertEquals(2, api.issueCalls.size)

    longHold.complete(ApiResult.Failure(ApiError.RateLimited(60)))
    runCurrent()
    clock.advance(Duration.ofSeconds(1))
    shortHold.complete(ApiResult.Failure(ApiError.RateLimited(5)))
    runCurrent()

    assertEquals(LoadResult.Failure(InboxError.RateLimited(59)), repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(2, api.issueCalls.size)
  }

  @Test
  fun refresh_whenTheCacheCannotSave_failsWithStorage() = runTest {
    api.issuesHandler = { page("1") }
    cache.writeError = IOException("disk full")

    assertEquals(LoadResult.Failure(InboxError.Storage), repository.refresh(TEST_ACCOUNT, filter))
  }

  @Test
  fun loadMore_whenTheCacheCannotReadOrSave_failsWithStorage() = runTest {
    api.issuesHandler = { page("1", next = "c2") }
    repository.refresh(TEST_ACCOUNT, filter)

    cache.readError = IOException("disk")
    assertEquals(LoadResult.Failure(InboxError.Storage), repository.loadMore(TEST_ACCOUNT, filter))
    assertEquals(1, api.issueCalls.size)

    cache.readError = null
    cache.writeError = IOException("disk full")
    assertEquals(LoadResult.Failure(InboxError.Storage), repository.loadMore(TEST_ACCOUNT, filter))
    assertEquals(2, api.issueCalls.size)
  }

  @Test
  fun refreshProjects_whenTheCacheCannotSave_failsWithStorage() = runTest {
    api.projectsResult = ApiResult.Success(listOf(projectDto("101")))
    cache.writeError = IOException("disk full")

    assertEquals(LoadResult.Failure(InboxError.Storage), repository.refreshProjects(TEST_ACCOUNT))
  }

  @Test
  fun lastRefreshed_whenTheCacheCannotRead_isNull() = runTest {
    api.issuesHandler = { page("1") }
    repository.refresh(TEST_ACCOUNT, filter)
    cache.readError = IOException("disk")

    assertNull(repository.lastRefreshed(filter))
  }

  @Test
  fun aStorageFailure_doesNotHoldLaterCalls() = runTest {
    api.issuesHandler = { page("1") }
    cache.writeError = IOException("disk full")
    assertEquals(LoadResult.Failure(InboxError.Storage), repository.refresh(TEST_ACCOUNT, filter))

    cache.writeError = null
    assertEquals(LoadResult.Success, repository.refresh(TEST_ACCOUNT, filter))
    assertEquals(LoadResult.Success, repository.refreshProjects(TEST_ACCOUNT))
    assertEquals(2, api.issueCalls.size)
    assertEquals(1, api.projectCalls.size)
  }

  @Test
  fun clear_emptiesTheCacheAndLiftsTheRateLimit() = runTest {
    api.issuesHandler = { page("1") }
    repository.refresh(TEST_ACCOUNT, filter)
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(600)) }
    repository.refresh(TEST_ACCOUNT, filter)

    repository.clear()

    assertTrue(shownIds().isEmpty())
    api.issuesHandler = { page("2") }
    assertEquals(LoadResult.Success, repository.refresh(TEST_ACCOUNT, filter))
  }
}
