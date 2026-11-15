package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.alerts.AlertChecker
import io.github.sriharinmn.triage.data.alerts.AlertFinding
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import io.github.sriharinmn.triage.data.alerts.AlertState
import io.github.sriharinmn.triage.data.alerts.CheckOutcome
import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import io.github.sriharinmn.triage.testing.InMemoryDemoStateStore
import io.github.sriharinmn.triage.testing.TestClock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's real background check, run against the demo: the demo has to make real alerts happen. */
class DemoAlertsTest {
  private val zone = ZoneId.of("Asia/Kolkata")
  private val start = Instant.parse("2026-11-16T03:30:00Z")
  private val clock = TestClock(start)
  private val store = InMemoryDemoStateStore()
  private val demo = DemoSentryApi(store, clock::now) { zone }
  private val checker = AlertChecker(demo, RateLimitGate(clock::now))
  private val settings = AlertSettings(enabled = true, baselineRequest = 1)
  private var alertState = AlertState()

  private suspend fun check(): List<AlertFinding> {
    val outcome = checker.check(DemoAccount, settings, alertState, DemoWorld.projects.map { it.id }, clock.now())
    val checked = outcome as CheckOutcome.Checked
    alertState = checked.next
    return checked.findings
  }

  private fun minutes(n: Long) = Duration.ofMinutes(n)

  @Test
  fun theFirstNewIssue_isANewAlert() = runTest {
    demo.startFresh()
    clock.advance(minutes(1))
    assertTrue("baseline", check().isEmpty())
    clock.advance(minutes(15))
    assertTrue(check().isEmpty())

    clock.advance(minutes(15))
    val found = check()

    assertEquals(listOf(AlertKind.New), found.map { it.kind })
    assertEquals(DemoWorld.arrivals[0].title, found.single().title)
  }

  @Test
  fun dailySlots_bringEscalatingThenRegressedThenNewAlerts() = runTest {
    demo.startFresh()
    clock.advance(minutes(1))
    check()
    clock.advance(minutes(30))
    check()
    val slots = happenings(store.current!!, clock.now(), clock.now() + Duration.ofDays(2), zone)

    clock.current = slots[0].at + minutes(1)
    assertEquals(listOf(AlertKind.Escalating), check().map { it.kind })
    clock.current = slots[1].at + minutes(1)
    assertEquals(listOf(AlertKind.Regressed), check().map { it.kind })
    clock.current = slots[2].at + minutes(1)
    assertEquals(listOf(AlertKind.New), check().map { it.kind })
  }

  @Test
  fun turningAlertsOn_bringsANewIssueAbout20MinutesLater() = runTest {
    demo.startFresh()
    clock.advance(Duration.ofHours(2))
    demo.listProjects(DEMO_BASE_URL, DemoAccount.token, DemoAccount.orgSlug)
    while (happenings(store.current!!, clock.now(), clock.now() + minutes(50), zone).isNotEmpty()) clock.advance(minutes(10))
    assertTrue("baseline", check().isEmpty())

    demo.onAlertsTurnedOn()
    clock.advance(minutes(21))

    assertEquals(listOf(AlertKind.New), check().map { it.kind })
  }
}
