package io.github.sriharinmn.triage.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class TabFillerTest {
  private val viewport = 2000
  private val tabRow = 144
  private val min = 63

  @Test
  fun beforeTheListHasBeenLaidOut_theFillerIsAWholeViewport() {
    assertEquals(viewport, tabFillerPx(viewport, tabRow, contentTop = null, contentBottom = null, minPx = min, laidOut = false))
    assertEquals(viewport, tabFillerPx(viewport, tabRow, contentTop = 1105, contentBottom = 1441, minPx = min, laidOut = false))
  }

  @Test
  fun longContent_getsTheMinimum() {
    assertEquals(min, tabFillerPx(viewport, tabRow, contentTop = null, contentBottom = null, minPx = min, laidOut = true))
  }

  @Test
  fun shortContent_isToppedUpToOneViewportBelowTheTabRow() {
    // 336 px of content under a 144 px tab row: 2000 - 144 - 336.
    assertEquals(1520, tabFillerPx(viewport, tabRow, contentTop = 1105, contentBottom = 1441, minPx = min, laidOut = true))
  }

  @Test
  fun shortContent_measuresFromItsOwnTop_notFromTheScreenTop() {
    // The same 336 px of content, scrolled so the tab row is pinned and the content starts right under it.
    assertEquals(1520, tabFillerPx(viewport, tabRow, contentTop = 144, contentBottom = 480, minPx = min, laidOut = true))
  }

  @Test
  fun contentThatExactlyFills_getsTheMinimum() {
    assertEquals(min, tabFillerPx(viewport, tabRow, contentTop = 144, contentBottom = 2000, minPx = min, laidOut = true))
  }

  @Test
  fun theFiller_isNeverBelowTheMinimum() {
    assertEquals(min, tabFillerPx(viewport, tabRow, contentTop = 144, contentBottom = 1990, minPx = min, laidOut = true))
    assertEquals(min, tabFillerPx(viewport, tabRow, contentTop = 144, contentBottom = 2400, minPx = min, laidOut = true))
    assertEquals(min, tabFillerPx(viewport = 40, tabRow, contentTop = null, contentBottom = null, minPx = min, laidOut = false))
  }

  @Test
  fun aTabWithNoContent_isToppedUpToOneViewportBelowTheTabRow() {
    assertEquals(viewport - tabRow, tabFillerPx(viewport, tabRow, contentTop = 1105, contentBottom = 1105, minPx = min, laidOut = true))
  }
}
