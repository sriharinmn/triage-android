package io.github.sriharinmn.triage.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.IssueDetailRepository
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.data.detail.TagSummary
import io.github.sriharinmn.triage.data.issues.issueBrowserUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One frame on screen: frame [frame] of stack [stack] of the shown event. */
data class FrameRef(val stack: Int, val frame: Int)

/** Everything the detail screen shows. Each part loads and fails on its own. */
data class IssueDetailUiState(
  val issueId: String,
  /** The cached row at first, then Sentry's fresh issue; null until either arrives. */
  val summary: IssueSummary? = null,
  val isLoadingSummary: Boolean = true,
  /** Why the fresh issue couldn't load; a cached summary stays on screen. */
  val summaryError: DetailError? = null,
  val selectedKind: EventKind = EventKind.Recommended,
  /** Events loaded on this screen, by kind, so switching back to one is instant. */
  val events: Map<EventKind, IssueEvent> = emptyMap(),
  val isLoadingEvent: Boolean = true,
  /** Why the selected kind's event couldn't load. */
  val eventError: DetailError? = null,
  /** The issue's main tags; null until loaded, or if Sentry couldn't give them (the event's own tags show instead). */
  val tags: List<TagSummary>? = null,
  /** Frames whose source lines are showing. */
  val expandedFrames: Set<FrameRef> = emptySet(),
  /** Stacks whose library frames are showing. */
  val revealedStacks: Set<Int> = emptySet(),
  val showAllBreadcrumbs: Boolean = false,
  val isDisconnecting: Boolean = false,
  val disconnectFailed: Boolean = false,
) {
  /** The selected kind's event, once loaded. */
  val event: IssueEvent?
    get() = events[selectedKind]

  /** Sentry no longer has the issue (merged or deleted), so nothing else on the screen matters. */
  val isGone: Boolean
    get() = summaryError == DetailError.NotFound

  /** A rejected or under-permissioned token, which only reconnecting can fix; null otherwise. */
  val reconnectError: DetailError?
    get() = listOf(summaryError, eventError).firstOrNull { it.needsReconnect() }
}

/** A rejected (401) or under-permissioned (403) token: only a new token helps. */
internal fun DetailError?.needsReconnect(): Boolean = this == DetailError.Unauthorized || this == DetailError.Forbidden

/**
 * The app's own frame nearest the crash starts expanded, so its code shows without a tap. In a stack with none of the
 * app's frames, the first frame with code does. Frames without source lines can't expand.
 */
internal fun defaultExpandedFrames(event: IssueEvent?): Set<FrameRef> {
  val frames = event?.stacks?.firstOrNull()?.frames ?: return emptySet()
  val ownCodeOnly = frames.any { it.inApp }
  val index = frames.indices.firstOrNull { (!ownCodeOnly || frames[it].inApp) && frames[it].context.isNotEmpty() }
  return if (index == null) emptySet() else setOf(FrameRef(stack = 0, frame = index))
}

/**
 * Drives the issue detail. It shows the cached summary at once, then loads the fresh issue, Sentry's recommended event
 * and the top tags. Nothing it loads is written to disk.
 */
