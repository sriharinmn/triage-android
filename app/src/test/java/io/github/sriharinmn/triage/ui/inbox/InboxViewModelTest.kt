package io.github.sriharinmn.triage.ui.inbox

import io.github.sriharinmn.triage.data.AccountDataCleaner
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.issues.IssueSort
import io.github.sriharinmn.triage.data.issues.toIssue
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.InMemoryInboxSettingsStore
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import io.github.sriharinmn.triage.testing.projectDto
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxViewModelTest {
  // Unconfined Main: each action runs to completion (or to a gate) before the test's next line.
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val clock = TestClock()
  private val api = FakeSentryApi()
  private val cache = InMemoryIssueCache()
  private val repository = IssueRepository(api, cache, clock::now)
  private val settings = InMemoryInboxSettingsStore()
  private val accountStore = InMemoryAccountStore(TEST_ACCOUNT)
  private val disconnector =
    Disconnector(AccountRepository(api, accountStore), AccountDataCleaner(repository, settings))

  private fun viewModel() = InboxViewModel(TEST_ACCOUNT, repository, settings, disconnector, clock::now)

  private fun page(vararg ids: String, next: String? = null): ApiResult<Page<IssueDto>> =
    ApiResult.Success(Page(ids.map { issueDto(it) }, next))

  private fun InboxViewModel.ids() = state.value.issues.map { it.id }

  private suspend fun cacheList(filter: InboxFilter, ids: List<String>, age: Duration) =
    cache.replaceList(filter.listKey, ids.map { issueDto(it).toIssue() }, null, clock.now() - age)

  @Test
  fun opens_onTheSavedFilter_andLoadsIt() = runTest {
    val saved = InboxFilter(tab = InboxTab.Regressed, sort = IssueSort.Events)
    settings.save(saved)
    api.issuesHandler = { page("1", "2") }

    val vm = viewModel()

    assertEquals(saved, vm.state.value.filter)
    val call = api.issueCalls.single()
    assertEquals("is:regressed", call.params.query)
    assertEquals("freq", call.params.sort)
    assertEquals(listOf("1", "2"), vm.ids())
    assertEquals(clock.now(), vm.state.value.refreshedAt)
    assertFalse(vm.state.value.isRefreshing)
    assertNull(vm.state.value.error)
    assertEquals("Example Org", vm.state.value.orgName)
  }

  @Test
  fun opens_withAFreshCache_showsItWithoutCallingSentry() = runTest {
    cacheList(InboxFilter(), listOf("9"), Duration.ofMinutes(1))

    val vm = viewModel()

    assertEquals(listOf("9"), vm.ids())
    assertTrue(api.issueCalls.isEmpty())
  }

  @Test
  fun opens_withAStaleCache_refreshesIt() = runTest {
    cacheList(InboxFilter(), listOf("9"), Duration.ofMinutes(3))
    api.issuesHandler = { page("1") }

    val vm = viewModel()

    assertEquals(1, api.issueCalls.size)
    assertEquals(listOf("1"), vm.ids())
  }

  @Test
  fun choosingATab_remembersItAndLoadsThatList() = runTest {
    val vm = viewModel()
    api.issuesHandler = { page("e1") }

    vm.onTabSelected(InboxTab.Escalating)

    assertEquals(InboxTab.Escalating, settings.current.tab)
    assertEquals("is:escalating", api.issueCalls.last().params.query)
    assertEquals(listOf("e1"), vm.ids())
  }

  @Test
  fun choosingTheCurrentTabAgain_doesNothing() = runTest {
    api.issuesHandler = { page("1", "2") }
    val vm = viewModel()
    val calls = api.issueCalls.size

    vm.onTabSelected(InboxTab.Unresolved)

    assertEquals(calls, api.issueCalls.size)
    assertEquals(listOf("1", "2"), vm.ids()) // the list must not be blanked
  }

  @Test
  fun switchingToAFreshList_showsItWithoutCallingSentry() = runTest {
    cacheList(InboxFilter(tab = InboxTab.Regressed), listOf("r9"), Duration.ofSeconds(30))
    api.issuesHandler = { page("1") }
    val vm = viewModel()
    assertEquals(1, api.issueCalls.size) // the Unresolved list had no cache, so opening refreshed it

    vm.onTabSelected(InboxTab.Regressed)

    assertEquals(1, api.issueCalls.size)
    assertEquals(listOf("r9"), vm.ids())
  }

  @Test
  fun aRefreshForTheOldTab_doesNotLandOnTheNewTab() = runTest {
    val gate = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      if (call.params.query == InboxTab.Regressed.query) {
        page("r1")
      } else {
        // NonCancellable: the response had already arrived when the tab changed, so only the filter check can stop it.
        withContext(NonCancellable) { gate.await() }
        ApiResult.Failure(ApiError.Network("offline"))
      }
    }
    val vm = viewModel()
    assertTrue(vm.state.value.isRefreshing)

    vm.onTabSelected(InboxTab.Regressed)
    assertEquals(listOf("r1"), vm.ids())
    gate.complete(Unit)

    assertEquals(InboxTab.Regressed, vm.state.value.filter.tab)
    assertEquals(listOf("r1"), vm.ids())
    assertFalse(vm.state.value.isRefreshing)
    assertNull(vm.state.value.error)
  }

  @Test
  fun pullingTwiceWhileRefreshing_makesOneCall() = runTest {
    cacheList(InboxFilter(), listOf("9"), Duration.ofSeconds(10))
    val gate = CompletableDeferred<Unit>()
    api.issuesHandler = {
      gate.await()
      page("1")
    }
    val vm = viewModel()
    assertTrue(api.issueCalls.isEmpty())

    vm.onRefresh()
    vm.onRefresh()
    assertTrue(vm.state.value.isRefreshing)
    assertEquals(1, api.issueCalls.size)
    gate.complete(Unit)

    assertEquals(1, api.issueCalls.size)
    assertEquals(listOf("1"), vm.ids())
    assertFalse(vm.state.value.isRefreshing)
  }

  @Test
  fun aSortChoice_isSentAndRemembered() = runTest {
    val vm = viewModel()

    vm.onSortSelected(IssueSort.Users)

    assertEquals("user", api.issueCalls.last().params.sort)
    assertEquals(IssueSort.Users, settings.current.sort)
  }

  @Test
  fun pullToRefresh_alwaysCallsSentry() = runTest {
    cacheList(InboxFilter(), listOf("9"), Duration.ofSeconds(10))
    val vm = viewModel()
    api.issuesHandler = { page("1") }

    vm.onRefresh()

    assertEquals(1, api.issueCalls.size)
    assertEquals(listOf("1"), vm.ids())
  }

  @Test
  fun loadMore_appendsTheNextPage_andIgnoresRepeatedTriggers() = runTest {
    val gate = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      if (call.cursor == null) {
        page("1", "2", next = "c2")
      } else {
        gate.await()
        page("3")
      }
    }
    val vm = viewModel()
    assertTrue(vm.state.value.hasMore)

    vm.onLoadMore()
    vm.onLoadMore()
    assertTrue(vm.state.value.isLoadingMore)
    gate.complete(Unit)

    assertEquals(listOf("1", "2", "3"), vm.ids())
    assertFalse(vm.state.value.isLoadingMore)
    assertFalse(vm.state.value.hasMore)
    assertEquals(2, api.issueCalls.size)
  }

  @Test
  fun refreshing_dropsAnInFlightPageOfTheOldList() = runTest {
    val gate = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      if (call.cursor == null) {
        page("1", next = "c2")
      } else {
        gate.await()
        page("3")
      }
    }
    val vm = viewModel()
    vm.onLoadMore()
    assertTrue(vm.state.value.isLoadingMore)
    api.issuesHandler = { page("9") }

    vm.onRefresh()
    assertEquals(listOf("9"), vm.ids())
    gate.complete(Unit)

    assertEquals(listOf("9"), vm.ids())
    assertFalse(vm.state.value.isLoadingMore)
    assertFalse(vm.state.value.hasMore)
  }

  @Test
  fun aLoadMoreFailure_waitsForAnExplicitRetry() = runTest {
    var failNext = true
    api.issuesHandler = { call ->
      when {
        call.cursor == null -> page("1", next = "c2")
        failNext -> ApiResult.Failure(ApiError.Network("offline"))
        else -> page("2")
      }
    }
    val vm = viewModel()

    vm.onLoadMore()
    assertTrue(vm.state.value.loadMoreFailed)
    vm.onLoadMore() // the list keeps asking while its end is on screen
    assertEquals(2, api.issueCalls.size)

    failNext = false
    vm.onRetryLoadMore()

    assertEquals(listOf("1", "2"), vm.ids())
    assertFalse(vm.state.value.loadMoreFailed)
  }

  @Test
  fun aRefreshFailure_keepsTheCachedIssuesAndSaysWhy() = runTest {
    cacheList(InboxFilter(), listOf("9"), Duration.ofHours(1))
    api.issuesHandler = { ApiResult.Failure(ApiError.Network("offline")) }

    val vm = viewModel()

    assertEquals(listOf("9"), vm.ids())
    assertEquals(InboxError.Network, vm.state.value.error)
    assertFalse(vm.state.value.isRefreshing)
  }

  @Test
  fun aRefreshThatCannotSave_endsWithAStorageError() = runTest {
    api.issuesHandler = { page("1") }
    cache.writeError = IOException("disk full")

    val vm = viewModel()

    assertFalse(vm.state.value.isRefreshing)
    assertEquals(InboxError.Storage, vm.state.value.error)
    assertTrue(vm.ids().isEmpty())
  }

  @Test
  fun aRejectedToken_isReported() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.Unauthorized) }

    assertEquals(InboxError.Unauthorized, viewModel().state.value.error)
  }

  @Test
  fun aSuccessfulRefresh_clearsTheError() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.Http(502)) }
    val vm = viewModel()
    assertEquals(InboxError.Server, vm.state.value.error)
    api.issuesHandler = { page("1") }

    vm.onRefresh()

    assertNull(vm.state.value.error)
  }

  @Test
  fun choosingAProject_dropsAnEnvironmentItDoesNotHave() = runTest {
    api.projectsResult =
      ApiResult.Success(listOf(projectDto("101", listOf("production", "staging")), projectDto("102", listOf("production"))))
    val vm = viewModel()

    vm.onEnvironmentSelected("staging")
    vm.onProjectSelected("102")
    assertEquals(InboxFilter(projectId = "102", environment = null), vm.state.value.filter)

    vm.onEnvironmentSelected("production")
    vm.onProjectSelected("101")
    assertEquals(InboxFilter(projectId = "101", environment = "production"), vm.state.value.filter)
    assertEquals("production", api.issueCalls.last().params.environment)
  }

  @Test
  fun environments_areTheSelectedProjectsOrEveryProjects() = runTest {
    api.projectsResult =
      ApiResult.Success(listOf(projectDto("101", listOf("staging", "production")), projectDto("102", listOf("production", "qa"))))
    val vm = viewModel()

    assertEquals(listOf("production", "qa", "staging"), vm.state.value.environments)
    vm.onProjectSelected("102")
    assertEquals(listOf("production", "qa"), vm.state.value.environments)
  }

  @Test
  fun aRememberedProjectThatIsGone_fallsBackToAllProjects() = runTest {
    settings.save(InboxFilter(projectId = "999"))
    api.projectsResult = ApiResult.Success(listOf(projectDto("101")))

    val vm = viewModel()

    assertNull(vm.state.value.filter.projectId)
    assertNull(api.issueCalls.last().params.projectId)
    assertNull(settings.current.projectId)
  }

  @Test
  fun disconnect_forgetsTheAccountTheCacheAndTheSettings() = runTest {
    api.issuesHandler = { page("1") }
    val vm = viewModel()
    vm.onTabSelected(InboxTab.Regressed)

    vm.onDisconnectConfirmed()

    assertNull(accountStore.current)
    assertTrue(cache.observeIssues(InboxFilter().listKey).first().isEmpty())
    assertEquals(InboxFilter(), settings.current)
  }

  @Test
  fun aRefreshInFlightWhenDisconnecting_neverWritesBack() = runTest {
    val gate = CompletableDeferred<Unit>()
    api.issuesHandler = {
      gate.await()
      page("1")
    }
    val vm = viewModel()
    assertTrue(vm.state.value.isRefreshing)

    vm.onDisconnectConfirmed()
    gate.complete(Unit)

    assertNull(accountStore.current)
    assertTrue(cache.observeIssues(InboxFilter().listKey).first().isEmpty())
    assertNull(cache.listState(InboxFilter().listKey))
  }

  @Test
  fun filterChangesDuringDisconnect_areIgnored() = runTest {
    val vm = viewModel()
    val calls = api.issueCalls.size
    val gate = CompletableDeferred<Unit>()
    cache.clearGate = gate
    vm.onDisconnectConfirmed()
    assertTrue(vm.state.value.isDisconnecting)

    vm.onTabSelected(InboxTab.Escalating)

    assertEquals(InboxFilter(), vm.state.value.filter)
    assertEquals(InboxFilter(), settings.current)
    assertEquals(calls, api.issueCalls.size)
    gate.complete(Unit)
    assertNull(accountStore.current)
    assertEquals(InboxFilter(), settings.current)
  }

  @Test
  fun aDisconnectThatFailsAfterClearingTheCache_reloadsTheInbox() = runTest {
    api.issuesHandler = { page("1") }
    val vm = viewModel()
    assertEquals(listOf("1"), vm.ids())
    settings.clearError = IOException("disk full")
    api.issuesHandler = { page("2") }
    val issueCalls = api.issueCalls.size
    val projectCalls = api.projectCalls.size

    vm.onDisconnectConfirmed()

    assertTrue(vm.state.value.disconnectFailed)
    assertFalse(vm.state.value.isDisconnecting)
    assertNotNull(accountStore.current)
    assertEquals(issueCalls + 1, api.issueCalls.size)
    assertEquals(projectCalls + 1, api.projectCalls.size)
    assertEquals(listOf("2"), vm.ids()) // the cache was cleared first, so this is the reloaded list
    assertFalse(vm.state.value.isRefreshing)
  }

  @Test
  fun aDisconnectFailure_isReportedAndTheAccountKept() = runTest {
    cache.clearError = IOException("disk full")
    val vm = viewModel()

    vm.onDisconnectConfirmed()

    assertTrue(vm.state.value.disconnectFailed)
    assertFalse(vm.state.value.isDisconnecting)
    assertNotNull(accountStore.current)
    vm.onDisconnectFailureShown()
    assertFalse(vm.state.value.disconnectFailed)
  }

  @Test
  fun issueUrl_usesSentrysHttpsPermalinkOrBuildsOne() = runTest {
    val vm = viewModel()
    val fallback = "https://example-org.sentry.io/organizations/example-org/issues/6/"

    assertEquals("https://example-org.sentry.io/issues/5/", vm.issueUrl(issueDto("5").toIssue()))
    assertEquals(fallback, vm.issueUrl(issueDto("6", permalink = "http://example-org.sentry.io/issues/6/").toIssue()))
    assertEquals(fallback, vm.issueUrl(issueDto("6", permalink = "javascript:alert(1)").toIssue()))
    assertEquals(fallback, vm.issueUrl(issueDto("6", permalink = null).toIssue()))
  }
}
