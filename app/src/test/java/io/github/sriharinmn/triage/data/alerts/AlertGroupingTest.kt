package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.issues.InboxTab
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertGroupingTest {
  private fun finding(id: String, kind: AlertKind = AlertKind.New, project: String = "triage-demo", projectId: String = "p1") =
    AlertFinding(id, "DEMO-$id", "Error $id", project, projectId, kind, eventCount = 3, userCount = 1)

  private fun group(
    findings: List<AlertFinding>,
    capped: Set<AlertKind> = emptySet(),
    backlog: Map<String, AlertKind> = emptyMap(),
    quiet: Boolean = false,
  ) = groupAlerts(findings, capped, backlog, quiet)

  @Test
  fun upToThree_getANotificationEach() {
    val findings = listOf(finding("1", AlertKind.Regressed), finding("2"), finding("3"))

    assertEquals(GroupedAlerts(AlertPost.Singles(findings), backlog = emptyMap()), group(findings))
  }

  @Test
  fun fourOrMore_becomeOneSummary_namingTheirProject() {
    val findings = listOf(finding("1", AlertKind.Regressed), finding("2"), finding("3"), finding("4"))

    assertEquals(
      GroupedAlerts(AlertPost.Summary(mapOf(AlertKind.New to 3, AlertKind.Regressed to 1), "triage-demo", "p1"), emptyMap()),
      group(findings),
    )
  }

  @Test
  fun aSummaryAcrossProjects_namesNone() {
    val findings = listOf(finding("1"), finding("2"), finding("3"), finding("4", project = "triage-demo-android", projectId = "p2"))

    assertEquals(AlertPost.Summary(mapOf(AlertKind.New to 4), projectSlug = null, projectId = null), group(findings).post)
  }

  @Test
  fun nothingFound_postsNothing() {
    assertEquals(GroupedAlerts(post = null, backlog = emptyMap()), group(emptyList()))
  }

  @Test
  fun duringQuietHours_everythingWaits_withItsStrongestKind() {
    val grouped =
      group(listOf(finding("1", AlertKind.Regressed), finding("2")), backlog = mapOf("1" to AlertKind.New), quiet = true)

    assertEquals(GroupedAlerts(post = null, backlog = mapOf("1" to AlertKind.Regressed, "2" to AlertKind.New)), grouped)
  }

  @Test
  fun afterQuietHours_theBacklogAndNewFindings_areOneSummary_evenIfFew() {
    val grouped = group(listOf(finding("2")), backlog = mapOf("1" to AlertKind.Regressed))

    assertEquals(
      GroupedAlerts(AlertPost.Summary(mapOf(AlertKind.Regressed to 1, AlertKind.New to 1), projectSlug = null, projectId = null), emptyMap()),
      grouped,
    )
  }

  @Test
  fun aBacklogAlone_isASummary() {
    assertEquals(
      AlertPost.Summary(mapOf(AlertKind.Escalating to 1), projectSlug = null, projectId = null),
      group(emptyList(), backlog = mapOf("1" to AlertKind.Escalating)).post,
    )
  }

  @Test
  fun aKindWhoseFirstPageWasFull_isAlwaysSummarised() {
    assertEquals(
      AlertPost.Summary(mapOf(AlertKind.Regressed to 1), "triage-demo", "p1"),
      group(listOf(finding("1", AlertKind.Regressed)), capped = setOf(AlertKind.Regressed)).post,
    )
  }

  @Test
  fun aFullPageWithNothingNew_doesNotForceASummary() {
    val findings = listOf(finding("1"))

    assertEquals(AlertPost.Singles(findings), group(findings, capped = setOf(AlertKind.Regressed)).post)
  }

  @Test
  fun aSummary_opensTheStrongestKindsTab() {
    val summary = AlertPost.Summary(mapOf(AlertKind.New to 5, AlertKind.Escalating to 1), projectSlug = null, projectId = null)

    assertEquals(AlertKind.Escalating, summary.strongest)
    assertEquals(InboxTab.Escalating, summary.strongest.inboxTab)
  }
}
