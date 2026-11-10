package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountStore
import io.github.sriharinmn.triage.remote.ApiError
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

/** One recorded [SentryApi.listIssues] call. */
data class IssueCall(
  val baseUrl: String,
  val token: String,
  val orgSlug: String,
  val params: IssueListParams,
  val cursor: String?,
)

/** One recorded issue-detail call; [what] is "issue", "event:<eventId>" or "tag:<key>". */
data class DetailCall(
  val baseUrl: String,
  val token: String,
  val orgSlug: String,
  val issueId: String,
  val what: String,
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

  /** Every getIssue, getIssueEvent and getIssueTag call, in call order. */
  val detailCalls = mutableListOf<DetailCall>()

  /** Answers getIssue; by default the issue doesn't exist. */
  var issueDetailHandler: suspend (issueId: String) -> ApiResult<IssueDetailDto> = { ApiResult.Failure(ApiError.Http(404)) }

  /** Answers getIssueEvent; by default the event doesn't exist. */
  var eventHandler: suspend (issueId: String, eventId: String) -> ApiResult<EventDto> = { _, _ ->
    ApiResult.Failure(ApiError.Http(404))
  }

  /** Answers getIssueTag; by default the tag doesn't exist. */
  var tagHandler: suspend (key: String) -> ApiResult<TagDetailDto> = { ApiResult.Failure(ApiError.Http(404)) }

  /** Every updateIssue call: the issue id and the changes sent. */
  val updateCalls = mutableListOf<Pair<String, JsonObject>>()

  /** Answers updateIssue; by default Sentry accepts the change. */
  var updateHandler: suspend (issueId: String, changes: JsonObject) -> ApiResult<Unit> = { _, _ -> ApiResult.Success(Unit) }

  /** When set, updateIssue suspends until it is completed. */
  var updateGate: CompletableDeferred<Unit>? = null

  /** orgSlug of every listMembers call. */
  val memberCalls = mutableListOf<String>()

  var membersResult: ApiResult<List<MemberDto>> = ApiResult.Success(emptyList())

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

  override suspend fun getIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
  ): ApiResult<IssueDetailDto> {
    detailCalls += DetailCall(baseUrl, token, orgSlug, issueId, "issue")
    return issueDetailHandler(issueId)
  }

  override suspend fun getIssueEvent(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    eventId: String,
  ): ApiResult<EventDto> {
    detailCalls += DetailCall(baseUrl, token, orgSlug, issueId, "event:$eventId")
    return eventHandler(issueId, eventId)
  }

  override suspend fun getIssueTag(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    key: String,
  ): ApiResult<TagDetailDto> {
    detailCalls += DetailCall(baseUrl, token, orgSlug, issueId, "tag:$key")
    return tagHandler(key)
  }

  override suspend fun updateIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    changes: JsonObject,
  ): ApiResult<Unit> {
    updateCalls += issueId to changes
    updateGate?.await()
    return updateHandler(issueId, changes)
  }

  override suspend fun listMembers(baseUrl: String, token: String, orgSlug: String): ApiResult<List<MemberDto>> {
    memberCalls += orgSlug
    return membersResult
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
