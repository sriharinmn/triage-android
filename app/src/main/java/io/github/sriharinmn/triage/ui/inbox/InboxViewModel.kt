package io.github.sriharinmn.triage.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.PendingActions
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.issues.IssueSort
import io.github.sriharinmn.triage.data.issues.LoadResult
import io.github.sriharinmn.triage.data.issues.Project
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the inbox screen shows: the list for [filter], the filter choices, and load and disconnect progress. */
data class InboxUiState(
  val orgName: String,
  val filter: InboxFilter = InboxFilter(),
  val projects: List<Project> = emptyList(),
  val issues: List<Issue> = emptyList(),
  /** When the shown list was last refreshed; null if it has never been loaded. */
  val refreshedAt: Instant? = null,
  val hasMore: Boolean = false,
  val isRefreshing: Boolean = false,
  val isLoadingMore: Boolean = false,
  /** Why the last refresh failed; cleared by the next successful refresh or a filter change. */
  val error: InboxError? = null,
  val loadMoreFailed: Boolean = false,
  val isDisconnecting: Boolean = false,
  val disconnectFailed: Boolean = false,
  /** The latest issue action's notice, for the Undo / failure snackbar. */
  val notice: ActionNotice? = null,
) {
  val selectedProject: Project?
    get() = projects.firstOrNull { it.id == filter.projectId }

  /** Environments offered by the filter: the selected project's, or every project's for "All projects". */
  val environments: List<String>
    get() = (selectedProject?.let(::listOf) ?: projects).flatMap { it.environments }.distinct().sorted()
}

