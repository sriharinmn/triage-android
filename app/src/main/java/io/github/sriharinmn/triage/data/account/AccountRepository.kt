package io.github.sriharinmn.triage.data.account

import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.SentryApi
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface ConnectResult {
  data class Organizations(val organizations: List<OrganizationDto>) : ConnectResult

  data class Error(val reason: ConnectError) : ConnectResult
}

/** Why connecting failed, mapped to a message by the Connect screen. */
sealed interface ConnectError {
  data object EmptyToken : ConnectError

  data object OrgTokenNotSupported : ConnectError

  data object InvalidToken : ConnectError

  data object MissingScopes : ConnectError

  data object NoOrganizations : ConnectError

  /** [retryAfterSeconds] is how long Sentry asked us to wait, when it said. */
  data class RateLimited(val retryAfterSeconds: Long?) : ConnectError

  data object Network : ConnectError

  data object Unknown : ConnectError

  data object SaveFailed : ConnectError
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

  /**
   * Call only after a successful [fetchOrganizations] for the same host and token; it does not re-validate the token.
   */
  suspend fun connect(host: SentryHost, rawToken: String, organization: OrganizationDto) {
    // Self-hosted installs often report misconfigured URLs in links, so only trust them on sentry.io.
    val links = if (host is SentryHost.Cloud) organization.links else null
    store.save(
      Account(
        apiBaseUrl = links?.regionUrl.sentryCloudOrigin() ?: host.baseUrl,
        webBaseUrl = links?.organizationUrl.sentryCloudOrigin() ?: host.baseUrl,
        token = rawToken.trim(),
        orgSlug = organization.slug,
        orgName = organization.name,
      )
    )
  }

  suspend fun disconnect() {
    store.clear()
  }

  /**
   * The `https://host` origin of a server-supplied link, or null unless it is an https link to sentry.io or one of its
   * subdomains. The token is sent to the API link, so a link pointing anywhere else must never be stored.
   */
  private fun String?.sentryCloudOrigin(): String? {
    val url = this?.trim()?.toHttpUrlOrNull() ?: return null
    val isSentryHost = url.host == SENTRY_DOMAIN || url.host.endsWith(".$SENTRY_DOMAIN")
    return if (url.isHttps && isSentryHost) "https://${url.host}" else null
  }

  private fun ApiError.toConnectError(): ConnectError =
    when (this) {
      ApiError.Unauthorized -> ConnectError.InvalidToken
      ApiError.Forbidden -> ConnectError.MissingScopes
      is ApiError.RateLimited -> ConnectError.RateLimited(retryAfterSeconds)
      is ApiError.Network -> ConnectError.Network
      is ApiError.Http,
      ApiError.Parse -> ConnectError.Unknown
    }

  private companion object {
    const val ORG_TOKEN_PREFIX = "sntrys_"
    const val SENTRY_DOMAIN = "sentry.io"
  }
}
