package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.IssueCall
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.RecordingAlertPoster
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertCheckRunnerTest {
  private val clock = TestClock(Instant.parse("2026-11-11T12:00:00Z"))
  private val api = FakeSentryApi()
  private val accounts = MutableStateFlow<Account?>(TEST_ACCOUNT)
  private val settingsStore = DataStoreAlertSettingsStore(InMemoryPreferencesDataStore())
  private val stateStore = DataStoreAlertStateStore(InMemoryPreferencesDataStore())
  private val poster = RecordingAlertPoster()
  private val runner =
    AlertCheckRunner(
      accounts = accounts,
      settingsStore = settingsStore,
      stateStore = stateStore,
      knownProjectIds = { listOf("101", "102", "103") },
      checker = AlertChecker(api, RateLimitGate(clock::now)),
      poster = poster,
      clock = clock::now,
      zone = { ZoneOffset.UTC },
    )

  /** A runner as a new process would build it: same stores, a fresh in-memory rate-limit gate. */
  private fun freshRunner() =
    AlertCheckRunner(
      accounts = accounts,
      settingsStore = settingsStore,
      stateStore = stateStore,
      knownProjectIds = { listOf("101", "102", "103") },
      checker = AlertChecker(api, RateLimitGate(clock::now)),
      poster = poster,
      clock = clock::now,
      zone = { ZoneOffset.UTC },
    )

  private var regressedIssues = listOf<IssueDto>()
  private var escalatingIssues = listOf<IssueDto>()

  init {
    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
  }

  private fun answer(call: IssueCall): List<IssueDto> =
    when (call.params.query) {
      "is:regressed" -> regressedIssues
      "is:escalating" -> escalatingIssues
      else -> emptyList()
    }

  /** Switches alerts on and runs the first check, which only records; then moves 15 minutes on. */
  private suspend fun baseline() {
    settingsStore.setEnabled(true)
    runner.runCheck()
    clock.advance(Duration.ofMinutes(15))
  }

  @Test
  fun nothingIsAsked_whenAlertsAreOff_disconnected_orNotificationsBlocked() = runTest {
    runner.runCheck()
    settingsStore.setEnabled(true)
    accounts.value = null
    runner.runCheck()
    accounts.value = TEST_ACCOUNT
    poster.allowed = false
    runner.runCheck()

    assertTrue(api.issueCalls.isEmpty())
    assertEquals(AlertState(), stateStore.state.first())
  }

  @Test
  fun theFirstCheckOnlyRecords_thenNewFindingsGetANotificationEach() = runTest {
    regressedIssues = listOf(issueDto("2"))
    baseline()
    assertTrue(poster.posts.isEmpty())
    regressedIssues = listOf(issueDto("2"), issueDto("4"))

    runner.runCheck()

    val (orgSlug, post) = poster.posts.single()
    assertEquals(TEST_ACCOUNT.orgSlug, orgSlug)
    assertEquals(listOf("4"), (post as AlertPost.Singles).findings.map { it.issueId })
    val state = stateStore.state.first()
    assertEquals(setOf("2", "4"), state.regressed)
    assertEquals(clock.now(), state.lastSuccessAt)
  }

  @Test
  fun fourOrMore_postOneSummary() = runTest {
    baseline()
    regressedIssues = (1..4).map { issueDto("r$it") }

    runner.runCheck()

    assertEquals(AlertPost.Summary(mapOf(AlertKind.Regressed to 4), "project-101", "101"), poster.posts.single().second)
  }

  @Test
  fun duringQuietHours_findingsWait_thenPostAsOneSummary() = runTest {
    clock.current = Instant.parse("2026-11-11T23:00:00Z")
    settingsStore.setQuietHours(QuietHours(enabled = true))
    baseline()
    regressedIssues = listOf(issueDto("4"))

    runner.runCheck()

    assertTrue(poster.posts.isEmpty())
    assertEquals(mapOf("4" to AlertKind.Regressed), stateStore.state.first().backlog)

    clock.current = Instant.parse("2026-11-12T07:15:00Z")
    escalatingIssues = listOf(issueDto("5"))
    runner.runCheck()

    assertEquals(
      AlertPost.Summary(mapOf(AlertKind.Regressed to 1, AlertKind.Escalating to 1), projectSlug = null, projectId = null),
      poster.posts.single().second,
    )
    assertTrue(stateStore.state.first().backlog.isEmpty())
  }

  @Test
  fun aKindSwitchedOnDuringQuietHours_stillPostsTheBacklogAfterTheWindow() = runTest {
    clock.current = Instant.parse("2026-11-11T23:00:00Z")
    settingsStore.setQuietHours(QuietHours(enabled = true))
    settingsStore.setKind(AlertKind.Escalating, on = false)
    baseline()
    regressedIssues = listOf(issueDto("4"))
    runner.runCheck()
    assertEquals(mapOf("4" to AlertKind.Regressed), stateStore.state.first().backlog)

    // A baseline check, still inside the window.
    settingsStore.setKind(AlertKind.Escalating, on = true)
    runner.runCheck()
    assertTrue(poster.posts.isEmpty())

    clock.current = Instant.parse("2026-11-12T07:15:00Z")
    runner.runCheck()

    assertEquals(AlertPost.Summary(mapOf(AlertKind.Regressed to 1), projectSlug = null, projectId = null), poster.posts.single().second)
  }

  @Test
  fun aRejectedToken_isReportedOnce_andPausesChecks_untilTheInboxLoadsAgain() = runTest {
    baseline()
    val callsBefore = api.issueCalls.size
    api.issuesHandler = { ApiResult.Failure(ApiError.Unauthorized) }

    runner.runCheck()
    runner.runCheck()

    assertEquals(1, poster.authProblems)
    assertEquals(callsBefore + 1, api.issueCalls.size)
    val state = stateStore.state.first()
    assertTrue(state.authProblemNotified)
    assertTrue(state.lastAttemptFailed)

    stateStore.clearAuthProblem()
    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
    runner.runCheck()

    assertFalse(stateStore.state.first().lastAttemptFailed)
    assertEquals(1, poster.authProblems)
  }

  @Test
  fun aFailedRound_isRecorded_andKeepsTheLastSuccessfulTime() = runTest {
    baseline()
    val checkedAt = stateStore.state.first().lastSuccessAt
    api.issuesHandler = { ApiResult.Failure(ApiError.Network("offline")) }

    runner.runCheck()

    val state = stateStore.state.first()
    assertTrue(state.lastAttemptFailed)
    assertEquals(checkedAt, state.lastSuccessAt)
    assertTrue(poster.posts.isEmpty())
  }

  @Test
  fun anAccountForgottenWhileSentryAnswers_getsNothingSavedOrPosted() = runTest {
    baseline()
    regressedIssues = listOf(issueDto("4"))
    val answered = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      answered.await()
      ApiResult.Success(Page(answer(call), null))
    }
    val check = launch { runner.runCheck() }
    runCurrent()

    runner.forget()
    answered.complete(Unit)
    check.join()

    assertEquals(AlertState(), stateStore.state.first())
    assertTrue(poster.posts.isEmpty())
    assertEquals(1, poster.cancelAlls)
  }

  @Test
  fun alertsSwitchedOffWhileSentryAnswers_postNothing() = runTest {
    baseline()
    val before = stateStore.state.first()
    regressedIssues = listOf(issueDto("4"))
    val answered = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      answered.await()
      ApiResult.Success(Page(answer(call), null))
    }
    val check = launch { runner.runCheck() }
    runCurrent()

    settingsStore.setEnabled(false)
    answered.complete(Unit)
    check.join()

    assertTrue(poster.posts.isEmpty())
    assertEquals(before, stateStore.state.first())
  }

  @Test
  fun aKindSwitchedOnWhileACheckRuns_stillGetsItsBaseline() = runTest {
    settingsStore.setKind(AlertKind.Escalating, on = false)
    baseline()
    val answered = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      answered.await()
      ApiResult.Success(Page(answer(call), null))
    }
    val check = launch { runner.runCheck() }
    runCurrent()
    settingsStore.setKind(AlertKind.Escalating, on = true)
    answered.complete(Unit)
    check.join()

    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
    escalatingIssues = listOf(issueDto("5"))
    clock.advance(Duration.ofMinutes(15))
    runner.runCheck()

    assertTrue("issues already escalating when the kind was switched on must not alert", poster.posts.isEmpty())
    assertEquals(setOf("5"), stateStore.state.first().escalating)
  }

  @Test
  fun twoChecksAtOnce_runOneAfterTheOther_andAlertOnce() = runTest {
    baseline()
    val callsAfterBaseline = api.issueCalls.size
    regressedIssues = listOf(issueDto("4"))
    val answered = CompletableDeferred<Unit>()
    api.issuesHandler = { call ->
      answered.await()
      ApiResult.Success(Page(answer(call), null))
    }
    val first = launch { runner.runCheck() }
    val second = launch { runner.runCheck() }
    runCurrent()

    answered.complete(Unit)
    first.join()
    second.join()

    assertEquals(1, poster.posts.size)
    // Both checks searched (3 kinds each), one after the other, rather than the second being dropped.
    assertEquals(callsAfterBaseline + 6, api.issueCalls.size)
  }

  @Test
  fun projectsSwitchedOff_areLeftOutOfTheSearches() = runTest {
    settingsStore.setProject("102", on = false)

    baseline()

    assertTrue(api.issueCalls.isNotEmpty())
    assertTrue(api.issueCalls.all { it.params.projectIds == listOf("101", "103") })
  }

  @Test
  fun forget_clearsTheStateAndTakesDownTheNotifications() = runTest {
    baseline()

    runner.forget()

    assertEquals(AlertState(), stateStore.state.first())
    assertEquals(1, poster.cancelAlls)
  }

  @Test
  fun a429_savesItsHold_andAFreshProcessInsideTheHoldAsksNothing() = runTest {
    baseline()
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(120)) }
    runner.runCheck()

    val held = stateStore.state.first()
    assertEquals(clock.now().plusSeconds(120), held.heldUntil)
    assertTrue(held.lastAttemptFailed)

    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
    api.issueCalls.clear()
    clock.advance(Duration.ofSeconds(60))
    freshRunner().runCheck()

    assertTrue(api.issueCalls.isEmpty())
    assertEquals(held, stateStore.state.first())
  }

  @Test
  fun afterTheHold_checksRunAgain_andTheHoldIsGone() = runTest {
    baseline()
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(120)) }
    runner.runCheck()

    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
    api.issueCalls.clear()
    clock.advance(Duration.ofSeconds(121))
    freshRunner().runCheck()

    assertTrue(api.issueCalls.isNotEmpty())
    val after = stateStore.state.first()
    assertEquals(null, after.heldUntil)
    assertFalse(after.lastAttemptFailed)
  }

  @Test
  fun forgettingTheAccount_dropsTheHold() = runTest {
    baseline()
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(120)) }
    runner.runCheck()

    runner.forget()

    assertEquals(null, stateStore.state.first().heldUntil)
  }
}