class IssueDetailViewModel(
  private val account: Account,
  private val issueId: String,
  private val repository: IssueDetailRepository,
  private val disconnector: Disconnector,
) : ViewModel() {
  private val _state = MutableStateFlow(IssueDetailUiState(issueId = issueId))
  val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()

  private var summaryJob: Job? = null
  private var eventJob: Job? = null
  private var tagsJob: Job? = null

  init {
    summaryJob =
      viewModelScope.launch {
        val cached = repository.cachedSummary(issueId)
        if (cached != null) _state.update { it.copy(summary = cached) }
        loadSummary(cached)
      }
    loadEvent(EventKind.Recommended)
  }

  fun onEventKindSelected(kind: EventKind) {
    val current = _state.value
    // The account is being forgotten; nothing more should load for it.
    if (current.isDisconnecting || kind == current.selectedKind) return
    eventJob?.cancel()
    val loaded = current.events[kind]
    _state.update {
      it.copy(
        selectedKind = kind,
        isLoadingEvent = loaded == null,
        eventError = null,
        expandedFrames = defaultExpandedFrames(loaded),
        revealedStacks = emptySet(),
        showAllBreadcrumbs = false,
      )
    }
    if (loaded == null) loadEvent(kind)
  }

  /** Retries whatever failed. Inside a 429's hold the repository answers at once, without asking Sentry. */
  fun onRetry() {
    val current = _state.value
    if (current.isDisconnecting) return
    if (current.summaryError != null && summaryJob?.isActive != true) {
      summaryJob = viewModelScope.launch { loadSummary(current.summary) }
    }
    // Tags that failed after the fresh issue loaded (say, inside a 429's hold) would otherwise stay missing for the
    // whole visit. Only the fresh issue has tag keys; the cached row has none.
    val tagKeys = current.summary?.tagKeys.orEmpty()
    if (current.summaryError == null && tagKeys.isNotEmpty() && current.tags == null && tagsJob?.isActive != true) {
      loadTags(tagKeys)
    }
    if (current.eventError != null && eventJob?.isActive != true) loadEvent(current.selectedKind)
  }

  fun onFrameToggled(frame: FrameRef) {
    _state.update {
      it.copy(expandedFrames = if (frame in it.expandedFrames) it.expandedFrames - frame else it.expandedFrames + frame)
    }
  }

  fun onShowLibraryFrames(stack: Int) {
    _state.update { it.copy(revealedStacks = it.revealedStacks + stack) }
  }

  fun onShowAllBreadcrumbs() {
    _state.update { it.copy(showAllBreadcrumbs = true) }
  }

  /**
   * Forgets the connection so the user can connect with a new token. Loads in flight don't need to stop first: this
   * screen writes nothing to disk, and the issue repository discards cache writes from loads that started before the
   * clear, so the inbox's loads can't write the old account's issues back either.
   */
  fun onReconnectConfirmed() {
    if (_state.value.isDisconnecting) return
    _state.update { it.copy(isDisconnecting = true, disconnectFailed = false) }
    viewModelScope.launch {
      try {
        // On success the account disappears, the root shows Connect, and this ViewModel is discarded.
        disconnector.disconnect()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Stay connected so the user can try again. Nothing from the exception is shown.
        _state.update { it.copy(isDisconnecting = false, disconnectFailed = true) }
      }
    }
  }

  fun onDisconnectFailureShown() {
    _state.update { it.copy(disconnectFailed = false) }
  }

  /** Where "Open in browser" goes: Sentry's https permalink, or a link built from the account. */
  fun browserUrl(): String = issueBrowserUrl(account, issueId, _state.value.summary?.permalink)

  private suspend fun loadSummary(cached: IssueSummary?) {
    _state.update { it.copy(isLoadingSummary = true, summaryError = null) }
    when (val result = repository.summary(account, issueId, cached)) {
      is DetailResult.Success -> {
        _state.update { it.copy(summary = result.value, isLoadingSummary = false) }
        loadTags(result.value.tagKeys)
      }
      is DetailResult.Failure -> _state.update { it.copy(isLoadingSummary = false, summaryError = result.error) }
    }
  }

  private fun loadTags(keys: List<String>) {
    tagsJob?.cancel()
    tagsJob =
      viewModelScope.launch {
        val tags =
          when (val result = repository.tags(account, issueId, keys)) {
            is DetailResult.Success -> result.value
            // The section falls back to the event's own tags; there is nothing to report.
            is DetailResult.Failure -> null
          }
        _state.update { it.copy(tags = tags) }
      }
  }

  private fun loadEvent(kind: EventKind) {
    eventJob?.cancel()
    _state.update { it.copy(isLoadingEvent = true, eventError = null) }
    eventJob =
      viewModelScope.launch {
        val result = repository.event(account, issueId, kind)
        _state.update {
          // A chip switch cancels this job, but a result that slipped past the cancel must not land on another chip.
          if (it.selectedKind != kind) return@update it
          when (result) {
            is DetailResult.Success ->
              it.copy(
                events = it.events + (kind to result.value),
                isLoadingEvent = false,
                expandedFrames = defaultExpandedFrames(result.value),
              )
            is DetailResult.Failure -> it.copy(isLoadingEvent = false, eventError = result.error)
          }
        }
      }
  }
}
