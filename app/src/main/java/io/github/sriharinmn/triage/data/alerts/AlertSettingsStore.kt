package io.github.sriharinmn.triage.data.alerts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The user's alert choices, forgotten with the account. A missing or unreadable file reads as the defaults (alerts off). */
interface AlertSettingsStore {
  val settings: Flow<AlertSettings>

  /** Switching alerts on also asks for a baseline. */
  suspend fun setEnabled(enabled: Boolean)

  suspend fun setInterval(interval: CheckInterval)

  /** Switching a kind on also asks for a baseline. */
  suspend fun setKind(kind: AlertKind, on: Boolean)

  /** Switching a project on also asks for a baseline. */
  suspend fun setProject(projectId: String, on: Boolean)

  suspend fun setQuietHours(quietHours: QuietHours)

  /** The inbox card was answered; it doesn't show again for this account. */
  suspend fun dismissCard()

  suspend fun clear()
}

internal val Context.alertSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "alert_settings",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAlertSettingsStore(private val dataStore: DataStore<Preferences>) : AlertSettingsStore {
  override val settings: Flow<AlertSettings> =
    dataStore.data
      // Unreadable settings read as the defaults, which keep alerts off until the user turns them on again.
      .retryOnReadError()
      .map { it.toSettings() }

  override suspend fun setEnabled(enabled: Boolean) {
    dataStore.edit { prefs ->
      if (enabled && prefs[Keys.ENABLED] != true) prefs.requestBaseline()
      prefs[Keys.ENABLED] = enabled
    }
  }

  override suspend fun setInterval(interval: CheckInterval) {
    dataStore.edit { it[Keys.INTERVAL] = interval.name }
  }

  override suspend fun setKind(kind: AlertKind, on: Boolean) {
    dataStore.edit { prefs ->
      val off = prefs[Keys.KINDS_OFF].orEmpty()
      if (on && kind.name in off) prefs.requestBaseline()
      prefs[Keys.KINDS_OFF] = if (on) off - kind.name else off + kind.name
    }
  }

  override suspend fun setProject(projectId: String, on: Boolean) {
    dataStore.edit { prefs ->
      val off = prefs[Keys.PROJECTS_OFF].orEmpty()
      if (on && projectId in off) prefs.requestBaseline()
      prefs[Keys.PROJECTS_OFF] = if (on) off - projectId else off + projectId
    }
  }

  override suspend fun setQuietHours(quietHours: QuietHours) {
    dataStore.edit { prefs ->
      prefs[Keys.QUIET_ON] = quietHours.enabled
      prefs[Keys.QUIET_START] = quietHours.startMinute
      prefs[Keys.QUIET_END] = quietHours.endMinute
    }
  }

  override suspend fun dismissCard() {
    dataStore.edit { it[Keys.CARD_DISMISSED] = true }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  /** In the same edit as the switch, so a check can never see the switch without the request. */
  private fun MutablePreferences.requestBaseline() {
    this[Keys.BASELINE_REQUEST] = (this[Keys.BASELINE_REQUEST] ?: 0L) + 1
  }

  private fun Preferences.toSettings(): AlertSettings {
    val kindsOff = this[Keys.KINDS_OFF].orEmpty()
    val defaults = QuietHours()
    return AlertSettings(
      enabled = this[Keys.ENABLED] ?: false,
      interval = CheckInterval.entries.firstOrNull { it.name == this[Keys.INTERVAL] } ?: CheckInterval.Minutes15,
      kinds = AlertKind.entries.filter { it.name !in kindsOff }.toSet(),
      projectsOff = this[Keys.PROJECTS_OFF].orEmpty(),
      quietHours =
        QuietHours(
          enabled = this[Keys.QUIET_ON] ?: false,
          startMinute = this[Keys.QUIET_START].validMinute() ?: defaults.startMinute,
          endMinute = this[Keys.QUIET_END].validMinute() ?: defaults.endMinute,
        ),
      cardDismissed = this[Keys.CARD_DISMISSED] ?: false,
      baselineRequest = this[Keys.BASELINE_REQUEST] ?: 0L,
    )
  }

  private fun Int?.validMinute(): Int? = this?.takeIf { it in 0 until QuietHours.MINUTES_PER_DAY }

  private object Keys {
    val ENABLED = booleanPreferencesKey("enabled")
    val INTERVAL = stringPreferencesKey("interval")
    /** Kinds are stored switched off, like projects, so a kind added in a later version starts on. */
    val KINDS_OFF = stringSetPreferencesKey("kinds_off")
    val PROJECTS_OFF = stringSetPreferencesKey("projects_off")
    val QUIET_ON = booleanPreferencesKey("quiet_on")
    val QUIET_START = intPreferencesKey("quiet_start")
    val QUIET_END = intPreferencesKey("quiet_end")
    val CARD_DISMISSED = booleanPreferencesKey("card_dismissed")
    val BASELINE_REQUEST = longPreferencesKey("baseline_request")
  }
}
