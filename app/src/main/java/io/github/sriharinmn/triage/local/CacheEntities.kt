package io.github.sriharinmn.triage.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One issue, shared by every cached list that contains it. */
@Entity(tableName = "issues")
data class IssueEntity(
  @PrimaryKey val id: String,
  val shortId: String,
  val title: String,
  val culprit: String?,
  /** An [io.github.sriharinmn.triage.data.issues.IssueLevel] name. */
  val level: String,
  /** An [io.github.sriharinmn.triage.data.issues.IssueBadge] name, or null. */
  val badge: String?,
  val isUnhandled: Boolean,
  val eventCount: Long,
  val userCount: Long,
  val firstSeenMillis: Long?,
  val lastSeenMillis: Long?,
  val permalink: String?,
  val projectId: String,
  val projectSlug: String,
  /** Hourly event counts, oldest first, comma-separated. */
  val hourlyEvents: String,
)

/** Issue [issueId] sits at [position] (0-based) in the cached list [listKey]. */
@Entity(tableName = "issue_list_entries", primaryKeys = ["listKey", "position"], indices = [Index("issueId")])
data class IssueListEntryEntity(val listKey: String, val position: Int, val issueId: String)

/** A cached list: when it was last refreshed, and the cursor of its next page (null on the last page). */
@Entity(tableName = "issue_lists")
data class IssueListEntity(@PrimaryKey val listKey: String, val nextCursor: String?, val refreshedAtMillis: Long)

/** One of the user's Sentry projects, kept in the order Sentry returned them. */
@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey val id: String,
  /** Order in Sentry's response. */
  val position: Int,
  val slug: String,
  val name: String,
  val platform: String?,
  /** Environment names, newline-separated (Sentry doesn't allow newlines in them). */
  val environments: String,
)
