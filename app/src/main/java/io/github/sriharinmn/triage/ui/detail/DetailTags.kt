package io.github.sriharinmn.triage.ui.detail

import io.github.sriharinmn.triage.data.detail.EventKind

/** Test tags for the detail screen's interactive and scrollable parts. */
object DetailTags {
  const val LIST = "detail_list"
  const val BACK = "detail_back"
  const val OPEN_IN_BROWSER = "detail_open_in_browser"
  const val RETRY = "detail_retry"
  const val RECONNECT = "detail_reconnect"
  const val SHOW_ALL_BREADCRUMBS = "detail_show_all_breadcrumbs"
  const val TREND_24H = "detail_trend_24h"
  const val TREND_30D = "detail_trend_30d"
  const val CHART = "detail_chart"
  const val STAT_EVENTS = "detail_stat_events"
  const val STAT_USERS = "detail_stat_users"
  const val STAT_FIRST_SEEN = "detail_stat_first_seen"
  const val STAT_FIRST_RELEASE = "detail_stat_first_release"
  const val STAT_LAST_SEEN = "detail_stat_last_seen"
  const val RESOLVE = "detail_resolve"
  const val ARCHIVE = "detail_archive"
  const val ASSIGN = "detail_assign"
  const val MARK_REVIEWED = "detail_mark_reviewed"
  const val NOT_ME = "detail_not_me"

  fun eventChip(kind: EventKind) = "detail_event_${kind.name}"

  fun tab(tab: DetailTab) = "detail_tab_${tab.name}"

  fun frame(ref: FrameRef) = "detail_frame_${ref.stack}_${ref.frame}"

  fun showLibraryFrames(stack: Int) = "detail_show_library_$stack"
}
