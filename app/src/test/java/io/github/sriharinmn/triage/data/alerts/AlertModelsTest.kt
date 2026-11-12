package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.issues.InboxTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlertModelsTest {
  @Test
  fun kindsRank_escalatingOverRegressedOverNew() {
    assertEquals(AlertKind.Escalating, listOf(AlertKind.New, AlertKind.Escalating, AlertKind.Regressed).max())
    assertEquals(AlertKind.Regressed, maxOf(AlertKind.New, AlertKind.Regressed))
  }

  @Test
  fun eachKind_opensItsInboxTab() {
    assertEquals(InboxTab.Unresolved, AlertKind.New.inboxTab)
    assertEquals(InboxTab.Regressed, AlertKind.Regressed.inboxTab)
    assertEquals(InboxTab.Escalating, AlertKind.Escalating.inboxTab)
  }

  @Test
  fun theDefaults_matchTheDesign() {
    val settings = AlertSettings()

    assertFalse(settings.enabled)
    assertEquals(CheckInterval.Minutes15, settings.interval)
    assertEquals(AlertKind.entries.toSet(), settings.kinds)
    assertEquals(emptySet<String>(), settings.projectsOff)
    assertEquals(QuietHours(enabled = false, startMinute = 22 * 60, endMinute = 7 * 60), settings.quietHours)
    assertEquals(listOf(15L, 30L, 60L), CheckInterval.entries.map { it.minutes })
  }
}
