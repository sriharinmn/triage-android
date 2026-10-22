package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountStore
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.SentryApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSentryApi(
  var organizationsResult: ApiResult<List<OrganizationDto>> = ApiResult.Success(emptyList()),
) : SentryApi {
  /** (baseUrl, token) for every listOrganizations call. */
  val calls = mutableListOf<Pair<String, String>>()

  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> {
    calls += baseUrl to token
    return organizationsResult
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
