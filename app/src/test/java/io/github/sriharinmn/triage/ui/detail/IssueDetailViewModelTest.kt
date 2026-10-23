package io.github.sriharinmn.triage.ui.detail

import io.github.sriharinmn.triage.data.AccountDataCleaner
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.detail.ContextLine
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventContext
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.IssueDetailRepository
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.StackFrame
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.issues.toIssue
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.EventDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.SentryJson
import io.github.sriharinmn.triage.remote.TagDetailDto
import io.github.sriharinmn.triage.remote.TagValueDto
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.InMemoryInboxSettingsStore
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import io.github.sriharinmn.triage.testing.readFixture
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IssueDetailViewModelTest {
  // Unconfined Main: each action runs to completion (or to a gate) before the test's next line.
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val clock = TestClock()
  private val api = FakeSentryApi()
  private val cache = InMemoryIssueCache()
  private val rateLimitGate = RateLimitGate(clock::now)
  private val repository = IssueDetailRepository(api, cache, rateLimitGate)
  private val accountStore = InMemoryAccountStore(TEST_ACCOUNT)
  private val disconnector =
    Disconnector(
      AccountRepository(api, accountStore),
      AccountDataCleaner(IssueRepository(api, cache, clock::now, rateLimitGate), InMemoryInboxSettingsStore()),
    )
  private val detailDto = SentryJson.decodeFromString<IssueDetailDto>(readFixture("issue_detail.json"))
  private val jsEvent = SentryJson.decodeFromString<EventDto>(readFixture("event_js.json"))
  private val javaEvent = SentryJson.decodeFromString<EventDto>(readFixture("event_java_chained.json"))

  init {
    api.issueDetailHandler = { ApiResult.Success(detailDto) }
    // Latest is the Java crash; Recommended and Oldest are the JS one.
    api.eventHandler = { _, eventId -> ApiResult.Success(if (eventId == "latest") javaEvent else jsEvent) }
    api.tagHandler = { key ->
      ApiResult.Success(TagDetailDto(key = key, totalValues = 10, topValues = listOf(TagValueDto(value = "v-$key", count = 5))))
    }
  }

  private fun viewModel() = IssueDetailViewModel(TEST_ACCOUNT, "4201", repository, disconnector)

  private fun whats() = api.detailCalls.map { it.what }

  @Test
  fun theCachedSummaryShowsAtOnce_thenTheFreshIssueReplacesIt() = runTest {
    cache.replaceList("k", listOf(issueDto("4201").toIssue()), null, clock.now())
    val issueGate = CompletableDeferred<Unit>()
    api.issueDetailHandler = {
      issueGate.await()
      ApiResult.Success(detailDto)
    }

    val vm = viewModel()

    assertEquals("DEMO-4201", vm.state.value.summary?.shortId)
    assertTrue(vm.state.value.isLoadingSummary)
    issueGate.complete(Unit)
    assertEquals("TRIAGE-DEMO-1A", vm.state.value.summary?.shortId)
    assertEquals(30, vm.state.value.summary?.dailyEvents?.size)
    assertFalse(vm.state.value.isLoadingSummary)
  }

  @Test
  fun withoutACachedRow_theSummaryWaitsForSentry() = runTest {
    val issueGate = CompletableDeferred<Unit>()
    api.issueDetailHandler = {
      issueGate.await()
      ApiResult.Success(detailDto)
    }

    val vm = viewModel()

    assertNull(vm.state.value.summary)
    assertTrue(vm.state.value.isLoadingSummary)
    issueGate.complete(Unit)
    assertEquals("TRIAGE-DEMO-1A", vm.state.value.summary?.shortId)
  }

  @Test
  fun opens_onTheRecommendedEvent_withTheCrashFrameExpanded() = runTest {
    val vm = viewModel()

    val state = vm.state.value
    assertEquals(EventKind.Recommended, state.selectedKind)
    assertEquals("TypeError", state.event?.stacks?.single()?.type)
    assertFalse(state.isLoadingEvent)
    assertEquals(setOf(FrameRef(0, 0)), state.expandedFrames)
    assertTrue("event:recommended" in whats())
  }

  @Test
  fun tagsLoadAfterTheIssue() = runTest {
    val vm = viewModel()

    assertEquals(listOf("environment", "release", "os", "browser", "level"), vm.state.value.tags?.map { it.key })
    assertTrue(whats().indexOf("issue") < whats().indexOfFirst { it.startsWith("tag:") })
  }

  @Test
  fun whenEveryTagFails_theTagsStayNullForTheFallback() = runTest {
    api.tagHandler = { ApiResult.Failure(ApiError.Http(500)) }

    val vm = viewModel()

    assertNull(vm.state.value.tags)
    assertNull(vm.state.value.eventError)
    assertNull(vm.state.value.summaryError)
  }

  @Test
  fun switchingKinds_loadsEachOnceAndKeepsEarlierOnes() = runTest {
    val vm = viewModel()

    vm.onEventKindSelected(EventKind.Latest)
    assertEquals(listOf("IllegalStateException", "SocketTimeoutException"), vm.state.value.event?.stacks?.map { it.type })
    vm.onEventKindSelected(EventKind.Recommended)

    assertEquals("TypeError", vm.state.value.event?.stacks?.single()?.type)
    assertEquals(1, whats().count { it == "event:recommended" })
    assertEquals(1, whats().count { it == "event:latest" })
  }

  @Test
  fun switchingKinds_resetsTheTogglesToTheNewEventsDefaults() = runTest {
    val vm = viewModel()
    vm.onShowLibraryFrames(0)
    vm.onShowAllBreadcrumbs()
    vm.onFrameToggled(FrameRef(0, 1))

    vm.onEventKindSelected(EventKind.Latest)

    val state = vm.state.value
    assertTrue(state.revealedStacks.isEmpty())
    assertFalse(state.showAllBreadcrumbs)
    // The Java crash has no source lines, so nothing starts expanded.
    assertTrue(state.expandedFrames.isEmpty())
  }

  @Test
  fun aResultForAChipNoLongerSelected_isDropped() = runTest {
    val oldestGate = CompletableDeferred<Unit>()
    api.eventHandler = { _, eventId ->
      // NonCancellable: the result arrives even though the chip switch cancelled the load.
      if (eventId == "oldest") withContext(NonCancellable) { oldestGate.await() }
      ApiResult.Success(if (eventId == "latest") javaEvent else jsEvent)
    }
    val vm = viewModel()
    vm.onEventKindSelected(EventKind.Oldest)
    assertTrue(vm.state.value.isLoadingEvent)

    vm.onEventKindSelected(EventKind.Latest)
    oldestGate.complete(Unit)

    assertEquals(EventKind.Latest, vm.state.value.selectedKind)
    assertFalse(EventKind.Oldest in vm.state.value.events)
    assertEquals("IllegalStateException", vm.state.value.event?.stacks?.first()?.type)
  }

  @Test
  fun anEventFailure_isReported_andRetryReloadsIt() = runTest {
    api.eventHandler = { _, _ -> ApiResult.Failure(ApiError.Network("offline")) }
    val vm = viewModel()
    assertEquals(DetailError.Network, vm.state.value.eventError)
    assertFalse(vm.state.value.isLoadingEvent)

    api.eventHandler = { _, _ -> ApiResult.Success(jsEvent) }
    vm.onRetry()

    assertNull(vm.state.value.eventError)
    assertNotNull(vm.state.value.event)
  }

  @Test
  fun aSummaryFailure_keepsTheCachedSummary_andRetryReloadsIt() = runTest {
    cache.replaceList("k", listOf(issueDto("4201").toIssue()), null, clock.now())
    api.issueDetailHandler = { ApiResult.Failure(ApiError.Network("offline")) }
    val vm = viewModel()
    assertEquals(DetailError.Network, vm.state.value.summaryError)
    assertEquals("DEMO-4201", vm.state.value.summary?.shortId)

    api.issueDetailHandler = { ApiResult.Success(detailDto) }
    vm.onRetry()

    assertNull(vm.state.value.summaryError)
    assertEquals("TRIAGE-DEMO-1A", vm.state.value.summary?.shortId)
    assertNotNull(vm.state.value.tags)
  }

  @Test
  fun aMissingIssue_isGone() = runTest {
    api.issueDetailHandler = { ApiResult.Failure(ApiError.Http(404)) }

    val vm = viewModel()

    assertTrue(vm.state.value.isGone)
  }

  @Test
  fun aRejectedToken_asksToReconnect() = runTest {
    api.eventHandler = { _, _ -> ApiResult.Failure(ApiError.Unauthorized) }

    val vm = viewModel()

    assertEquals(DetailError.Unauthorized, vm.state.value.reconnectError)
  }

  @Test
  fun duringARateLimit_retryDoesNotAskSentryUntilTheWaitPasses() = runTest {
    api.issueDetailHandler = { ApiResult.Failure(ApiError.RateLimited(30)) }
    val vm = viewModel()
    assertEquals(DetailError.RateLimited(30), vm.state.value.summaryError)
    // The event load started while the hold was already on, so it never reached Sentry.
    assertEquals(DetailError.RateLimited(30), vm.state.value.eventError)
    assertEquals(listOf("issue"), whats())

    clock.advance(Duration.ofSeconds(10))
    vm.onRetry()
    assertEquals(DetailError.RateLimited(20), vm.state.value.eventError)
    assertEquals(listOf("issue"), whats())

    clock.advance(Duration.ofSeconds(20))
    api.issueDetailHandler = { ApiResult.Success(detailDto) }
    vm.onRetry()
    assertNull(vm.state.value.summaryError)
    assertNull(vm.state.value.eventError)
  }

  @Test
  fun toggles_expandFramesRevealLibraryFramesAndShowAllBreadcrumbs() = runTest {
    val vm = viewModel()

    vm.onFrameToggled(FrameRef(0, 1))
    assertEquals(setOf(FrameRef(0, 0), FrameRef(0, 1)), vm.state.value.expandedFrames)
    vm.onFrameToggled(FrameRef(0, 0))
    assertEquals(setOf(FrameRef(0, 1)), vm.state.value.expandedFrames)
    vm.onShowLibraryFrames(0)
    assertEquals(setOf(0), vm.state.value.revealedStacks)
    vm.onShowAllBreadcrumbs()
    assertTrue(vm.state.value.showAllBreadcrumbs)
  }

  @Test
  fun reconnect_disconnects() = runTest {
    val vm = viewModel()

    vm.onReconnectConfirmed()

    assertNull(accountStore.current)
  }

  @Test
  fun aFailedReconnect_isReportedAndTheAccountKept() = runTest {
    val vm = viewModel()
    cache.clearError = IOException("disk full")

    vm.onReconnectConfirmed()

    assertTrue(vm.state.value.disconnectFailed)
    assertFalse(vm.state.value.isDisconnecting)
    assertNotNull(accountStore.current)
    vm.onDisconnectFailureShown()
    assertFalse(vm.state.value.disconnectFailed)
  }

  @Test
  fun browserUrl_usesTheFreshPermalinkOrBuildsOne() = runTest {
    assertEquals("https://example-org.sentry.io/issues/4201/", viewModel().browserUrl())

    api.issueDetailHandler = { ApiResult.Failure(ApiError.Network("offline")) }
    assertEquals("https://example-org.sentry.io/organizations/example-org/issues/4201/", viewModel().browserUrl())
  }

  @Test
  fun defaultExpandedFrames_preferTheAppsOwnCode() {
    fun frame(inApp: Boolean, code: Boolean) =
      StackFrame("f", null, "f.js", 1, null, inApp, if (code) listOf(ContextLine(1, "x")) else emptyList())
    fun event(vararg frames: StackFrame) =
      IssueEvent(null, null, null, null, listOf(EventStack("E", null, null, frames.toList())), null, emptyList(), emptyList(), EventContext())

    assertEquals(setOf(FrameRef(0, 2)), defaultExpandedFrames(event(frame(false, true), frame(true, false), frame(true, true))))
    assertEquals(setOf(FrameRef(0, 1)), defaultExpandedFrames(event(frame(false, false), frame(false, true))))
    assertTrue(defaultExpandedFrames(event(frame(true, false))).isEmpty())
    assertTrue(defaultExpandedFrames(null).isEmpty())
  }
}
