package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.EventDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.remote.MemberDto
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.remote.ProjectDto
import io.github.sriharinmn.triage.remote.SentryApi
import io.github.sriharinmn.triage.remote.TagDetailDto
import kotlinx.serialization.json.JsonObject

/** Sends calls for the demo's server ([DEMO_BASE_URL]) to [demo], and every other call to the [real] Sentry. */
class DemoRoutingSentryApi(private val real: SentryApi, private val demo: SentryApi) : SentryApi {
  private fun pick(baseUrl: String): SentryApi = if (baseUrl == DEMO_BASE_URL) demo else real

  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> =
    pick(baseUrl).listOrganizations(baseUrl, token)

  override suspend fun listProjects(baseUrl: String, token: String, orgSlug: String): ApiResult<List<ProjectDto>> =
    pick(baseUrl).listProjects(baseUrl, token, orgSlug)

  override suspend fun listIssues(
    baseUrl: String,
    token: String,
    orgSlug: String,
    params: IssueListParams,
    cursor: String?,
  ): ApiResult<Page<IssueDto>> = pick(baseUrl).listIssues(baseUrl, token, orgSlug, params, cursor)

  override suspend fun getIssue(baseUrl: String, token: String, orgSlug: String, issueId: String): ApiResult<IssueDetailDto> =
    pick(baseUrl).getIssue(baseUrl, token, orgSlug, issueId)

  override suspend fun getIssueEvent(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    eventId: String,
  ): ApiResult<EventDto> = pick(baseUrl).getIssueEvent(baseUrl, token, orgSlug, issueId, eventId)

  override suspend fun getIssueTag(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    key: String,
  ): ApiResult<TagDetailDto> = pick(baseUrl).getIssueTag(baseUrl, token, orgSlug, issueId, key)

  override suspend fun updateIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    changes: JsonObject,
  ): ApiResult<Unit> = pick(baseUrl).updateIssue(baseUrl, token, orgSlug, issueId, changes)

  override suspend fun listMembers(baseUrl: String, token: String, orgSlug: String): ApiResult<List<MemberDto>> =
    pick(baseUrl).listMembers(baseUrl, token, orgSlug)
}
