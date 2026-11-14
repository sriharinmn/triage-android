package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTimelineTest {
  private val zone = ZoneId.of("Asia/Kolkata")

  /** 09:00 in Kolkata. */
  private val start = Instant.parse("2026-11-16T03:30:00Z")
  private val fresh = freshDemoState(start)
  private val minutes = { n: Long -> Duration.ofMinutes(n) }

  private fun DemoState.issue(key: String) = issues.single { it.contentKey == key }

  private fun slotsOver(days: Long, state: DemoState = fresh) =
    happenings(state, start, start + Duration.ofDays(days), zone).filter { !it.alwaysNew }

  @Test
  fun nothingHappens_inTheFirst25Minutes() {
    assertSame(fresh, advance(fresh, start + minutes(24), zone))
  }

  @Test
  fun the25thMinute_bringsANewIssue() {
    val next = advance(fresh, start + minutes(26), zone)

    val arrived = next.issues.last()
    assertEquals(DemoWorld.arrivals[0].key, arrived.contentKey)
    assertEquals((start + minutes(25)).epochMs, arrived.firstSeen)
    assertEquals(arrived.firstSeen, arrived.inboxSince)
    assertEquals(ARRIVAL_ID_BASE.toString(), arrived.id)
    assertEquals("SHOP-ANDROID-52", arrived.shortId)
    assertEquals(1, next.happeningCount)
    assertEquals(1, next.arrivalCount)
    assertEquals((start + minutes(26)).epochMs, next.processedUntil)
  }

  @Test
  fun dailySlots_fallInTheDaytime_within40MinutesOfTheirTimes() {
    val slots = slotsOver(days = 6)
    val local = slots.map { it.at.atZone(zone).toLocalTime() }
    val anchors = listOf(LocalTime.of(10, 0), LocalTime.of(13, 30), LocalTime.of(17, 0), LocalTime.of(20, 0))

    assertTrue(local.all { time -> anchors.any { Duration.between(it, time).abs() <= minutes(40) } })
    // Days 2 to 5 have all four slots; the first day loses the slots crowded by the first happening.
    for (d in 1L..4L) {
      val date = (start + Duration.ofDays(d)).atZone(zone).toLocalDate()
      assertEquals(4, slots.count { it.at.atZone(zone).toLocalDate() == date })
    }
    assertTrue("the jitter varies", local.toSet().size > 4)
  }

  @Test
  fun theSchedule_isFixedByTheStart() {
    assertEquals(slotsOver(days = 3), slotsOver(days = 3))
    val other = freshDemoState(start + Duration.ofDays(1).plusMinutes(1))
    val shift = { list: List<Happening> -> list.map { it.at.atZone(zone).toLocalTime() } }
    assertNotEquals(
      shift(happenings(fresh, start + Duration.ofDays(2), start + Duration.ofDays(3), zone)),
      shift(happenings(other, start + Duration.ofDays(2), start + Duration.ofDays(3), zone)),
    )
  }

  @Test
  fun noSlot_within90MinutesAfterTheFirstHappening() {
    // 09:40 in Kolkata: the first happening (10:05) sits on top of the 10:00 slot.
    val crowded = freshDemoState(Instant.parse("2026-11-16T04:10:00Z"))
    val first = Instant.parse("2026-11-16T04:35:00Z")

    val soon = happenings(crowded, first - minutes(1), first + minutes(90), zone)

    assertEquals(listOf(Happening(first, alwaysNew = true)), soon)
  }

  @Test
  fun theRotation_isNewThenEscalatingThenRegressed() {
    val t = start + Duration.ofHours(3)

    val escalated = fresh.copy(happeningCount = 1).after(Happening(t, alwaysNew = false))
    // The issue archived longest escalates first.
    val resize = escalated.issue("web-resize-observer")
    assertEquals(DemoStatus.Unresolved, resize.status)
    assertNull(resize.archivedAt)
    assertEquals(t.epochMs, resize.escalatedAt)
    assertEquals(t.epochMs, resize.inboxSince)
    assertEquals(listOf(t.epochMs), resize.bursts)

    val regressed = fresh.copy(happeningCount = 2).after(Happening(t, alwaysNew = false))
    // The issue resolved longest comes back.
    val abort = regressed.issue("web-search-abort")
    assertEquals(DemoStatus.Unresolved, abort.status)
    assertEquals(t.epochMs, abort.regressedAt)
    assertEquals(t.epochMs, abort.inboxSince)
    assertEquals(t.epochMs, abort.quietSpans.single().to)

    val arrived = fresh.copy(happeningCount = 3).after(Happening(t, alwaysNew = false))
    assertEquals(fresh.issues.size + 1, arrived.issues.size)
    assertEquals(4, arrived.happeningCount)
  }

  @Test
  fun anArchivedIssueWithAnOpenQuietSpan_escalatesWithEverySpanClosed_andItsBurstShows() {
    val t = start + Duration.ofHours(3)
    val resize = fresh.issue("web-resize-observer")
    val withOpenSpan =
      fresh.copy(
        happeningCount = 1,
        issues = fresh.issues.map { if (it.id == resize.id) it.copy(quietSpans = listOf(DemoQuietSpan(from = (t - Duration.ofHours(5)).epochMs))) else it },
      )

    val escalated = withOpenSpan.after(Happening(t, alwaysNew = false)).issue("web-resize-observer")

    assertEquals(listOf(DemoQuietSpan(from = (t - Duration.ofHours(5)).epochMs, to = t.epochMs)), escalated.quietSpans)
    val atBurst = escalated.view(t)!!.count
    assertTrue(escalated.view(t + Duration.ofHours(3))!!.count > atBurst)
  }

  @Test
  fun regressed_skipsIssuesResolvedLessThan2HoursAgo_andFallsBackToEscalating() {
    val t = start + Duration.ofHours(3)
    val onlyJustResolved =
      fresh.copy(
        happeningCount = 2,
        issues =
          fresh.issues.map {
            if (it.status == DemoStatus.Resolved) it.copy(quietSpans = listOf(DemoQuietSpan(from = (t - Duration.ofHours(1)).epochMs))) else it
          },
      )

    val next = onlyJustResolved.after(Happening(t, alwaysNew = false))

    assertTrue(next.issues.filter { it.status == DemoStatus.Resolved }.size == 2)
    assertEquals(t.epochMs, next.issue("web-resize-observer").escalatedAt)
  }

  @Test
  fun escalating_withNothingArchived_picksTheBusiestIssueNotEscalatedThisWeek() {
    val t = start + Duration.ofHours(3)
    val nothingArchived =
      fresh.copy(happeningCount = 1, issues = fresh.issues.map { if (it.status == DemoStatus.Archived) it.copy(status = DemoStatus.Resolved) else it })

    val next = nothingArchived.after(Happening(t, alwaysNew = false))

    // web-fetch-failed (12 an hour) is the busiest unresolved issue.
    assertEquals(t.epochMs, next.issue("web-fetch-failed").escalatedAt)
  }

  @Test
  fun arrivals_goInOrder_andStartAgainWithFreshIds() {
    var state = fresh
    val count = DemoWorld.arrivals.size + 1
    repeat(count) { i -> state = state.after(Happening(start + minutes(i.toLong()), alwaysNew = true)) }

    val arrived = state.issues.drop(fresh.issues.size)
    assertEquals(DemoWorld.arrivals.map { it.key } + DemoWorld.arrivals[0].key, arrived.map { it.contentKey })
    assertEquals(count, arrived.map { it.id }.toSet().size)
    assertEquals(count, arrived.map { it.shortId }.toSet().size)
  }

  @Test
  fun aWeekAway_catchesUpInOneGo() {
    val now = start + Duration.ofDays(7)
    val due = happenings(fresh, start, now, zone)

    val next = advance(fresh, now, zone)

    assertEquals(due.size, next.happeningCount)
    assertEquals(now.epochMs, next.processedUntil)
    assertTrue(due.size in 22..29)
  }

  @Test
  fun aClockGoingBack_changesNothing() {
    val later = advance(fresh, start + Duration.ofDays(1), zone)
    assertSame(later, advance(later, start + Duration.ofHours(2), zone))
  }

  @Test
  fun turningAlertsOn_inAQuietStretch_bringsANewIssue20MinutesLater() {
    val now = quietMoment(advance(fresh, start + Duration.ofHours(2), zone))
    val settled = advance(fresh, now, zone)

    val kicked = settled.withAlertsKick(now, zone)

    assertEquals((now + minutes(20)).epochMs, kicked.kickAt)
    assertEquals(now.epochMs, kicked.processedUntil)
    val arrived = advance(kicked, now + minutes(21), zone).issues.last()
    assertEquals((now + minutes(20)).epochMs, arrived.firstSeen)
    // No daily slot crowds in within 90 minutes after the kick.
    assertEquals(
      listOf(Happening(now + minutes(20), alwaysNew = true)),
      happenings(kicked, now, now + minutes(110), zone),
    )
  }

  @Test
  fun turningAlertsOn_justBeforeAHappening_addsNoKick() {
    val firstSlot = slotsOver(days = 2).first().at
    val now = firstSlot - minutes(30)
    val settled = advance(fresh, now, zone)

    assertSame(settled, settled.withAlertsKick(now, zone))
  }

  /** A moment after [state]'s processing with no happening in the next 50 minutes. */
  private fun quietMoment(state: DemoState): Instant {
    var t = instant(state.processedUntil)
    while (happenings(state, t, t + minutes(50), zone).isNotEmpty()) t += minutes(10)
    return t
  }
}
