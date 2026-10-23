package io.github.sriharinmn.triage.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.ConnectError
import io.github.sriharinmn.triage.data.account.ConnectResult
import io.github.sriharinmn.triage.data.account.SentryHost
import io.github.sriharinmn.triage.data.account.normalizeBaseUrl
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.rateLimitHoldSeconds
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HostChoice {
  Cloud,
  SelfHosted,
}

enum class Step {
  EnterToken,
  Validating,
  ChooseOrganization,
}

data class ConnectUiState(
  val hostChoice: HostChoice = HostChoice.Cloud,
  val selfHostedUrl: String = "",
  val token: String = "",
  val step: Step = Step.EnterToken,
  val organizations: List<OrganizationDto> = emptyList(),
  val error: ConnectError? = null,
  val urlError: Boolean = false,
  /** True while the token is being checked with Sentry (not while saving), when Cancel is offered. */
  val canCancel: Boolean = false,
) {
  val canSubmit: Boolean
    get() =
      step == Step.EnterToken && token.isNotBlank() && (hostChoice == HostChoice.Cloud || selfHostedUrl.isNotBlank())

  override fun toString(): String =
    "ConnectUiState(hostChoice=$hostChoice, selfHostedUrl=$selfHostedUrl, token=<redacted>, step=$step, " +
      "organizations=${organizations.map { it.slug }}, error=$error, urlError=$urlError, canCancel=$canCancel)"
}

/** Host and token captured at submit time. The token is redacted so it can't leak through logging. */
private data class PendingConnection(val host: SentryHost, val token: String) {
  override fun toString(): String = "PendingConnection(host=$host, token=<redacted>)"
}

class ConnectViewModel(
  private val repository: AccountRepository,
  private val clock: () -> Instant = Instant::now,
) : ViewModel() {
  private val _state = MutableStateFlow(ConnectUiState())
  val state: StateFlow<ConnectUiState> = _state.asStateFlow()

  /** Host and token captured at submit time, used once the user picks an organization. */
  private var pending: PendingConnection? = null

  /** The in-flight token check, so Cancel can stop it. */
  private var validation: Job? = null

  /** Until when Sentry asked us to wait after a 429; a tap before then doesn't call Sentry. */
  private var rateLimitedUntil: Instant? = null

  fun onHostChoiceChange(choice: HostChoice) {
    if (_state.value.step != Step.EnterToken) return
    _state.update { it.copy(hostChoice = choice, urlError = false, error = null) }
  }

  fun onSelfHostedUrlChange(url: String) {
    if (_state.value.step != Step.EnterToken) return
    _state.update { it.copy(selfHostedUrl = url, urlError = false) }
  }

  fun onTokenChange(token: String) {
    if (_state.value.step != Step.EnterToken) return
    _state.update { it.copy(token = token, error = null) }
  }

  /** URL of the page where the user creates a token, or null (and a URL error) if the self-hosted URL is invalid. */
  fun requestTokenPageUrl(): String? {
    val host = currentHost()
    if (host == null) _state.update { it.copy(urlError = true) }
    return host?.tokenPageUrl
  }

  fun onSubmit() {
    val snapshot = _state.value
    if (!snapshot.canSubmit) return
    val host =
      currentHost()
        ?: run {
          _state.update { it.copy(urlError = true) }
          return
        }
    remainingRateLimitSeconds()?.let { seconds ->
      _state.update { it.copy(error = ConnectError.RateLimited(seconds)) }
      return
    }
    _state.update { it.copy(step = Step.Validating, error = null, canCancel = true) }
    validation =
      viewModelScope.launch {
        val result = repository.fetchOrganizations(host, snapshot.token)
        _state.update { it.copy(canCancel = false) }
        when (result) {
          is ConnectResult.Error -> {
            val reason = result.reason
            if (reason is ConnectError.RateLimited) {
              rateLimitedUntil = clock().plusSeconds(rateLimitHoldSeconds(reason.retryAfterSeconds))
            }
            _state.update { it.copy(step = Step.EnterToken, error = reason) }
          }
          is ConnectResult.Organizations ->
            if (result.organizations.size == 1) {
              finish(host, snapshot.token, result.organizations.single())
            } else {
              pending = PendingConnection(host, snapshot.token)
              _state.update { it.copy(step = Step.ChooseOrganization, organizations = result.organizations) }
            }
        }
      }
  }

  fun onOrganizationChosen(organization: OrganizationDto) {
    val connection = pending ?: return
    pending = null
    _state.update { it.copy(step = Step.Validating) }
    viewModelScope.launch { finish(connection.host, connection.token, organization) }
  }

  fun onBackFromOrganizations() {
    if (_state.value.step != Step.ChooseOrganization) return
    pending = null
    _state.update { it.copy(step = Step.EnterToken, organizations = emptyList()) }
  }

  /** Stops checking the token and returns to the form with everything still filled in. Ignored once saving began. */
  fun onCancelValidation() {
    if (!_state.value.canCancel) return
    validation?.cancel()
    validation = null
    _state.update { it.copy(step = Step.EnterToken, canCancel = false) }
  }

  private suspend fun finish(host: SentryHost, token: String, organization: OrganizationDto) {
    try {
      repository.connect(host, token, organization)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Saving can fail (Keystore, disk). Deliberately drop the exception: its message must not reach state or logs.
      pending = null
      _state.update { it.copy(step = Step.EnterToken, organizations = emptyList(), error = ConnectError.SaveFailed) }
      return
    }
    pending = null
    _state.value = ConnectUiState()
  }

  /** Whole seconds (rounded up) until Sentry's Retry-After passes, or null if we may ask again. */
  private fun remainingRateLimitSeconds(): Long? {
    val until = rateLimitedUntil ?: return null
    val remainingMillis = Duration.between(clock(), until).toMillis()
    if (remainingMillis <= 0) {
      rateLimitedUntil = null
      return null
    }
    return (remainingMillis + 999) / 1000
  }

  private fun currentHost(): SentryHost? {
    val snapshot = _state.value
    return when (snapshot.hostChoice) {
      HostChoice.Cloud -> SentryHost.Cloud
      HostChoice.SelfHosted -> normalizeBaseUrl(snapshot.selfHostedUrl)?.let { SentryHost.SelfHosted(it) }
    }
  }
}
