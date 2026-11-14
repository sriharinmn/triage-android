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
  /** Saving the organization the user picked; its list stays on screen, with progress, until the inbox replaces it. */
  Saving,
}

/** Where the screen shows an error: in the token field, under the server URL, or under the Connect button. */
enum class ErrorPlace {
  Token,
  ServerUrl,
  Form,
}

/**
 * Only errors about the token mark its field. Address problems mark the URL field when there is one (self-hosted); on
 * sentry.io they show under the button, with everything else.
 */
fun ConnectError.place(hostChoice: HostChoice): ErrorPlace =
  when (this) {
    ConnectError.EmptyToken,
    ConnectError.OrgTokenNotSupported,
    ConnectError.InvalidToken,
    ConnectError.MissingScopes -> ErrorPlace.Token
    ConnectError.InsecureConnection,
    ConnectError.ServerNotFound,
    ConnectError.NotSentryServer -> if (hostChoice == HostChoice.SelfHosted) ErrorPlace.ServerUrl else ErrorPlace.Form
    ConnectError.NoOrganizations,
    is ConnectError.RateLimited,
    ConnectError.Network,
    ConnectError.Unknown,
    ConnectError.SaveFailed -> ErrorPlace.Form
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
  /** Paste found nothing to paste. */
  val pasteEmpty: Boolean = false,
  /** The token page couldn't be opened (no browser); the screen shows this address to copy by hand. */
  val tokenPageUrlToCopy: String? = null,
) {
  val canSubmit: Boolean
    get() =
      step == Step.EnterToken && token.isNotBlank() && (hostChoice == HostChoice.Cloud || selfHostedUrl.isNotBlank())

  /** The error for the token field, if the error is about the token. */
  val tokenError: ConnectError?
    get() = error?.takeIf { it.place(hostChoice) == ErrorPlace.Token }

  /** The error for the server URL field, if the error is about the self-hosted address. */
  val serverUrlError: ConnectError?
    get() = error?.takeIf { it.place(hostChoice) == ErrorPlace.ServerUrl }

  /** Any other error, shown under the Connect button without marking a field. */
  val formError: ConnectError?
    get() = error?.takeIf { it.place(hostChoice) == ErrorPlace.Form }

  override fun toString(): String =
    "ConnectUiState(hostChoice=$hostChoice, selfHostedUrl=$selfHostedUrl, token=<redacted>, step=$step, " +
      "organizations=${organizations.map { it.slug }}, error=$error, urlError=$urlError, canCancel=$canCancel, " +
      "pasteEmpty=$pasteEmpty, tokenPageUrlToCopy=$tokenPageUrlToCopy)"
}

/** Host and token captured at submit time. The token is redacted so it can't leak through logging. */
private data class PendingConnection(val host: SentryHost, val token: String) {
  override fun toString(): String = "PendingConnection(host=$host, token=<redacted>)"
}

/**
 * Drives Connect. It lives in the signed-out session (see AccountGate), which is cleared once an account connects, so a
 * successful connect leaves the state as it is: the progress keeps showing until the inbox replaces the screen.
 */
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
    _state.update { it.copy(hostChoice = choice, urlError = false, error = null, tokenPageUrlToCopy = null) }
  }

  fun onSelfHostedUrlChange(url: String) {
    if (_state.value.step != Step.EnterToken) return
    // A new address makes any error about the old one (or the old token page) stale.
    _state.update { it.copy(selfHostedUrl = url, urlError = false, error = null, tokenPageUrlToCopy = null) }
  }

  fun onTokenChange(token: String) {
    if (_state.value.step != Step.EnterToken) return
    _state.update { it.copy(token = token, error = null, pasteEmpty = false) }
  }

  /** What Paste found on the clipboard: the token, trimmed, or a hint when there was nothing to paste. */
  fun onPasteToken(text: String?) {
    if (_state.value.step != Step.EnterToken) return
    val token = text?.trim().orEmpty()
    if (token.isEmpty()) _state.update { it.copy(pasteEmpty = true) }
    else _state.update { it.copy(token = token, error = null, pasteEmpty = false) }
  }

  /** URL of the page where the user creates a token, or null (and a URL error) if the self-hosted URL is invalid. */
  fun requestTokenPageUrl(): String? {
    val host = currentHost()
    if (host == null) _state.update { it.copy(urlError = true) }
    return host?.tokenPageUrl
  }

  /** No app could open the token page; the screen shows [url] to copy into a browser by hand. */
  fun onTokenPageUnavailable(url: String) {
    _state.update { it.copy(tokenPageUrlToCopy = url) }
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
    _state.update { it.copy(step = Step.Validating, error = null, canCancel = true, pasteEmpty = false) }
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
    // The list stays on screen while saving: going back to the form here is the flicker M2 left.
    _state.update { it.copy(step = Step.Saving) }
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
    // Success changes nothing on screen: the account appears, the inbox replaces Connect, and this ViewModel is cleared.
    pending = null
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
