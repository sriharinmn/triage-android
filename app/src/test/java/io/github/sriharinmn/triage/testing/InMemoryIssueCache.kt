package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.issues.CachedListState
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.data.issues.Project
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Mirrors RoomIssueCache's semantics in memory: ordered lists of shared issue rows, de-duplicated appends that
 * refresh repeated issues, and pruning of stale lists and orphan issues. All state is one immutable snapshot, so
 * observers never see a half-applied change.
 */
class InMemoryIssueCache : IssueCache {
  private data class Snapshot(
    /** One row per issue id, shared by every list that contains it. */
    val issues: Map<String, Issue> = emptyMap(),
    /** Issue ids of each list, in display order. */
    val entries: Map<String, List<String>> = emptyMap(),
    val states: Map<String, CachedListState> = emptyMap(),
    val projects: List<Project> = emptyList(),
  )

  private val state = MutableStateFlow(Snapshot())

  /** When set, [clear] throws it. */
  var clearError: Exception? = null

  /** When set, [clear] suspends until it is completed. */
  var clearGate: CompletableDeferred<Unit>? = null

  /** When set, [replaceList], [appendPage], [replaceProjects] and [prune] throw it, as a full disk would. */
  var writeError: IOException? = null

  /** When set, [listState] and [issue] throw it. */
  var readError: IOException? = null

  /** Ids of every stored issue row, including ones no list refers to; lets tests check orphan pruning. */
  val storedIssueIds: Set<String>
    get() = state.value.issues.keys

  override fun observeIssues(listKey: String): Flow<List<Issue>> =
    state.map { s -> s.entries[listKey].orEmpty().mapNotNull { s.issues[it] } }.distinctUntilChanged()

  override fun observeListState(listKey: String): Flow<CachedListState?> =
    state.map { it.states[listKey] }.distinctUntilChanged()

  override suspend fun listState(listKey: String): CachedListState? {
    readError?.let { throw it }
    return state.value.states[listKey]
  }

  override suspend fun issue(id: String): Issue? {
    readError?.let { throw it }
    return state.value.issues[id]
  }

  override suspend fun replaceList(listKey: String, issues: List<Issue>, nextCursor: String?, refreshedAt: Instant) {
    writeError?.let { throw it }
    // Sentry can repeat an issue when the order shifts mid-request; keep the first.
    val unique = issues.distinctBy { it.id }
    state.update { s ->
      s.copy(
        issues = s.issues + unique.associateBy { it.id },
        entries = s.entries + (listKey to unique.map { it.id }),
        states = s.states + (listKey to CachedListState(nextCursor, refreshedAt)),
      )
    }
  }

  override suspend fun appendPage(listKey: String, issues: List<Issue>, nextCursor: String?) {
    writeError?.let { throw it }
    state.update { s ->
      val list = s.states[listKey] ?: return@update s
      val present = s.entries[listKey].orEmpty().toHashSet()
      // Upsert every issue so repeats get their latest data, but only new ids join the list.
      val unique = issues.distinctBy { it.id }
      s.copy(
        issues = s.issues + unique.associateBy { it.id },
        entries = s.entries + (listKey to s.entries[listKey].orEmpty() + unique.map { it.id }.filter { present.add(it) }),
        states = s.states + (listKey to list.copy(nextCursor = nextCursor)),
      )
    }
  }

  override suspend fun removeFromLists(issueId: String, listKeyPrefix: String) {
    writeError?.let { throw it }
    state.update { s ->
      val entries = s.entries.mapValues { (key, ids) -> if (key.startsWith(listKeyPrefix)) ids - issueId else ids }
      val used = entries.values.flatMapTo(HashSet()) { it }
      s.copy(issues = s.issues.filterKeys { it in used }, entries = entries)
    }
  }

  override fun observeProjects(): Flow<List<Project>> = state.map { it.projects }.distinctUntilChanged()

  override suspend fun replaceProjects(projects: List<Project>) {
    writeError?.let { throw it }
    // Sentry's pages can repeat a project; keep the first.
    state.update { it.copy(projects = projects.distinctBy { project -> project.id }) }
  }

  override suspend fun prune(cutoff: Instant) {
    writeError?.let { throw it }
    state.update { s ->
      val stale = s.states.filterValues { it.refreshedAt < cutoff }.keys
      val entries = s.entries - stale
      val used = entries.values.flatMapTo(HashSet()) { it }
      s.copy(
        issues = s.issues.filterKeys { it in used },
        entries = entries,
        states = s.states - stale,
      )
    }
  }

  override suspend fun clear() {
    clearGate?.await()
    clearError?.let { throw it }
    state.value = Snapshot()
  }
}
