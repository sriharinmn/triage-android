package io.github.sriharinmn.triage.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CacheDao {
  @Query(
    """
    SELECT issues.* FROM issue_list_entries
    JOIN issues ON issues.id = issue_list_entries.issueId
    WHERE issue_list_entries.listKey = :listKey
    ORDER BY issue_list_entries.position
    """
  )
  abstract fun observeIssues(listKey: String): Flow<List<IssueEntity>>

  @Query("SELECT * FROM issue_lists WHERE listKey = :listKey")
  abstract fun observeList(listKey: String): Flow<IssueListEntity?>

  @Query("SELECT * FROM issue_lists WHERE listKey = :listKey")
  abstract suspend fun getList(listKey: String): IssueListEntity?

  @Query("SELECT issueId FROM issue_list_entries WHERE listKey = :listKey")
  abstract suspend fun issueIdsIn(listKey: String): List<String>

  @Query("SELECT COALESCE(MAX(position), -1) FROM issue_list_entries WHERE listKey = :listKey")
  abstract suspend fun lastPosition(listKey: String): Int

  @Upsert abstract suspend fun upsertIssues(issues: List<IssueEntity>)

  @Insert abstract suspend fun insertEntries(entries: List<IssueListEntryEntity>)

  @Upsert abstract suspend fun upsertList(list: IssueListEntity)

  @Query("DELETE FROM issue_list_entries WHERE listKey = :listKey")
  abstract suspend fun deleteEntries(listKey: String)

  @Transaction
  open suspend fun replaceList(listKey: String, issues: List<IssueEntity>, nextCursor: String?, refreshedAtMillis: Long) {
    // Sentry can repeat an issue when the order shifts mid-request; keep the first.
    val unique = issues.distinctBy { it.id }
    deleteEntries(listKey)
    upsertIssues(unique)
    insertEntries(unique.mapIndexed { index, issue -> IssueListEntryEntity(listKey, index, issue.id) })
    upsertList(IssueListEntity(listKey, nextCursor, refreshedAtMillis))
  }

  @Transaction
  open suspend fun appendPage(listKey: String, issues: List<IssueEntity>, nextCursor: String?) {
    val list = getList(listKey) ?: return
    val present = issueIdsIn(listKey).toHashSet()
    // The list's order shifts while the user scrolls, so a later page can repeat issues already shown.
    val fresh = issues.filter { present.add(it.id) }
    // Upsert every issue (not just the new ones) so repeated issues get their latest counts.
    upsertIssues(issues.distinctBy { it.id })
    val start = lastPosition(listKey) + 1
    insertEntries(fresh.mapIndexed { index, issue -> IssueListEntryEntity(listKey, start + index, issue.id) })
    upsertList(list.copy(nextCursor = nextCursor))
  }

  @Query(
    "DELETE FROM issue_list_entries WHERE listKey IN (SELECT listKey FROM issue_lists WHERE refreshedAtMillis < :cutoff)"
  )
  abstract suspend fun deleteEntriesOfListsOlderThan(cutoff: Long)

  @Query("DELETE FROM issue_lists WHERE refreshedAtMillis < :cutoff")
  abstract suspend fun deleteListsOlderThan(cutoff: Long)

  @Query("DELETE FROM issues WHERE id NOT IN (SELECT issueId FROM issue_list_entries)")
  abstract suspend fun deleteOrphanIssues()

  @Transaction
  open suspend fun prune(cutoffMillis: Long) {
    deleteEntriesOfListsOlderThan(cutoffMillis)
    deleteListsOlderThan(cutoffMillis)
    deleteOrphanIssues()
  }

  @Query("SELECT * FROM projects ORDER BY position")
  abstract fun observeProjects(): Flow<List<ProjectEntity>>

  @Query("DELETE FROM projects") abstract suspend fun deleteProjects()

  @Insert abstract suspend fun insertProjects(projects: List<ProjectEntity>)

  @Transaction
  open suspend fun replaceProjects(projects: List<ProjectEntity>) {
    deleteProjects()
    insertProjects(projects)
  }

  @Query("DELETE FROM issue_list_entries") abstract suspend fun deleteAllEntries()

  @Query("DELETE FROM issue_lists") abstract suspend fun deleteAllLists()

  @Query("DELETE FROM issues") abstract suspend fun deleteAllIssues()

  @Transaction
  open suspend fun clearAll() {
    deleteAllEntries()
    deleteAllLists()
    deleteAllIssues()
    deleteProjects()
  }
}
