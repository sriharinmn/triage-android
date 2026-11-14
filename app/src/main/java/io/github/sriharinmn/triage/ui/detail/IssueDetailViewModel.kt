package io.github.sriharinmn.triage.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.IssueOverlay
import io.github.sriharinmn.triage.data.actions.PendingActions
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.IssueDetailRepository
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.data.detail.TagSummary
import io.github.sriharinmn.triage.data.issues.issueBrowserUrl
import io.github.sriharinmn.triage.data.members.Member
import io.github.sriharinmn.triage.data.members.MembersRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One frame on screen: frame [frame] of stack [stack] of the shown event. */
data class FrameRef(val stack: Int, val frame: Int)

/** Who the screen says the issue is assigned to. */
sealed interface ShownAssignee {
  /** Only the cached row is loaded so far, and it doesn't say. */
  data object Unknown : ShownAssignee

  data object Unassigned : ShownAssignee

  data class Named(val name: String) : ShownAssignee
}

/** The assignee sheet while it is open. */
data class AssignSheetState(
  val isLoading: Boolean = true,
  val error: DetailError? = null,
  val members: List<Member> = emptyList(),
  /** The user's own member; null until they answer "Which one is you?" (or if the answer is no longer a member). */
  val me: Member? = null,
) {
  /** The sheet asks "Which one is you?" before it offers assignees. */
  val askingWhoIsMe: Boolean
    get() = !isLoading && error == null && me == null && members.isNotEmpty()
}

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
  /**
   * The issue's main tags; null only while they load or after they failed (the event's own tags show instead). An issue
   * without tag keys comes back as an empty list.
   */
  val tags: List<TagSummary>? = null,
  val isLoadingTags: Boolean = false,
  /** Why the issue's tags couldn't load. */
  val tagsError: DetailError? = null,
  /** Frames whose source lines are showing. */
  val expandedFrames: Set<FrameRef> = emptySet(),
  /** Stacks whose library frames are showing. */
  val revealedStacks: Set<Int> = emptySet(),
  val showAllBreadcrumbs: Boolean = false,
  val isDisconnecting: Boolean = false,
  val disconnectFailed: Boolean = false,
  /** The user's own changes to this issue that Sentry may not reflect yet (pending, or sent after the issue loaded). */
  val overlay: IssueOverlay = IssueOverlay(),
  /** The latest action's notice, for the snackbar. */
  val notice: ActionNotice? = null,
  val assignSheet: AssignSheetState? = null,
  /** Set after Resolve or Archive: the screen closes so the inbox can offer Undo. */
  val closeRequested: Boolean = false,
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

  /** The user's own assignment if they made one, else Sentry's; unknown while only the cached row is loaded. */
  val shownAssignee: ShownAssignee
    get() {
      overlay.assignment?.let { return it.member?.let { member -> ShownAssignee.Named(member.name) } ?: ShownAssignee.Unassigned }
      val summary = summary?.takeIf { it.isFresh } ?: return ShownAssignee.Unknown
      return summary.assignedTo?.let { ShownAssignee.Named(it) } ?: ShownAssignee.Unassigned
    }

  /** Mark reviewed applies while Sentry has the issue up for review and the user hasn't marked it already. */
  val canMarkReviewed: Boolean
    get() = summary?.isUpForReview == true && !overlay.reviewed

  /** Actions name the issue by its short id, so they wait for a summary; none while it's gone or disconnecting. */
  val canAct: Boolean
    get() = summary != null && !isGone && !isDisconnecting
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
  private val pendingActions: PendingActions,
  private val members: MembersRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(IssueDetailUiState(issueId = issueId))
  val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()

  private var summaryJob: Job? = null
  private var eventJob: Job? = null
  private var tagsJob: Job? = null
  private var membersJob: Job? = null

  init {
    summaryJob =
      viewModelScope.launch {
        val cached = repository.cachedSummary(issueId)
        if (cached != null) _state.update { it.copy(summary = cached) }
        loadSummary(cached)
      }
    loadEvent(EventKind.Recommended)
    viewModelScope.launch {
      pendingActions.overlays.collect { all -> _state.update { it.copy(overlay = all[issueId] ?: IssueOverlay()) } }
    }
    viewModelScope.launch { pendingActions.notice.collect { notice -> _state.update { it.copy(notice = notice) } } }
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

  fun onResolve() = act(IssueAction.Resolve, close = true)

  fun onArchive() = act(IssueAction.Archive, close = true)

  fun onMarkReviewed() {
    // A second tap before the button goes away would only restart the Undo window.
    if (_state.value.overlay.reviewed) return
    act(IssueAction.MarkReviewed, close = false)
  }

  fun onCloseHandled() {
    _state.update { it.copy(closeRequested = false) }
  }

  fun onAssignClicked() {
    if (!_state.value.canAct) return
    _state.update { it.copy(assignSheet = AssignSheetState()) }
    loadMembers()
  }

  fun onRetryMembers() {
    _state.update { it.copy(assignSheet = it.assignSheet?.copy(isLoading = true, error = null)) }
    loadMembers()
  }

  /** While the sheet asks "Which one is you?", [member] is the user; after that, the issue is assigned to [member]. */
  fun onMemberChosen(member: Member) {
    val sheet = _state.value.assignSheet ?: return
    if (!sheet.askingWhoIsMe) return assign(member)
    _state.update { it.copy(assignSheet = sheet.copy(me = member)) }
    remember(member.userId)
  }

  fun onUnassign() = assign(null)

  /** "Not you? Change": forget the answer and ask again. */
  fun onNotMe() {
    _state.update { it.copy(assignSheet = it.assignSheet?.copy(me = null)) }
    remember(null)
  }

  fun onAssignSheetDismissed() {
    _state.update { it.copy(assignSheet = null) }
  }

  fun onUndo(notice: ActionNotice.Pending) = pendingActions.undo(notice.action.id)

  fun onRetryAction(notice: ActionNotice.Failed) {
    // Not while disconnecting (or once the issue is gone), as for the action bar.
    if (!_state.value.canAct) return
    pendingActions.onNoticeDismissed(notice)
    pendingActions.submit(account, notice.action.issueId, notice.action.shortId, notice.action.action)
  }

  fun onNoticeDismissed(notice: ActionNotice) = pendingActions.onNoticeDismissed(notice)

  private fun act(action: IssueAction, close: Boolean) {
    val current = _state.value
    val summary = current.summary
    if (!current.canAct || summary == null) return
    pendingActions.submit(account, issueId, summary.shortId, action)
    if (close) _state.update { it.copy(closeRequested = true) }
  }

  private fun assign(member: Member?) {
    _state.update { it.copy(assignSheet = null) }
    act(IssueAction.Assign(member), close = false)
  }

  private fun loadMembers() {
    membersJob?.cancel()
    membersJob =
      viewModelScope.launch {
        val result = members.members(account)
        val myUserId = members.myUserId.first()
        _state.update { state ->
          val sheet = state.assignSheet ?: return@update state
          val next =
            when (result) {
              is DetailResult.Success ->
                sheet.copy(isLoading = false, error = null, members = result.value, me = result.value.firstOrNull { it.userId == myUserId })
              is DetailResult.Failure -> sheet.copy(isLoading = false, error = result.error)
            }
          state.copy(assignSheet = next)
        }
      }
  }

  private fun remember(userId: String?) {
    viewModelScope.launch {
      try {
        members.rememberMe(userId)
      } catch (_: IOException) {
        // Only a convenience: the answer still holds for this visit, and the sheet asks again next time.
      }
    }
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
    // Taken before asking: only changes that went through before this load are sure to be in Sentry's answer.
    val mark = pendingActions.freshnessMark()
    when (val result = repository.summary(account, issueId, cached)) {
      is DetailResult.Success -> {
        _state.update { it.copy(summary = result.value, isLoadingSummary = false) }
        // After the fresh issue is on screen, so the assignee never flashes "unknown".
        pendingActions.onFreshIssue(issueId, mark)
        loadTags(result.value.tagKeys)
      }
      is DetailResult.Failure -> _state.update { it.copy(isLoadingSummary = false, summaryError = result.error) }
    }
  }

  private fun loadTags(keys: List<String>) {
    tagsJob?.cancel()
    _state.update { it.copy(isLoadingTags = true, tagsError = null) }
    tagsJob =
      viewModelScope.launch {
        val result = repository.tags(account, issueId, keys)
        _state.update {
          when (result) {
            is DetailResult.Success -> it.copy(tags = result.value, isLoadingTags = false, tagsError = null)
            // The tab falls back to the event's own tags, or says why there are none.
            is DetailResult.Failure -> it.copy(tags = null, isLoadingTags = false, tagsError = result.error)
          }
        }
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
