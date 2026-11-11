package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.IssueCall
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCheckerTest {
  private val clock = TestClock(Instant.parse("2026-11-11T12:00:00Z"))
  private val api = FakeSentryApi()
  private val gate = RateLimitGate(clock::now)
  private val checker = AlertChecker(api, gate)

  /** Alerts on, with one baseline request. */
  private val on = AlertSettings(enabled = true, baselineRequest = 1)

  /** A state whose baseline answers [on], last checked 15 minutes ago. */
  private val settled = AlertState(lastSuccessAt = clock.now() - Duration.ofMinutes(15), baselineFor = 1)

  private var newIssues = listOf<IssueDto>()
  private var regressedIssues = listOf<IssueDto>()
  private var escalatingIssues = listOf<IssueDto>()

  init {
    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
  }

  private fun answer(call: IssueCall): List<IssueDto> =
    when {
      call.params.query.startsWith("is:unresolved firstSeen:>") -> newIssues
      call.params.query == "is:regressed" -> regressedIssues
      call.params.query == "is:escalating" -> escalatingIssues
      else -> error("unexpected query ${call.params.query}")
    }

  /** An issue Sentry first saw [minutesAgo] minutes ago. */
  private fun fresh(id: String, minutesAgo: Long = 5) =
    issueDto(id).copy(firstSeen = (clock.now() - Duration.ofMinutes(minutesAgo)).toString())

  private suspend fun check(
    settings: AlertSettings = on,
    state: AlertState = settled,
    known: List<String> = listOf("101", "102"),
  ): CheckOutcome = checker.check(TEST_ACCOUNT, settings, state, known, clock.now())

  private fun CheckOutcome.checked(): CheckOutcome.Checked = this as CheckOutcome.Checked

  private fun CheckOutcome.found(): List<Pair<String, AlertKind>> = checked().findings.map { it.issueId to it.kind }

  @Test
  fun theFirstCheck_onlyRecords() = runTest {
    newIssues = listOf(fresh("1"))
    regressedIssues = listOf(issueDto("2"))
    escalatingIssues = listOf(issueDto("3"))

    val outcome = check(state = AlertState()).checked()

    assertTrue(outcome.findings.isEmpty())
    assertEquals(setOf("2"), outcome.next.regressed)
    assertEquals(setOf("3"), outcome.next.escalating)
    assertEquals(setOf("1"), outcome.next.alertedNew.keys)
    assertEquals(1L, outcome.next.baselineFor)
    assertEquals(clock.now(), outcome.next.lastSuccessAt)
  }

  @Test
  fun aNewBaselineRequest_onlyRecords_butKeepsTheQuietHoursBacklog() = runTest {
    regressedIssues = listOf(issueDto("2"))

    val outcome =
      check(settings = on.copy(baselineRequest = 2), state = settled.copy(backlog = mapOf("9" to AlertKind.New))).checked()

    assertTrue(outcome.findings.isEmpty())
    assertEquals(2L, outcome.next.baselineFor)
    assertEquals(mapOf("9" to AlertKind.New), outcome.next.backlog)
  }

  @Test
  fun aBaseline_dropsABacklogFromBeforeTheLookbackCap() = runTest {
    val stale = settled.copy(lastSuccessAt = clock.now() - Duration.ofHours(25), backlog = mapOf("9" to AlertKind.New))

    val outcome = check(settings = on.copy(baselineRequest = 2), state = stale).checked()

    assertTrue(outcome.next.backlog.isEmpty())
  }

  @Test
  fun aBaseline_keepsOnlyTheBacklogOfKindsStillOn() = runTest {
    val held = settled.copy(backlog = mapOf("9" to AlertKind.New, "8" to AlertKind.Regressed))

    val outcome =
      check(settings = on.copy(baselineRequest = 2, kinds = setOf(AlertKind.Regressed, AlertKind.Escalating)), state = held)
        .checked()

    assertEquals(mapOf("8" to AlertKind.Regressed), outcome.next.backlog)
  }

  @Test
  fun aNewIssue_isFound_withWhatItsNotificationShows() = runTest {
    newIssues = listOf(fresh("1"))

    val finding = check().checked().findings.single()

    assertEquals(AlertFinding("1", "DEMO-1", "Error 1", "project-101", AlertKind.New, eventCount = 3, userCount = 1), finding)
  }

  @Test
  fun theQueries_matchTheInboxTabs_firstPageOnly_sortedByDate() = runTest {
    check()

    val calls = api.issueCalls
    // 15 minutes since the last check, plus 10 minutes of overlap.
    assertEquals(
      listOf("is:unresolved firstSeen:>2026-11-11T11:35:00+00:00", "is:regressed", "is:escalating"),
      calls.map { it.params.query },
    )
    assertTrue(calls.all { it.params.projectIds.isEmpty() && it.params.environment == null })
    assertTrue(calls.all { it.params.sort == "date" && it.params.statsPeriod == "14d" && it.params.limit == 100 })
    assertTrue(calls.all { it.cursor == null })
  }

  @Test
  fun theOverlap_neverAlertsTheSameNewIssueTwice() = runTest {
    newIssues = listOf(fresh("1"), fresh("2"))

    val outcome = check(state = settled.copy(alertedNew = mapOf("1" to clock.now() - Duration.ofMinutes(5))))

    assertEquals(listOf("2" to AlertKind.New), outcome.found())
    assertEquals(setOf("1", "2"), outcome.checked().next.alertedNew.keys)
  }

  @Test
  fun afterALongGap_newLooksBackAtMost24Hours() = runTest {
    check(state = settled.copy(lastSuccessAt = clock.now() - Duration.ofDays(3)))
    check(state = AlertState(baselineFor = 1))

    assertEquals(
      listOf("is:unresolved firstSeen:>2026-11-10T12:00:00+00:00"),
      api.issueCalls.map { it.params.query }.filter { it.startsWith("is:unresolved") }.distinct(),
    )
  }

  @Test
  fun alertedNewIssues_areForgottenAfter24Hours() = runTest {
    val state =
      settled.copy(alertedNew = mapOf("old" to clock.now() - Duration.ofHours(25), "recent" to clock.now() - Duration.ofHours(1)))

    assertEquals(setOf("recent"), check(state = state).checked().next.alertedNew.keys)
  }

  @Test
  fun regressed_alertsWhenNewToTheSet_andAgainAfterLeavingIt() = runTest {
    regressedIssues = listOf(issueDto("2"), issueDto("4"))
    val first = check(state = settled.copy(regressed = setOf("2")))
    assertEquals(listOf("4" to AlertKind.Regressed), first.found())
    assertEquals(setOf("2", "4"), first.checked().next.regressed)

    regressedIssues = listOf(issueDto("4"))
    val second = check(state = first.checked().next)
    assertTrue(second.found().isEmpty())

    regressedIssues = listOf(issueDto("2"), issueDto("4"))
    val third = check(state = second.checked().next)
    assertEquals(listOf("2" to AlertKind.Regressed), third.found())
  }

  @Test
  fun escalating_alertsWhenNewToTheSet() = runTest {
    escalatingIssues = listOf(issueDto("3"), issueDto("5"))

    val outcome = check(state = settled.copy(escalating = setOf("3")))

    assertEquals(listOf("5" to AlertKind.Escalating), outcome.found())
    assertEquals(setOf("3", "5"), outcome.checked().next.escalating)
  }

  @Test
  fun anIssueFoundAsSeveralKinds_getsOneAlert_withTheStrongest() = runTest {
    newIssues = listOf(fresh("7"))
    regressedIssues = listOf(issueDto("7"), issueDto("8"))
    escalatingIssues = listOf(issueDto("7"))

    assertEquals(listOf("7" to AlertKind.Escalating, "8" to AlertKind.Regressed), check().found())
  }

  @Test
  fun aFullFirstPage_marksTheKindCapped_andIsWhatGetsRecorded() = runTest {
    regressedIssues = (0 until AlertChecker.PAGE_SIZE).map { issueDto("r$it") }

    val outcome = check().checked()

    assertEquals(setOf(AlertKind.Regressed), outcome.capped)
    assertEquals(AlertChecker.PAGE_SIZE, outcome.next.regressed.size)
  }

  @Test
  fun aKindSwitchedOff_isNotAskedAbout_andItsSetEmpties() = runTest {
    val outcome = check(settings = on.copy(kinds = setOf(AlertKind.New, AlertKind.Escalating)), state = settled.copy(regressed = setOf("2")))

    assertTrue(api.issueCalls.none { it.params.query == "is:regressed" })
    assertTrue(outcome.checked().next.regressed.isEmpty())
  }

  @Test
  fun projectsSwitchedOff_areLeftOutOfEveryQuery() = runTest {
    check(settings = on.copy(projectsOff = setOf("102")), known = listOf("101", "102", "103"))

    assertEquals(3, api.issueCalls.size)
    assertTrue(api.issueCalls.all { it.params.projectIds == listOf("101", "103") })
  }

  @Test
  fun everyProjectSwitchedOff_asksNothing_andCountsAsChecked() = runTest {
    val outcome = check(settings = on.copy(projectsOff = setOf("101", "102"))).checked()

    assertTrue(api.issueCalls.isEmpty())
    assertTrue(outcome.findings.isEmpty())
    assertEquals(clock.now(), outcome.next.lastSuccessAt)
  }

  @Test
  fun projectsSwitchedOff_withoutAProjectList_failTheRound() = runTest {
    assertEquals(CheckOutcome.Failed, check(settings = on.copy(projectsOff = setOf("102")), known = emptyList()))
    assertTrue(api.issueCalls.isEmpty())
  }

  @Test
  fun aRejectedToken_orMissingScope_isAnAuthProblem() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.Unauthorized) }
    assertEquals(CheckOutcome.AuthProblem, check())

    api.issuesHandler = { ApiResult.Failure(ApiError.Forbidden) }
    assertEquals(CheckOutcome.AuthProblem, check())
  }

  @Test
  fun otherErrors_failTheRound_atTheFirstQuery() = runTest {
    for (error in listOf(ApiError.Network("offline"), ApiError.Http(502), ApiError.TooLarge, ApiError.Parse)) {
      api.issueCalls.clear()
      api.issuesHandler = { ApiResult.Failure(error) }

      assertEquals(CheckOutcome.Failed, check())
      assertEquals(1, api.issueCalls.size)
    }
  }

  @Test
  fun a429_failsTheRound_andHoldsTheNextOneWithoutAsking() = runTest {
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(60)) }
    assertEquals(CheckOutcome.Failed, check())
    assertNotNull(gate.remainingSeconds())

    api.issuesHandler = { call -> ApiResult.Success(Page(answer(call), null)) }
    assertEquals(CheckOutcome.Failed, check())

    assertEquals(1, api.issueCalls.size)
  }
}
