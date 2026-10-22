package io.github.sriharinmn.triage.remote

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
}
