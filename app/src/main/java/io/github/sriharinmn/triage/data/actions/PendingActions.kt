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
import kotlinx.coroutines.currentCoroutineContext
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

  /** Waiting out its Undo window; nothing has been sent yet. Cleared the moment sending starts. */
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
  private class Entry(val submitted: SubmittedAction, val account: Account, var previousAssignment: Assignment?) {
    var job: Job? = null
    /** Set once sending began; from then on Undo is too late. */
    var sending = false
  }

  /** What an action changes. A newer action replaces a waiting one that changes the same thing. */
  private enum class Change { Status, Assignee, Review }

  // Guards the entries and every write to the overlays and the notice, so what the screens see always matches the
  // entries: submit and undo run on the main thread while sends finish on [scope].
  private val lock = Any()
  private val entries = LinkedHashMap<Long, Entry>()
  private var nextId = 1L
  /** Counts clear() calls, so the tail of a send that clear() overlapped changes nothing on screen. */
  private var clears = 0L
  /** A failure that landed while a newer action's Undo was showing; it shows once that notice goes. */
  private var heldFailure: ActionNotice.Failed? = null

  private val _overlays = MutableStateFlow<Map<String, IssueOverlay>>(emptyMap())
  val overlays: StateFlow<Map<String, IssueOverlay>> = _overlays.asStateFlow()

  private val _notice = MutableStateFlow<ActionNotice?>(null)
  val notice: StateFlow<ActionNotice?> = _notice.asStateFlow()

  /**
   * Shows [action] on [issueId] at once and sends it when the Undo window ends. It replaces an action on the issue that
   * changes the same thing and is still waiting (a second tap, or Resolve then Archive), so Undo covers what will be
   * sent.
   */
  fun submit(account: Account, issueId: String, shortId: String, action: IssueAction): SubmittedAction {
    val entry =
      synchronized(lock) {
        val replaced = entries.values.filter { !it.sending && it.changes(issueId, action.change) }
        replaced.forEach {
          entries.remove(it.submitted.id)
          it.job?.cancel()
        }
        // Undoing the new assignment goes back to what was there before the one it replaced.
        val previous =
          if (replaced.isEmpty()) _overlays.value[issueId]?.assignment else replaced.first().previousAssignment
        val submitted = SubmittedAction(nextId++, issueId, shortId, action)
        val entry = Entry(submitted, account, previous).also { entries[submitted.id] = it }
        updateOverlay(issueId) { it.with(action) }
        _notice.value = ActionNotice.Pending(submitted)
        entry
      }
    schedule(entry, afterUndoWindow = true)
    return entry.submitted
  }

  /** Drops action [id] if it hasn't started sending; nothing reaches Sentry. */
  fun undo(id: Long) {
    synchronized(lock) {
      val entry = entries[id]?.takeIf { !it.sending } ?: return
      entries.remove(id)
      entry.job?.cancel()
      revert(entry)
      takeDownUndo(id)
    }
  }

  /** A pending notice swiped away sends its action at once (as Gmail does); a failure notice is just cleared. */
  fun onNoticeDismissed(notice: ActionNotice) {
    when (notice) {
      is ActionNotice.Pending -> synchronized(lock) { entries[notice.action.id] }?.let { schedule(it, afterUndoWindow = false) }
      is ActionNotice.Failed -> synchronized(lock) { _notice.update { if (it == notice) null else it } }
    }
  }

  /** Sends every action still in its Undo window now; called when the app goes to the background. */
  fun flushNow() {
    val waiting = synchronized(lock) { entries.values.filter { !it.sending } }
    waiting.forEach { schedule(it, afterUndoWindow = false) }
  }

  /** Drops every action, sent or not, and everything shown for them: the account is being forgotten. */
  fun clear() {
    synchronized(lock) {
      entries.values.forEach { it.job?.cancel() }
      entries.clear()
      clears++
      _overlays.value = emptyMap()
      _notice.value = null
      heldFailure = null
    }
  }

  private fun schedule(entry: Entry, afterUndoWindow: Boolean) {
    val job =
      synchronized(lock) {
        // A send already in flight is never cancelled here; only clear() may do that.
        if (entries[entry.submitted.id] !== entry || entry.sending) return
        entry.job?.cancel()
        scope
          .launch(start = CoroutineStart.LAZY) {
            if (afterUndoWindow) delay(undoWindow)
            send(entry)
          }
          .also { entry.job = it }
      }
    job.start()
  }

  private suspend fun send(entry: Entry) {
    val self = currentCoroutineContext()[Job]
    synchronized(lock) {
      // A job that schedule() superseded may already be running; it must not become the in-flight send.
      if (entries[entry.submitted.id] !== entry || entry.sending || entry.job !== self) return
      entry.sending = true
      // Undo is no longer possible, so stop offering it.
      takeDownUndo(entry.submitted.id)
    }
    val (issueId, action) = entry.submitted.issueId to entry.submitted.action
    val error = (actions.apply(entry.account, issueId, action) as? DetailResult.Failure)?.error
    val clearsBefore =
      synchronized(lock) {
        // clear() cancels this job, but a result can still slip past the cancel: the account is gone, so stop here.
        if (entries.remove(entry.submitted.id) == null) return
        if (error != null && error != DetailError.NotFound) {
          revert(entry)
          showFailure(ActionNotice.Failed(entry.submitted, error))
          return
        }
        clears
      }
    // The cache writes suspend, so they run outside the lock; if clear() ran meanwhile, nothing more is shown.
    when {
      error == null ->
        // Leave the cache first, then stop hiding, so the row never flashes back.
        when (action) {
          IssueAction.Resolve,
          IssueAction.Archive -> {
            issues.forget(issueId)
            synchronized(lock) { if (clears == clearsBefore) showAgain(issueId) }
          }
          IssueAction.MarkReviewed -> issues.forget(issueId, InboxTab.ForReview)
          is IssueAction.Assign -> Unit
        }
      else -> {
        // Merged or deleted in Sentry: it won't come back on a refresh, so stop showing it now.
        issues.forget(issueId)
        synchronized(lock) {
          if (clears != clearsBefore) return
          revert(entry)
          showFailure(ActionNotice.Failed(entry.submitted, DetailError.NotFound))
        }
      }
    }
  }

  /** Shows [failed], or holds it back while a newer action's Undo shows: it must not take that. Call under [lock]. */
  private fun showFailure(failed: ActionNotice.Failed) {
    val current = _notice.value
    if (current is ActionNotice.Pending && current.action.id != failed.action.id) heldFailure = failed
    else _notice.value = failed
  }

  /** Takes down action [id]'s Undo notice, if showing, and shows any failure held back behind it. Call under [lock]. */
  private fun takeDownUndo(id: Long) {
    val current = _notice.value
    if (current is ActionNotice.Pending && current.action.id == id) {
      _notice.value = heldFailure
      heldFailure = null
    }
  }

  /** Takes [entry]'s change off the issue, unless another action on it still shows that change. Call under [lock]. */
  private fun revert(entry: Entry) {
    val issueId = entry.submitted.issueId
    when (entry.submitted.action.change) {
      Change.Status -> showAgain(issueId)
      Change.Review -> if (!isChanging(issueId, Change.Review)) updateOverlay(issueId) { it.copy(reviewed = false) }
      Change.Assignee -> {
        val id = entry.submitted.id
        val newer = entries.values.firstOrNull { it.submitted.id > id && it.changes(issueId, Change.Assignee) }
        // A newer assignment stays on screen; undoing it (or its failing) now goes back to what was before this one.
        if (newer != null) newer.previousAssignment = entry.previousAssignment
        else updateOverlay(issueId) { it.copy(assignment = entry.previousAssignment) }
      }
    }
  }

  /** Stops hiding [issueId] unless another resolve or archive of it is still waiting or sending. Call under [lock]. */
  private fun showAgain(issueId: String) {
    if (!isChanging(issueId, Change.Status)) updateOverlay(issueId) { it.copy(hidden = false) }
  }

  /** Whether an action that makes [change] to [issueId] is still waiting or being sent. Call under [lock]. */
  private fun isChanging(issueId: String, change: Change): Boolean = entries.values.any { it.changes(issueId, change) }

  private fun Entry.changes(issueId: String, change: Change): Boolean =
    submitted.issueId == issueId && submitted.action.change == change

  private fun updateOverlay(issueId: String, change: (IssueOverlay) -> IssueOverlay) {
    _overlays.update { all ->
      val next = change(all[issueId] ?: IssueOverlay())
      if (next == IssueOverlay()) all - issueId else all + (issueId to next)
    }
  }

  private val IssueAction.change: Change
    get() =
      when (this) {
        IssueAction.Resolve,
        IssueAction.Archive -> Change.Status
        is IssueAction.Assign -> Change.Assignee
        IssueAction.MarkReviewed -> Change.Review
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
