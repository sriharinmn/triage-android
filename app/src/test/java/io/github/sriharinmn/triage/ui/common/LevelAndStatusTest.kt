package io.github.sriharinmn.triage.ui.common

import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelAndStatusTest {
  @Test
  fun everyLevel_hasAShape_soColorIsNeverTheOnlySignal() {
    assertEquals(LevelShape.Square, IssueLevel.Fatal.shape)
    assertEquals(LevelShape.Square, IssueLevel.Error.shape)
    assertEquals(LevelShape.Triangle, IssueLevel.Warning.shape)
    assertEquals(LevelShape.Dot, IssueLevel.Info.shape)
    assertEquals(LevelShape.Ring, IssueLevel.Debug.shape)
    assertEquals(LevelShape.Ring, IssueLevel.Unknown.shape)
  }

  @Test
  fun everyStatus_hasAMarkBeforeItsWord() {
    assertEquals("●", IssueBadge.New.symbol)
    assertEquals("↺", IssueBadge.Regressed.symbol)
    assertEquals("↗", IssueBadge.Escalating.symbol)
  }
}