/**
 * Drives the inbox: shows the cached list for the remembered filter at once, refreshes it only when it is stale (or on
 * pull-to-refresh), runs one load at a time, and disconnects the account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
  private val account: Account,
  private val issues: IssueRepository,
  private val settings: InboxSettingsStore,
  private val disconnector: Disconnector,
  private val pendingActions: PendingActions,
  private val clock: () -> Instant = Instant::now,
) : ViewModel() {
  private val _state = MutableStateFlow(InboxUiState(orgName = account.orgName))
  val state: StateFlow<InboxUiState> = _state.asStateFlow()

  /** The filter whose cached list is on screen; null until the saved filter has been read. */
  private val shownFilter = MutableStateFlow<InboxFilter?>(null)
  private var refreshJob: Job? = null
  private var loadMoreJob: Job? = null
  private var startupJob: Job? = null
  private var projectsJob: Job? = null

  init {
    viewModelScope.launch {
      shownFilter
        .filterNotNull()
        .flatMapLatest { filter ->
          // Rows the user just resolved, archived or marked reviewed stay hidden while their Undo window runs.
          issues.observeInbox(filter).combine(pendingActions.overlays) { snapshot, overlays ->
            filter to snapshot.copy(issues = snapshot.issues.filterNot { overlays[it.id]?.hides(filter.tab) == true })
          }
        }
        .collect { (filter, snapshot) ->
          _state.update {
            if (it.filter != filter) it
            else it.copy(issues = snapshot.issues, refreshedAt = snapshot.refreshedAt, hasMore = snapshot.hasMore)
          }
        }
    }
    viewModelScope.launch { pendingActions.notice.collect { notice -> _state.update { it.copy(notice = notice) } } }
    viewModelScope.launch { issues.projects.collect(::onProjectsLoaded) }
    startupJob =
      viewModelScope.launch {
        show(settings.filter.first(), persist = false)
        // A failure here only leaves the filter choices stale; the issue list reports errors itself.
        issues.refreshProjects(account)
      }
  }

  fun onTabSelected(tab: InboxTab) = show(_state.value.filter.copy(tab = tab))

  fun onSortSelected(sort: IssueSort) = show(_state.value.filter.copy(sort = sort))

  fun onEnvironmentSelected(environment: String?) = show(_state.value.filter.copy(environment = environment))

  fun onProjectSelected(projectId: String?) {
    val current = _state.value
    val project = current.projects.firstOrNull { it.id == projectId }
    // An environment the new project doesn't have would always show an empty list.
    val environment = current.filter.environment?.takeIf { project == null || it in project.environments }
    show(current.filter.copy(projectId = projectId, environment = environment))
  }

  fun onRefresh() = startRefresh()

  fun onLoadMore() {
    val current = _state.value
    if (!current.hasMore || current.isRefreshing || current.isLoadingMore || current.isDisconnecting) return
    // After a failure, only onRetryLoadMore asks again; the list's end staying on screen must not retry in a loop.
    if (current.loadMoreFailed) return
    val filter = current.filter
    _state.update { it.copy(isLoadingMore = true) }
    loadMoreJob =
      viewModelScope.launch {
        val result = issues.loadMore(account, filter)
        _state.update {
          if (it.filter != filter) it else it.copy(isLoadingMore = false, loadMoreFailed = result is LoadResult.Failure)
        }
      }
  }

  fun onRetryLoadMore() {
    _state.update { it.copy(loadMoreFailed = false) }
    onLoadMore()
  }

  fun onResolve(issue: Issue) = act(issue, IssueAction.Resolve)

  fun onArchive(issue: Issue) = act(issue, IssueAction.Archive)

  fun onUndo(notice: ActionNotice.Pending) = pendingActions.undo(notice.action.id)

  fun onRetryAction(notice: ActionNotice.Failed) {
    // As for a swipe: the retry would go out with a token that's about to be dropped.
    if (_state.value.isDisconnecting) return
    pendingActions.onNoticeDismissed(notice)
    pendingActions.submit(account, notice.action.issueId, notice.action.shortId, notice.action.action)
  }

  fun onNoticeDismissed(notice: ActionNotice) = pendingActions.onNoticeDismissed(notice)

  private fun act(issue: Issue, action: IssueAction) {
    // The account is being forgotten; an action now would go out with a token that's about to be dropped.
    if (_state.value.isDisconnecting) return
    pendingActions.submit(account, issue.id, issue.shortId, action)
  }

  fun onDisconnectConfirmed() {
    if (_state.value.isDisconnecting) return
    // Stop loading first: a refresh finishing after the cache is cleared would write this account's issues back.
    refreshJob?.cancel()
    loadMoreJob?.cancel()
    startupJob?.cancel()
    projectsJob?.cancel()
    _state.update { it.copy(isDisconnecting = true, isRefreshing = false, isLoadingMore = false, disconnectFailed = false) }
    viewModelScope.launch {
      try {
        // On success the account disappears, the root shows Connect, and this ViewModel is discarded.
        disconnector.disconnect()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Stay connected so the user can try again. Nothing from the exception is shown.
        _state.update { it.copy(isDisconnecting = false, disconnectFailed = true) }
        // The cache may already be cleared and the startup job (the only projects loader) was cancelled; reload so the
        // inbox isn't left empty.
        startRefresh()
        projectsJob = viewModelScope.launch { issues.refreshProjects(account) }
      }
    }
  }

  fun onDisconnectFailureShown() {
    _state.update { it.copy(disconnectFailed = false) }
  }

  private fun onProjectsLoaded(projects: List<Project>) {
    _state.update { it.copy(projects = projects) }
    if (shownFilter.value != null) show(_state.value.filter)
  }

  /** Switches the screen to [requested] (minus a project that no longer exists) and refreshes it if it is stale. */
  private fun show(requested: InboxFilter, persist: Boolean = true) {
    // A tap during disconnect could save a filter after the settings were cleared; the next account would open on it.
    if (_state.value.isDisconnecting) return
    val filter = requested.withKnownProject(_state.value.projects)
    if (filter == shownFilter.value) return
    refreshJob?.cancel()
    loadMoreJob?.cancel()
    _state.update {
      it.copy(
        filter = filter,
        issues = emptyList(),
        refreshedAt = null,
        hasMore = false,
        isRefreshing = false,
        isLoadingMore = false,
        error = null,
        loadMoreFailed = false,
      )
    }
    shownFilter.value = filter
    viewModelScope.launch {
      if (persist) rememberFilter(filter)
      val last = issues.lastRefreshed(filter)
      val stale = last == null || Duration.between(last, clock()) > STALE_AFTER
      if (stale && shownFilter.value == filter) startRefresh()
    }
  }

  private fun startRefresh() {
    if (refreshJob?.isActive == true || _state.value.isDisconnecting) return
    loadMoreJob?.cancel()
    val filter = _state.value.filter
    _state.update { it.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false) }
    refreshJob =
      viewModelScope.launch {
        // Taken before asking: only reviews that went through before this load are sure to be in Sentry's answer.
        val mark = pendingActions.freshnessMark()
        val result = issues.refresh(account, filter)
        // An issue Sentry has put up for review again shows again.
        if (result is LoadResult.Success && filter.tab == InboxTab.ForReview) pendingActions.onFreshReviewList(mark)
        _state.update {
          if (it.filter != filter) it else it.copy(isRefreshing = false, error = (result as? LoadResult.Failure)?.error)
        }
      }
  }

  private suspend fun rememberFilter(filter: InboxFilter) {
    try {
      settings.save(filter)
    } catch (_: IOException) {
      // Remembering the filter is a convenience; the inbox works without it.
    }
  }

  /** A remembered project that was deleted or is no longer visible falls back to all projects. */
  private fun InboxFilter.withKnownProject(projects: List<Project>): InboxFilter =
    if (projectId != null && projects.isNotEmpty() && projects.none { it.id == projectId }) copy(projectId = null)
    else this

  private companion object {
    /** Switching to a list refreshed within this long shows the cache without calling Sentry. */
    val STALE_AFTER: Duration = Duration.ofMinutes(2)
  }
}
