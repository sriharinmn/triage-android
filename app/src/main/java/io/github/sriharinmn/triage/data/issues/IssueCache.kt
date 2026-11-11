package io.github.sriharinmn.triage.data.issues

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** When a cached list was last refreshed, and the cursor of its next page (null on the last page). */
data class CachedListState(val nextCursor: String?, val refreshedAt: Instant)

/**
 * The on-device copy of inbox lists and projects: Room in the app, in memory in JVM tests. Every `suspend` call reports
 * a storage failure (a full or unreadable disk) as [java.io.IOException].
 */
interface IssueCache {
  /** The issues of one list in display order. Empty until the list is loaded. */
  fun observeIssues(listKey: String): Flow<List<Issue>>

  /** Null until the list has been loaded once. */
  fun observeListState(listKey: String): Flow<CachedListState?>

  suspend fun listState(listKey: String): CachedListState?

  /** Any cached copy of issue [id] (rows are shared by every list), or null if no list holds it. */
  suspend fun issue(id: String): Issue?

  /** Replaces the list with a freshly loaded first page. */
  suspend fun replaceList(listKey: String, issues: List<Issue>, nextCursor: String?, refreshedAt: Instant)

  /** Adds the next page, skipping issues already in the list. Does nothing if the list was never loaded. */
  suspend fun appendPage(listKey: String, issues: List<Issue>, nextCursor: String?)

  /**
   * Takes issue [issueId] out of every cached list whose key starts with [listKeyPrefix] (an empty prefix means every
   * list), and forgets its row once no list holds it.
   */
  suspend fun removeFromLists(issueId: String, listKeyPrefix: String)

  fun observeProjects(): Flow<List<Project>>

  suspend fun replaceProjects(projects: List<Project>)

  /** Drops lists not refreshed since [cutoff], and issues no remaining list refers to. */
  suspend fun prune(cutoff: Instant)

  /** Forgets everything; used on disconnect. */
  suspend fun clear()
}
