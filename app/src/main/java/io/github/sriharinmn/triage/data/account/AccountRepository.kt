package io.github.sriharinmn.triage.data.account

import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.SentryApi
import kotlinx.coroutines.flow.Flow

sealed interface ConnectResult {
  data class Organizations(val organizations: List<OrganizationDto>) : ConnectResult

  data class Error(val reason: ConnectError) : ConnectResult
}

enum class ConnectError {
  EmptyToken,
  OrgTokenNotSupported,
  InvalidToken,
  MissingScopes,
  NoOrganizations,
  RateLimited,
  Network,
  Unknown,
}

class AccountRepository(private val api: SentryApi, private val store: AccountStore) {
  val account: Flow<Account?> = store.account

  suspend fun fetchOrganizations(host: SentryHost, rawToken: String): ConnectResult {
    val token = rawToken.trim()
    if (token.isEmpty()) return ConnectResult.Error(ConnectError.EmptyToken)
    // Organization auth tokens only carry CI scopes and can't read issues.
    if (token.startsWith(ORG_TOKEN_PREFIX)) return ConnectResult.Error(ConnectError.OrgTokenNotSupported)

    return when (val result = api.listOrganizations(host.baseUrl, token)) {
      is ApiResult.Success ->
        if (result.value.isEmpty()) ConnectResult.Error(ConnectError.NoOrganizations)
        else ConnectResult.Organizations(result.value)
      is ApiResult.Failure -> ConnectResult.Error(result.error.toConnectError())
    }
  }

  suspend fun connect(host: SentryHost, rawToken: String, organization: OrganizationDto) {
    // Self-hosted installs often report misconfigured URLs in links, so only trust them on sentry.io.
    val links = if (host is SentryHost.Cloud) organization.links else null
    store.save(
      Account(
        apiBaseUrl = links?.regionUrl.cleanUrl() ?: host.baseUrl,
        webBaseUrl = links?.organizationUrl.cleanUrl() ?: host.baseUrl,
        token = rawToken.trim(),
        orgSlug = organization.slug,
        orgName = organization.name,
      )
    )
  }

  suspend fun disconnect() {
    store.clear()
  }

  private fun String?.cleanUrl(): String? = this?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

  private fun ApiError.toConnectError(): ConnectError =
    when (this) {
      ApiError.Unauthorized -> ConnectError.InvalidToken
      ApiError.Forbidden -> ConnectError.MissingScopes
      is ApiError.RateLimited -> ConnectError.RateLimited
      is ApiError.Network -> ConnectError.Network
      is ApiError.Http,
      ApiError.Parse -> ConnectError.Unknown
    }

  private companion object {
    const val ORG_TOKEN_PREFIX = "sntrys_"
  }
}
