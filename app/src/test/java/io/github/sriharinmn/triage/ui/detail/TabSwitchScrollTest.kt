package io.github.sriharinmn.triage.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSwitchScrollTest {
  @Test
  fun pinnedTabRowAndAnUnvisitedTab_scrollsTheNewTabToTheTabRow() {
    assertEquals(1, tabSwitchScrollTarget(oldFirstVisible = 7, newFirstVisible = 0, tabsIndex = 1))
    assertEquals(1, tabSwitchScrollTarget(oldFirstVisible = 1, newFirstVisible = 0, tabsIndex = 1))
  }

  @Test
  fun pinnedTabRowAndANewTabAlreadyDeep_leavesIt() {
    assertNull(tabSwitchScrollTarget(oldFirstVisible = 7, newFirstVisible = 1, tabsIndex = 1))
    assertNull(tabSwitchScrollTarget(oldFirstVisible = 7, newFirstVisible = 12, tabsIndex = 1))
  }

  @Test
  fun tabRowNotPinned_leavesTheNewTab() {
    assertNull(tabSwitchScrollTarget(oldFirstVisible = 0, newFirstVisible = 0, tabsIndex = 1))
    assertNull(tabSwitchScrollTarget(oldFirstVisible = 0, newFirstVisible = 5, tabsIndex = 1))
  }

  @Test
  fun withTheReconnectBanner_theTabRowIsItemTwo() {
    assertNull(tabSwitchScrollTarget(oldFirstVisible = 1, newFirstVisible = 0, tabsIndex = 2))
    assertEquals(2, tabSwitchScrollTarget(oldFirstVisible = 2, newFirstVisible = 0, tabsIndex = 2))
    assertEquals(2, tabSwitchScrollTarget(oldFirstVisible = 9, newFirstVisible = 1, tabsIndex = 2))
    assertNull(tabSwitchScrollTarget(oldFirstVisible = 9, newFirstVisible = 2, tabsIndex = 2))
  }
}
