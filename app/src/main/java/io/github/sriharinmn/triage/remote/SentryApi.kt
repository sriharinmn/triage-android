package io.github.sriharinmn.triage.remote

import kotlinx.serialization.json.JsonObject

interface SentryApi {
  /** `GET {baseUrl}/api/0/organizations/`, all organizations this token can see (every page). */
  suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>>

  /** `GET {baseUrl}/api/0/organizations/{org}/projects/` (every page); each project lists its environments. */
  suspend fun listProjects(baseUrl: String, token: String, orgSlug: String): ApiResult<List<ProjectDto>>

  /**
   * One page of `GET {baseUrl}/api/0/organizations/{org}/issues/` with hourly 24h stats. Pass the previous page's
   * [Page.nextCursor] as [cursor], or null for the first page.
   */
  suspend fun listIssues(
    baseUrl: String,
    token: String,
    orgSlug: String,
    params: IssueListParams,
    cursor: String?,
  ): ApiResult<Page<IssueDto>>

  /** `GET {baseUrl}/api/0/organizations/{org}/issues/{issueId}/`. */
  suspend fun getIssue(baseUrl: String, token: String, orgSlug: String, issueId: String): ApiResult<IssueDetailDto>

  /**
   * `GET {baseUrl}/api/0/organizations/{org}/issues/{issueId}/events/{eventId}/`, where [eventId] is an event id or one
   * of `recommended`, `latest` and `oldest`.
   */
  suspend fun getIssueEvent(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    eventId: String,
  ): ApiResult<EventDto>

  /** `GET {baseUrl}/api/0/organizations/{org}/issues/{issueId}/tags/{key}/`. */
  suspend fun getIssueTag(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    key: String,
  ): ApiResult<TagDetailDto>

  /** `PUT {baseUrl}/api/0/organizations/{org}/issues/{issueId}/` with [changes] as the JSON body. */
  suspend fun updateIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    changes: JsonObject,
  ): ApiResult<Unit>

  /** `GET {baseUrl}/api/0/organizations/{org}/members/` (every page), including invites that have no user yet. */
  suspend fun listMembers(baseUrl: String, token: String, orgSlug: String): ApiResult<List<MemberDto>>
}
