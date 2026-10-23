package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountStore
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.remote.ProjectDto
import io.github.sriharinmn.triage.remote.SentryApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** One recorded [SentryApi.listIssues] call. */
data class IssueCall(
  val baseUrl: String,
  val token: String,
  val orgSlug: String,
  val params: IssueListParams,
  val cursor: String?,
)

class FakeSentryApi(
  var organizationsResult: ApiResult<List<OrganizationDto>> = ApiResult.Success(emptyList()),
  var projectsResult: ApiResult<List<ProjectDto>> = ApiResult.Success(emptyList()),
) : SentryApi {
  /** (baseUrl, token) for every listOrganizations call. */
  val calls = mutableListOf<Pair<String, String>>()

  /** When set, listOrganizations suspends until it is completed. */
  var organizationsGate: CompletableDeferred<Unit>? = null

  /** orgSlug of every listProjects call. */
  val projectCalls = mutableListOf<String>()

  /** When set, listProjects suspends until it is completed. */
  var projectsGate: CompletableDeferred<Unit>? = null

  val issueCalls = mutableListOf<IssueCall>()

  /** Answers listIssues; by default an empty last page. */
  var issuesHandler: suspend (IssueCall) -> ApiResult<Page<IssueDto>> = { ApiResult.Success(Page(emptyList(), null)) }

  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> {
    calls += baseUrl to token
    organizationsGate?.await()
    return organizationsResult
  }

  override suspend fun listProjects(baseUrl: String, token: String, orgSlug: String): ApiResult<List<ProjectDto>> {
    projectCalls += orgSlug
    projectsGate?.await()
    return projectsResult
  }

  override suspend fun listIssues(
    baseUrl: String,
    token: String,
    orgSlug: String,
    params: IssueListParams,
    cursor: String?,
  ): ApiResult<Page<IssueDto>> {
    val call = IssueCall(baseUrl, token, orgSlug, params, cursor)
    issueCalls += call
    return issuesHandler(call)
  }
}

class InMemoryAccountStore(initial: Account? = null) : AccountStore {
  private val state = MutableStateFlow(initial)
  override val account: Flow<Account?> = state
  val current: Account?
    get() = state.value

  /** When set, [save] throws it. */
  var saveError: Exception? = null

  /** When set, [save] suspends until it is completed. */
  var saveGate: CompletableDeferred<Unit>? = null

  /** Number of successful saves. */
  var saveCount = 0

  override suspend fun save(account: Account) {
    saveGate?.await()
    saveError?.let { throw it }
    state.value = account
    saveCount++
  }

  override suspend fun clear() {
    state.value = null
  }
}
