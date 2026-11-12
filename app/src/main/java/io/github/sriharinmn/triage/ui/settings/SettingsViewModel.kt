package io.github.sriharinmn.triage.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.data.appearance.AppearanceStore
import io.github.sriharinmn.triage.data.appearance.ThemeMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What Settings shows: the connected account, the appearance choices, and Disconnect's progress. */
data class SettingsUiState(
  val orgName: String,
  /** The Sentry server's host, e.g. de.sentry.io or sentry.example.com. */
  val serverHost: String,
  val appearance: AppearanceSettings = AppearanceSettings(),
  val isDisconnecting: Boolean = false,
  val disconnectFailed: Boolean = false,
)

/** "de.sentry.io" from "https://de.sentry.io"; the address as stored when it can't be read as a URL. */
internal fun serverHost(baseUrl: String): String = baseUrl.toHttpUrlOrNull()?.host ?: baseUrl

/**
 * Drives Settings. Appearance choices are saved straight to the store, which the theme follows at once; that store is
 * phone-wide, so Disconnect leaves it alone. [initialAppearance] is what the app already read, so the controls never
 * show the defaults for a frame.
 */
class SettingsViewModel(
  account: Account,
  private val appearance: AppearanceStore,
  private val disconnector: Disconnector,
  initialAppearance: AppearanceSettings = AppearanceSettings(),
) : ViewModel() {
  private val _state =
    MutableStateFlow(
      SettingsUiState(orgName = account.orgName, serverHost = serverHost(account.apiBaseUrl), appearance = initialAppearance)
    )
  val state: StateFlow<SettingsUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch { appearance.settings.collect { settings -> _state.update { it.copy(appearance = settings) } } }
  }

  fun onThemeModeSelected(mode: ThemeMode) = save { setThemeMode(mode) }

  fun onWallpaperColorsChanged(on: Boolean) = save { setUseWallpaperColors(on) }

  /**
   * Forgets the connection. Loads still running below (the inbox) don't need to stop first: the issue repository discards
   * cache writes from loads that started before the clear, so they can't write this account's issues back.
   */
  fun onDisconnectConfirmed() {
    if (_state.value.isDisconnecting) return
    _state.update { it.copy(isDisconnecting = true, disconnectFailed = false) }
    viewModelScope.launch {
      try {
        // On success the account disappears, the root shows Connect, and this ViewModel goes with its session.
        disconnector.disconnect()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Stay connected so the user can try again. Nothing from the exception is shown.
        _state.update { it.copy(isDisconnecting = false, disconnectFailed = true) }
      }
    }
  }

  fun onDisconnectFailureShown() {
    _state.update { it.copy(disconnectFailed = false) }
  }

  private fun save(change: suspend AppearanceStore.() -> Unit) {
    viewModelScope.launch {
      try {
        appearance.change()
      } catch (_: IOException) {
        // The control stays where the store says, so the user sees it didn't change and can try again.
      }
    }
  }
}
