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

  fun eventChip(kind: EventKind) = "detail_event_${kind.name}"

  fun frame(ref: FrameRef) = "detail_frame_${ref.stack}_${ref.frame}"

  fun showLibraryFrames(stack: Int) = "detail_show_library_$stack"
}
