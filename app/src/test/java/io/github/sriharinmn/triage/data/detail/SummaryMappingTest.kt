package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.toIssue
import io.github.sriharinmn.triage.remote.AssigneeDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.SentryJson
import io.github.sriharinmn.triage.remote.TagDetailDto
import io.github.sriharinmn.triage.testing.issueDto
import io.github.sriharinmn.triage.testing.readFixture
import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryMappingTest {
  private val detail = SentryJson.decodeFromString<IssueDetailDto>(readFixture("issue_detail.json"))

  @Test
  fun freshIssue_saysWhoItIsAssignedTo_andWhetherItIsUpForReview() {
    val unassigned = detail.toSummary(cached = null)
    assertNull(unassigned.assignedTo)
    assertFalse(unassigned.isUpForReview)
    assertTrue(unassigned.isFresh)

    val assigned =
      detail
        .copy(
          assignedTo = AssigneeDto(type = "user", id = "41", name = "Ana Silva", email = "ana@example.com"),
          inbox = buildJsonObject { put("reason", 0) },
        )
        .toSummary(cached = null)

    assertEquals("Ana Silva", assigned.assignedTo)
    assertTrue(assigned.isUpForReview)
  }

  @Test
  fun aTeamAssignee_isWrittenAsSentryDoes_andAPersonWithoutANameByEmail() {
    assertEquals("#backend", detail.copy(assignedTo = AssigneeDto(type = "team", id = "7", name = "backend")).toSummary(null).assignedTo)
    assertEquals("ana@example.com", detail.copy(assignedTo = AssigneeDto(type = "user", email = "ana@example.com")).toSummary(null).assignedTo)
  }

  @Test
  fun aCachedRow_isNotFresh() {
    assertFalse(issueDto("7").toIssue().toSummary().isFresh)
  }

  @Test
  fun freshIssue_fillsTheWholeSummary() {
    val summary = detail.toSummary(cached = null)

    assertEquals("4201", summary.id)
    assertEquals("TRIAGE-DEMO-1A", summary.shortId)
    assertEquals("cartTotal(shop:cart)", summary.culprit)
    assertEquals(IssueLevel.Error, summary.level)
    assertEquals(IssueBadge.Regressed, summary.badge)
    assertEquals(IssueStatus.Unresolved, summary.status)
    assertEquals(1204L, summary.eventCount)
    assertEquals(340L, summary.userCount)
    assertEquals(Instant.parse("2026-10-20T08:00:00.123456Z"), summary.firstSeen)
    assertEquals("https://example-org.sentry.io/issues/4201/", summary.permalink)
    assertEquals(24, summary.hourlyEvents.size)
    assertEquals(82L, summary.hourlyEvents.sum())
    assertEquals(30, summary.dailyEvents.size)
    assertEquals(90L, summary.dailyEvents.last())
    assertEquals("1.4.0", summary.firstRelease)
    assertEquals("1.4.2", summary.lastRelease)
    assertEquals(9, summary.tagKeys.size)
    assertFalse(summary.isUnhandled)
  }

  @Test
  fun freshIssue_keepsTheCachedUnhandledFlagOnlyWhenSentryOmitsIt() {
    val cached = issueDto("4201").copy(isUnhandled = true).toIssue().toSummary()

    assertTrue(detail.toSummary(cached).isUnhandled)
    assertFalse(detail.copy(isUnhandled = false).toSummary(cached).isUnhandled)
  }

  @Test
  fun aReleaseWithoutAShortVersion_usesItsVersion() {
    val summary = detail.copy(firstRelease = detail.firstRelease?.copy(shortVersion = null)).toSummary(null)

    assertEquals("shop@1.4.0", summary.firstRelease)
  }

  @Test
  fun cachedRow_isASummaryWithoutTheFreshParts() {
    val summary = issueDto("7").toIssue().toSummary()

    assertEquals("DEMO-7", summary.shortId)
    assertEquals(24, summary.hourlyEvents.size)
    assertTrue(summary.dailyEvents.isEmpty())
    assertNull(summary.firstRelease)
    assertNull(summary.lastRelease)
    assertTrue(summary.tagKeys.isEmpty())
    assertEquals(IssueStatus.Unresolved, summary.status)
  }

  @Test
  fun status_readsResolvedAndIgnored() {
    assertEquals(IssueStatus.Resolved, IssueStatus.fromApi("resolved"))
    assertEquals(IssueStatus.Archived, IssueStatus.fromApi("ignored"))
    assertEquals(IssueStatus.Unresolved, IssueStatus.fromApi("reprocessing"))
    assertEquals(IssueStatus.Unresolved, IssueStatus.fromApi(null))
  }

  @Test
  fun tagDetail_becomesItsTopValue() {
    val tag = SentryJson.decodeFromString<TagDetailDto>(readFixture("tag_browser.json")).toTagSummary()

    assertEquals(TagSummary(key = "browser", topValue = "Chrome 128", count = 858, total = 1100), tag)
    assertEquals(78, tag!!.percent)
  }

  @Test
  fun aTagWithoutValues_isNull() {
    assertNull(TagDetailDto(key = "x").toTagSummary())
  }

  @Test
  fun percent_isWholeAndSafe() {
    assertEquals(0, TagSummary("k", "v", 5, 0).percent)
    assertEquals(100, TagSummary("k", "v", 7, 7).percent)
    assertEquals(33, TagSummary("k", "v", 1, 3).percent)
  }

  @Test
  fun chooseTagKeys_prefersCommonTagsThenSentrysOrder() {
    assertEquals(listOf("environment", "release", "os", "browser", "level"), chooseTagKeys(detail.tags.map { it.key }))
    assertEquals(listOf("os.name", "a"), chooseTagKeys(listOf("a", "os.name")))
    assertEquals(listOf("a", "b"), chooseTagKeys(listOf("a", "b", "a")))
    assertTrue(chooseTagKeys(emptyList()).isEmpty())
    assertEquals(MAX_SHOWN_TAGS, chooseTagKeys((1..20).map { "k$it" }).size)
  }
}
