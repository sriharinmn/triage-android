package io.github.sriharinmn.triage.data.actions

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.members.Member
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One action the user took, as the screens refer to it; [shortId] names the issue in messages. */
data class SubmittedAction(val id: Long, val issueId: String, val shortId: String, val action: IssueAction)

/** What a screen's snackbar should say about the latest action. */
sealed interface ActionNotice {
  val action: SubmittedAction

  /** Waiting out its Undo window; nothing has been sent yet. */
  data class Pending(override val action: SubmittedAction) : ActionNotice

  /** Sentry refused it or couldn't be reached; the issue is back as it was. */
  data class Failed(override val action: SubmittedAction, val error: DetailError) : ActionNotice
}

/** Assigned to [member], or unassigned when it is null. */
data class Assignment(val member: Member?)

/** How the screens show an issue while actions on it wait out their Undo window, or after they went through. */
data class IssueOverlay(
  /** A resolve or archive is waiting, so the issue is hidden on every tab. */
  val hidden: Boolean = false,
  /** Marked reviewed (waiting or done): hidden on For review, and no more "Mark reviewed". */
  val reviewed: Boolean = false,
  /** The user's assignment (waiting or done); null while Sentry's own assignee still applies. */
  val assignment: Assignment? = null,
) {
  /** Whether [tab]'s list should leave the issue out. */
  fun hides(tab: InboxTab): Boolean = hidden || (reviewed && tab == InboxTab.ForReview)
}

/**
 * Holds each issue action for an Undo window before sending it, so an undone action never reaches Sentry (no activity,
 * no notifications for teammates). Lives as long as the process: leaving a screen never cancels an action. [flushNow]
 * sends everything at once (the app going to the background), and [clear] drops everything unsent (the account being
 * forgotten). Called from the main thread; sends run on [scope].
 */
class PendingActions(
  private val actions: IssueActionRepository,
  private val issues: IssueRepository,
  private val scope: CoroutineScope,
  private val undoWindow: Duration = UNDO_WINDOW,
) {
  private class Entry(val submitted: SubmittedAction, val account: Account, val previousAssignment: Assignment?) {
    var job: Job? = null
    /** Set once sending began; from then on Undo is too late. */
    var sending = false
  }

  private val lock = Any()
  private val entries = LinkedHashMap<Long, Entry>()
  private var nextId = 1L

  private val _overlays = MutableStateFlow<Map<String, IssueOverlay>>(emptyMap())
  val overlays: StateFlow<Map<String, IssueOverlay>> = _overlays.asStateFlow()

  private val _notice = MutableStateFlow<ActionNotice?>(null)
  val notice: StateFlow<ActionNotice?> = _notice.asStateFlow()

  /** Shows [action] on [issueId] at once and sends it when the Undo window ends. */
  fun submit(account: Account, issueId: String, shortId: String, action: IssueAction): SubmittedAction {
    val entry =
      synchronized(lock) {
        val submitted = SubmittedAction(nextId++, issueId, shortId, action)
        Entry(submitted, account, _overlays.value[issueId]?.assignment).also { entries[submitted.id] = it }
      }
    updateOverlay(issueId) { it.with(action) }
    _notice.value = ActionNotice.Pending(entry.submitted)
    schedule(entry, afterUndoWindow = true)
    return entry.submitted
  }

  /** Drops action [id] if it hasn't started sending; nothing reaches Sentry. */
  fun undo(id: Long) {
    val entry = synchronized(lock) { entries[id]?.takeIf { !it.sending }?.also { entries.remove(id) } } ?: return
    entry.job?.cancel()
    revert(entry)
    _notice.update { if (it?.action?.id == id) null else it }
  }

  /** A pending notice swiped away sends its action at once (as Gmail does); a failure notice is just cleared. */
  fun onNoticeDismissed(notice: ActionNotice) {
    when (notice) {
      is ActionNotice.Pending -> synchronized(lock) { entries[notice.action.id] }?.let { schedule(it, afterUndoWindow = false) }
      is ActionNotice.Failed -> _notice.update { if (it == notice) null else it }
    }
  }

  /** Sends every action still in its Undo window now; called when the app goes to the background. */
  fun flushNow() {
    val waiting = synchronized(lock) { entries.values.filter { !it.sending } }
    waiting.forEach { schedule(it, afterUndoWindow = false) }
  }

  /** Drops every action, sent or not, and everything shown for them: the account is being forgotten. */
  fun clear() {
    val all = synchronized(lock) { entries.values.toList().also { entries.clear() } }
    all.forEach { it.job?.cancel() }
    _overlays.value = emptyMap()
    _notice.value = null
  }

  private fun schedule(entry: Entry, afterUndoWindow: Boolean) {
    entry.job?.cancel()
    val job =
      scope.launch(start = CoroutineStart.LAZY) {
        if (afterUndoWindow) delay(undoWindow)
        send(entry)
      }
    entry.job = job
    job.start()
  }

  private suspend fun send(entry: Entry) {
    synchronized(lock) {
      if (entries[entry.submitted.id] !== entry || entry.sending) return
      entry.sending = true
    }
    val (issueId, action) = entry.submitted.issueId to entry.submitted.action
    val result = actions.apply(entry.account, issueId, action)
    // clear() cancels this job, but a result can still slip past the cancel.
    synchronized(lock) { if (entries.remove(entry.submitted.id) == null) return }
    when {
      result is DetailResult.Success -> {
        // Leave the cache first, then stop hiding, so the row never flashes back.
        when (action) {
          IssueAction.Resolve,
          IssueAction.Archive -> issues.forget(issueId)
          IssueAction.MarkReviewed -> issues.forget(issueId, InboxTab.ForReview)
          is IssueAction.Assign -> Unit
        }
        updateOverlay(issueId) { if (action is IssueAction.Resolve || action is IssueAction.Archive) it.copy(hidden = false) else it }
        _notice.update { if (it is ActionNotice.Pending && it.action.id == entry.submitted.id) null else it }
      }
      result is DetailResult.Failure && result.error == DetailError.NotFound -> {
        // Merged or deleted in Sentry: it won't come back on a refresh, so stop showing it now.
        issues.forget(issueId)
        revert(entry)
        _notice.value = ActionNotice.Failed(entry.submitted, DetailError.NotFound)
      }
      result is DetailResult.Failure -> {
        revert(entry)
        _notice.value = ActionNotice.Failed(entry.submitted, result.error)
      }
    }
  }

  private fun revert(entry: Entry) {
    updateOverlay(entry.submitted.issueId) {
      when (entry.submitted.action) {
        IssueAction.Resolve,
        IssueAction.Archive -> it.copy(hidden = false)
        IssueAction.MarkReviewed -> it.copy(reviewed = false)
        is IssueAction.Assign -> it.copy(assignment = entry.previousAssignment)
      }
    }
  }

  private fun updateOverlay(issueId: String, change: (IssueOverlay) -> IssueOverlay) {
    _overlays.update { all ->
      val next = change(all[issueId] ?: IssueOverlay())
      if (next == IssueOverlay()) all - issueId else all + (issueId to next)
    }
  }

  private fun IssueOverlay.with(action: IssueAction): IssueOverlay =
    when (action) {
      IssueAction.Resolve,
      IssueAction.Archive -> copy(hidden = true)
      IssueAction.MarkReviewed -> copy(reviewed = true)
      is IssueAction.Assign -> copy(assignment = Assignment(action.member))
    }

  companion object {
    /** How long Undo is offered before an action is sent. */
    val UNDO_WINDOW: Duration = 5.seconds
  }
}
