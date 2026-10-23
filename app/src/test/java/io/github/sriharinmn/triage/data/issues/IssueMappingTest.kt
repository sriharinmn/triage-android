package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueProjectDto
import io.github.sriharinmn.triage.remote.ProjectDto
import io.github.sriharinmn.triage.remote.SentryJson
import io.github.sriharinmn.triage.testing.readFixture
import java.time.Instant
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueMappingTest {
  private val fixture = SentryJson.decodeFromString<List<IssueDto>>(readFixture("issues_page.json"))

  private fun dto(level: String? = "error", substatus: String? = null, culprit: String? = "x", firstSeen: String? = null) =
    IssueDto(
      id = "1",
      shortId = "X-1",
      title = "Boom",
      culprit = culprit,
      level = level,
      substatus = substatus,
      firstSeen = firstSeen,
      project = IssueProjectDto(id = "9", slug = "x"),
    )

  @Test
  fun mapsAFullIssue() {
    val issue = fixture[0].toIssue()

    assertEquals("7001", issue.id)
    assertEquals("TRIAGE-DEMO-1", issue.shortId)
    assertEquals("TypeError: Cannot read properties of undefined (reading 'price')", issue.title)
    assertEquals("?(shop:cart)", issue.culprit)
    assertEquals(IssueLevel.Error, issue.level)
    assertEquals(IssueBadge.New, issue.badge)
    assertFalse(issue.isUnhandled)
    assertEquals(48L, issue.eventCount)
    assertEquals(11L, issue.userCount)
    assertEquals(Instant.parse("2026-10-17T09:12:44.123Z"), issue.firstSeen)
    assertEquals(Instant.parse("2026-10-22T12:40:03Z"), issue.lastSeen)
    assertEquals("https://example-org.sentry.io/issues/7001/", issue.permalink)
    assertEquals("101", issue.projectId)
    assertEquals("triage-demo", issue.projectSlug)
    assertEquals(24, issue.hourlyEvents.size)
    assertEquals(listOf(0L, 1L, 0L), issue.hourlyEvents.take(3))
    assertEquals(9L, issue.hourlyEvents.last())
  }

  @Test
  fun offsetTimestamps_parse() {
    assertEquals(Instant.parse("2026-10-22T11:48:00Z"), fixture[1].toIssue().lastSeen)
  }

  @Test
  fun unhandledFatalRegression() {
    val issue = fixture[1].toIssue()
    assertEquals(IssueLevel.Fatal, issue.level)
    assertEquals(IssueBadge.Regressed, issue.badge)
    assertTrue(issue.isUnhandled)
  }

  @Test
  fun blankCulprit_isNull_andMissingStats_isEmpty() {
    val issue = fixture[2].toIssue()
    assertNull(issue.culprit)
    assertTrue(issue.hourlyEvents.isEmpty())
    assertNull(issue.badge) // ongoing has no badge
  }

  @Test
  fun levels_areCaseInsensitiveAndUnknownFallsBack() {
    assertEquals(IssueLevel.Fatal, dto(level = "FATAL").toIssue().level)
    assertEquals(IssueLevel.Warning, dto(level = "warning").toIssue().level)
    assertEquals(IssueLevel.Info, dto(level = "info").toIssue().level)
    assertEquals(IssueLevel.Debug, dto(level = "debug").toIssue().level)
    assertEquals(IssueLevel.Unknown, dto(level = "sample").toIssue().level)
    assertEquals(IssueLevel.Unknown, dto(level = null).toIssue().level)
  }

  @Test
  fun escalatingSubstatus_isABadge() {
    assertEquals(IssueBadge.Escalating, dto(substatus = "escalating").toIssue().badge)
    assertNull(dto(substatus = "archived_until_escalating").toIssue().badge)
  }

  @Test
  fun badTimestamp_isNull() {
    assertNull(dto(firstSeen = "yesterday").toIssue().firstSeen)
  }

  @Test
  fun project_environmentsAreSortedDistinctAndNonBlank() {
    val project =
      ProjectDto(id = "1", slug = "shop", name = "Shop", environments = listOf("staging", "", "production", "staging"))
        .toProject()
    assertEquals(Project("1", "shop", "Shop", null, listOf("production", "staging")), project)
  }
}
