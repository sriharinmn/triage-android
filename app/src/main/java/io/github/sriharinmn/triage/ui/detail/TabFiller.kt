package io.github.sriharinmn.triage.ui.detail

import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * The height in px of the space at the end of a tab's list. It makes the content below the tab row at least one
 * [viewport] minus the [tabRow] tall, so a short tab can keep the tab row pinned at the top like a long one.
 *
 * [contentTop] and [contentBottom] bound the tab's content (everything between the tab row and this space) when all of it
 * is on screen, else null: then the content is at least a screen tall and the space is just [minPx]. Until the list has
 * been [laidOut] with its current content, the space is a whole viewport, so the first layout has room for any scroll
 * position and nothing gets clamped. The result is never below [minPx].
 */
internal fun tabFillerPx(viewport: Int, tabRow: Int, contentTop: Int?, contentBottom: Int?, minPx: Int, laidOut: Boolean): Int {
  val filler =
    when {
      !laidOut -> viewport
      contentTop == null || contentBottom == null -> minPx
      else -> viewport - tabRow - (contentBottom - contentTop)
    }
  return maxOf(minPx, filler)
}

/**
 * [tabFillerPx] for a tab's list as this layout shows it: the tab row is item [tabsIndex], the filler is the last item
 * and the tab's content is everything in between. [layoutBefore] is the layout the list's state held before the list
 * was shown or its content last changed; until a newer one arrives, the list hasn't been laid out with its content.
 */
internal fun LazyListLayoutInfo.tabFillerPx(layoutBefore: LazyListLayoutInfo, viewport: Int, tabsIndex: Int, minPx: Int): Int {
  val items = visibleItemsInfo
  val tabRow = items.firstOrNull { it.index == tabsIndex }
  val firstContent = tabsIndex + 1
  val lastContent = totalItemsCount - 2
  val first = items.firstOrNull { it.index == firstContent }
  val last = items.firstOrNull { it.index == lastContent }
  // Measured from the content's own items: a pinned tab row sits at the top whatever the scroll, so its offset says nothing.
  val bounds =
    when {
      tabRow == null -> null
      lastContent < firstContent -> 0 to 0
      first != null && last != null -> first.offset to last.offset + last.size
      else -> null
    }
  return tabFillerPx(
    viewport = viewport,
    tabRow = tabRow?.size ?: 0,
    contentTop = bounds?.first,
    contentBottom = bounds?.second,
    minPx = minPx,
    laidOut = this !== layoutBefore && items.isNotEmpty(),
  )
}
