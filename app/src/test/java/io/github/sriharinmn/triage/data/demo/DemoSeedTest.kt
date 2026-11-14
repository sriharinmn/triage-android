package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedTest {
  private val now = Instant.parse("2026-11-16T03:30:00Z")
  private val state = freshDemoState(now)

  private fun issue(key: String) = state.issues.single { it.contentKey == key }

  @Test
  fun aFreshDemo_startsNow_withEveryStartingIssue() {
    assertEquals(now.toEpochMilli(), state.startedAt)
    assertEquals(now.toEpochMilli(), state.processedUntil)
    assertEquals(0, state.happeningCount)
    assertEquals(0, state.arrivalCount)
    assertNull(state.kickAt)
    assertEquals(DemoWorld.startingIssues.map { it.spec.key }, state.issues.map { it.contentKey })
    assertEquals(state.issues.size, state.issues.map { it.id }.toSet().size)
    assertEquals(state.issues.size, state.issues.map { it.shortId }.toSet().size)
  }

  @Test
  fun shortIds_countUpPerProject_oldestFirst() {
    val android = state.issues.filter { it.shortId.startsWith("SHOP-ANDROID-") }.sortedBy { it.firstSeen }
    assertEquals((41 until 41 + 11).map { "SHOP-ANDROID-$it" }, android.map { it.shortId })
    assertEquals(mapOf("shop-android" to 52, "shop-web" to 27, "shop-api" to 73), state.nextShortNumber)
  }

  @Test
  fun eachStartingState_isSetUp() {
    val day = Duration.ofDays(1).toMillis()
    val hour = Duration.ofHours(1).toMillis()
    val t = now.toEpochMilli()

    val regressed = issue("android-fragment-detached")
    assertEquals(t - 30 * hour, regressed.regressedAt)
    assertEquals(listOf(DemoQuietSpan(from = t - 30 * hour - 3 * day, to = t - 30 * hour)), regressed.quietSpans)
    assertEquals(regressed.regressedAt, regressed.inboxSince)

    val escalating = issue("android-anr-main")
    assertEquals(t - 5 * hour, escalating.escalatedAt)
    assertEquals(listOf(t - 5 * hour), escalating.bursts)
    assertEquals(escalating.escalatedAt, escalating.inboxSince)

    val archived = issue("web-resize-observer")
    assertEquals(DemoStatus.Archived, archived.status)
    assertEquals(t - 9 * day, archived.archivedAt)
    assertNull(archived.inboxSince)

    val resolved = issue("android-db-locked")
    assertEquals(DemoStatus.Resolved, resolved.status)
    assertEquals(listOf(DemoQuietSpan(from = t - 6 * day)), resolved.quietSpans)
    assertEquals("1001", resolved.assigneeId)

    val new = issue("api-postal-code")
    assertEquals(t - 9 * hour, new.firstSeen)
    assertEquals(new.firstSeen, new.inboxSince)

    val ongoing = issue("android-socket-timeout")
    assertNull(ongoing.inboxSince)
    assertEquals("1003", ongoing.assigneeId)
  }

  @Test
  fun everyIssueHasAView() {
    assertEquals(state.issues.size, state.views(now).size)
    assertNotNull(state.view(state.issues.first().id, now))
    assertNull(state.view("no-such-issue", now))
    assertTrue(state.views(now).all { it.lastSeen <= now })
  }
}
