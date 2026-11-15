package io.github.sriharinmn.triage.data.demo

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoIssueViewTest {
  private val now = Instant.parse("2026-11-16T03:30:00Z")
  private val day = Duration.ofDays(1)

  /** An issue of real content (so it has a spec), first seen [firstSeenAgo] before [now]. */
  private fun issue(firstSeenAgo: Duration = Duration.ofDays(20)) =
    DemoIssueState(id = "1", shortId = "SHOP-API-1", contentKey = "api-invoice-rgba", firstSeen = (now - firstSeenAgo).epochMs)

  private fun DemoIssueState.at(time: Instant = now): DemoIssueView = checkNotNull(view(time))

  @Test
  fun anIssueFirstSeenThisWeek_isNew_thenOngoing() {
    val fresh = issue(firstSeenAgo = Duration.ofHours(2))
    assertEquals("unresolved", fresh.at().status)
    assertEquals("new", fresh.at().substatus)
    assertEquals("ongoing", fresh.at(now + Duration.ofDays(7)).substatus)
  }

  @Test
  fun theMostRecentChangeWithinAWeek_namesTheSubstatus() {
    val regressed = issue().copy(regressedAt = (now - day).epochMs)
    assertEquals("regressed", regressed.at().substatus)

    val thenEscalated = regressed.copy(escalatedAt = (now - Duration.ofHours(1)).epochMs)
    assertEquals("escalating", thenEscalated.at().substatus)

    val escalatedLongAgo = regressed.copy(escalatedAt = (now - Duration.ofDays(9)).epochMs)
    assertEquals("regressed", escalatedLongAgo.at().substatus)
    assertEquals("ongoing", escalatedLongAgo.at(now + Duration.ofDays(7)).substatus)
  }

  @Test
  fun resolvedAndArchived_haveSentrysStatuses() {
    val resolved = issue().copy(status = DemoStatus.Resolved).at()
    assertEquals("resolved", resolved.status)
    assertNull(resolved.substatus)

    val archived = issue().copy(status = DemoStatus.Archived).at()
    assertEquals("ignored", archived.status)
    assertEquals("archived_until_escalating", archived.substatus)
  }

  @Test
  fun upForReview_whileUnresolvedAndWithinAWeek() {
    val inbox = issue().copy(inboxSince = (now - day).epochMs)
    assertTrue(inbox.at().forReview)
    assertFalse(inbox.at(now + Duration.ofDays(7)).forReview)
    assertFalse(inbox.copy(status = DemoStatus.Resolved).at().forReview)
    assertFalse(issue().at().forReview)
  }

  @Test
  fun countsAndTimes_comeFromTheTraffic() {
    val view = issue().at()
    assertEquals(view.traffic.summary(now).count, view.count)
    assertEquals(view.traffic.summary(now).lastSeen, view.lastSeen)
    assertEquals(userCount(view.count, view.spec.userShare), view.userCount)
    assertEquals(view.spec.tags.getValue("environment").map { it.first }, view.environments)
  }

  @Test
  fun anIssueWhoseContentIsGone_hasNoView() {
    assertNull(issue().copy(contentKey = "removed-in-a-later-version").view(now))
  }
}
