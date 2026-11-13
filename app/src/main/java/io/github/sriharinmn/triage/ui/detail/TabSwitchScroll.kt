package io.github.sriharinmn.triage.ui.detail

/**
 * Where the new tab's list should scroll to on a tab switch, or null to leave it where it is.
 *
 * [tabsIndex] is the index of the sticky tab row in the list. When the old tab was scrolled far enough that the tab row
 * was pinned at the top and the new tab sits above it, the new tab opens with the tab row pinned too, instead of
 * dropping the tab row back down below the issue header. A tab the user already scrolled deep keeps its own place.
 */
internal fun tabSwitchScrollTarget(oldFirstVisible: Int, newFirstVisible: Int, tabsIndex: Int): Int? =
  if (oldFirstVisible >= tabsIndex && newFirstVisible < tabsIndex) tabsIndex else null
