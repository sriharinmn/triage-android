package io.github.sriharinmn.triage.ui.detail

import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventTag
import io.github.sriharinmn.triage.data.detail.TagSummary

/** What the Tags tab shows, decided apart from Compose so it can be unit tested. */
internal sealed interface TagsContent {
  /** The issue's main tags with their most common value. */
  data class Distribution(val tags: List<TagSummary>) : TagsContent

  /** The shown event's own tags, while the issue's are missing (loading, or Sentry couldn't give them). */
  data class EventTags(val tags: List<EventTag>) : TagsContent

  data object Loading : TagsContent

  /** Neither could load: the same error, with the same action, as the event tabs show. */
  data class Failed(val error: DetailError) : TagsContent

  /** Nothing went wrong and there are none. */
  data object Empty : TagsContent

  /** A rejected token: the reconnect banner above the tabs already says why. */
  data object Hidden : TagsContent
}

/**
 * The Tags tab's content: the issue's tags, else the event's, else a spinner while [isLoading]. Without any, [tags] being
 * null (never loaded) and an [error] mean the tags couldn't load, so the tab says why instead of "no tags".
 */
internal fun tagsContent(
  tags: List<TagSummary>?,
  eventTags: List<EventTag>,
  isLoading: Boolean = false,
  error: DetailError? = null,
): TagsContent =
  when {
    !tags.isNullOrEmpty() -> TagsContent.Distribution(tags)
    eventTags.isNotEmpty() -> TagsContent.EventTags(eventTags)
    tags != null -> TagsContent.Empty
    isLoading -> TagsContent.Loading
    error == null -> TagsContent.Empty
    error.needsReconnect() -> TagsContent.Hidden
    else -> TagsContent.Failed(error)
  }

/**
 * The Tags tab for this state. The issue's tags load only after the fresh issue, so its error comes first; the event's
 * error explains why its own tags are missing too.
 */
internal fun IssueDetailUiState.tagsTabContent(): TagsContent =
  tagsContent(
    tags = tags,
    eventTags = event?.tags.orEmpty(),
    isLoading = event == null && (isLoadingSummary || isLoadingEvent),
    error = summaryError ?: eventError,
  )
