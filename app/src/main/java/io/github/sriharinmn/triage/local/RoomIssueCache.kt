package io.github.sriharinmn.triage.local

import io.github.sriharinmn.triage.data.issues.CachedListState
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.Project
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The Room-backed [IssueCache]: maps domain types to entities and delegates to [CacheDao] transactions. */
class RoomIssueCache(database: TriageDatabase) : IssueCache {
  private val dao = database.cacheDao()

  override fun observeIssues(listKey: String): Flow<List<Issue>> =
    dao.observeIssues(listKey).map { rows -> rows.map { it.toIssue() } }

  override fun observeListState(listKey: String): Flow<CachedListState?> =
    dao.observeList(listKey).map { it?.toState() }

  override suspend fun listState(listKey: String): CachedListState? = dao.getList(listKey)?.toState()

  override suspend fun replaceList(listKey: String, issues: List<Issue>, nextCursor: String?, refreshedAt: Instant) =
    dao.replaceList(listKey, issues.map { it.toEntity() }, nextCursor, refreshedAt.toEpochMilli())

  override suspend fun appendPage(listKey: String, issues: List<Issue>, nextCursor: String?) =
    dao.appendPage(listKey, issues.map { it.toEntity() }, nextCursor)

  override fun observeProjects(): Flow<List<Project>> = dao.observeProjects().map { rows -> rows.map { it.toProject() } }

  override suspend fun replaceProjects(projects: List<Project>) =
    dao.replaceProjects(projects.mapIndexed { index, project -> project.toEntity(index) })

  override suspend fun prune(cutoff: Instant) = dao.prune(cutoff.toEpochMilli())

  override suspend fun clear() = dao.clearAll()
}

private fun IssueListEntity.toState() = CachedListState(nextCursor, Instant.ofEpochMilli(refreshedAtMillis))

private fun Issue.toEntity() =
  IssueEntity(
    id = id,
    shortId = shortId,
    title = title,
    culprit = culprit,
    level = level.name,
    badge = badge?.name,
    isUnhandled = isUnhandled,
    eventCount = eventCount,
    userCount = userCount,
    firstSeenMillis = firstSeen?.toEpochMilli(),
    lastSeenMillis = lastSeen?.toEpochMilli(),
    permalink = permalink,
    projectId = projectId,
    projectSlug = projectSlug,
    hourlyEvents = hourlyEvents.joinToString(","),
  )

private fun IssueEntity.toIssue() =
  Issue(
    id = id,
    shortId = shortId,
    title = title,
    culprit = culprit,
    level = IssueLevel.entries.firstOrNull { it.name == level } ?: IssueLevel.Unknown,
    badge = IssueBadge.entries.firstOrNull { it.name == badge },
    isUnhandled = isUnhandled,
    eventCount = eventCount,
    userCount = userCount,
    firstSeen = firstSeenMillis?.let(Instant::ofEpochMilli),
    lastSeen = lastSeenMillis?.let(Instant::ofEpochMilli),
    permalink = permalink,
    projectId = projectId,
    projectSlug = projectSlug,
    hourlyEvents = if (hourlyEvents.isEmpty()) emptyList() else hourlyEvents.split(',').map { it.toLongOrNull() ?: 0L },
  )

private fun Project.toEntity(position: Int) =
  ProjectEntity(
    id = id,
    position = position,
    slug = slug,
    name = name,
    platform = platform,
    environments = environments.joinToString("\n"),
  )

private fun ProjectEntity.toProject() =
  Project(
    id = id,
    slug = slug,
    name = name,
    platform = platform,
    environments = if (environments.isEmpty()) emptyList() else environments.split('\n'),
  )
