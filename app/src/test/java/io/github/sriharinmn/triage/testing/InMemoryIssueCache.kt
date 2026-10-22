package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.issues.CachedListState
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.data.issues.Project
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Same list semantics as RoomIssueCache (ordered lists, de-duplicated appends), kept in memory. */
class InMemoryIssueCache : IssueCache {
  private val lists = MutableStateFlow<Map<String, List<Issue>>>(emptyMap())
  private val states = MutableStateFlow<Map<String, CachedListState>>(emptyMap())
  private val projects = MutableStateFlow<List<Project>>(emptyList())

  /** When set, [clear] throws it. */
  var clearError: Exception? = null

  override fun observeIssues(listKey: String): Flow<List<Issue>> =
    lists.map { it[listKey].orEmpty() }.distinctUntilChanged()

  override fun observeListState(listKey: String): Flow<CachedListState?> =
    states.map { it[listKey] }.distinctUntilChanged()

  override suspend fun listState(listKey: String): CachedListState? = states.value[listKey]

  override suspend fun replaceList(listKey: String, issues: List<Issue>, nextCursor: String?, refreshedAt: Instant) {
    lists.update { it + (listKey to issues.distinctBy { issue -> issue.id }) }
    states.update { it + (listKey to CachedListState(nextCursor, refreshedAt)) }
  }

  override suspend fun appendPage(listKey: String, issues: List<Issue>, nextCursor: String?) {
    val state = states.value[listKey] ?: return
    lists.update { current ->
      val existing = current[listKey].orEmpty()
      val ids = existing.mapTo(HashSet()) { it.id }
      current + (listKey to existing + issues.filter { ids.add(it.id) })
    }
    states.update { it + (listKey to state.copy(nextCursor = nextCursor)) }
  }

  override fun observeProjects(): Flow<List<Project>> = projects

  override suspend fun replaceProjects(projects: List<Project>) {
    this.projects.value = projects
  }

  override suspend fun prune(cutoff: Instant) {
    val stale = states.value.filterValues { it.refreshedAt < cutoff }.keys
    states.update { it - stale }
    lists.update { it - stale }
  }

  override suspend fun clear() {
    clearError?.let { throw it }
    lists.value = emptyMap()
    states.value = emptyMap()
    projects.value = emptyList()
  }
}
