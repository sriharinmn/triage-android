package io.github.sriharinmn.triage.remote

interface SentryApi {
  /** `GET {baseUrl}/api/0/organizations/`, the organizations this token can see. */
  suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>>
}
