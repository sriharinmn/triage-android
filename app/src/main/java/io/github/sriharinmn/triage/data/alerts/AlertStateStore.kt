package io.github.sriharinmn.triage.data.alerts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** What the background check remembers between runs, forgotten with the account. Ids, kinds and times only. */
interface AlertStateStore {
  val state: Flow<AlertState>

  /** Replaces everything with [state]. */
  suspend fun save(state: AlertState)

  /** The inbox loaded, so the token works again: checks may run after an auth problem. Writes nothing if it isn't set. */
  suspend fun clearAuthProblem()

  suspend fun clear()
}

internal val Context.alertStateDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "alert_state",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAlertStateStore(private val dataStore: DataStore<Preferences>) : AlertStateStore {
  override val state: Flow<AlertState> =
    dataStore.data
      // An unreadable state reads as empty, so the next check is a quiet baseline rather than a burst of alerts.
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { it.toState() }

  override suspend fun save(state: AlertState) {
    dataStore.edit { prefs ->
      prefs.clear()
      state.lastSuccessAt?.let { prefs[Keys.LAST_SUCCESS_AT] = it.toEpochMilli() }
      if (state.lastAttemptFailed) prefs[Keys.LAST_ATTEMPT_FAILED] = true
      state.baselineFor?.let { prefs[Keys.BASELINE_FOR] = it }
      prefs[Keys.REGRESSED] = state.regressed
      prefs[Keys.ESCALATING] = state.escalating
      prefs[Keys.ALERTED_NEW] = state.alertedNew.map { (id, firstSeen) -> "$id$SEPARATOR${firstSeen.toEpochMilli()}" }.toSet()
      prefs[Keys.BACKLOG] = state.backlog.map { (id, kind) -> "$id$SEPARATOR${kind.name}" }.toSet()
      if (state.authProblemNotified) prefs[Keys.AUTH_PROBLEM_NOTIFIED] = true
    }
  }

  override suspend fun clearAuthProblem() {
    // DataStore skips the write when nothing changed, so calling this after every inbox load is cheap.
    dataStore.edit { if (it[Keys.AUTH_PROBLEM_NOTIFIED] == true) it.remove(Keys.AUTH_PROBLEM_NOTIFIED) }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  private fun Preferences.toState(): AlertState =
    AlertState(
      lastSuccessAt = this[Keys.LAST_SUCCESS_AT]?.let(Instant::ofEpochMilli),
      lastAttemptFailed = this[Keys.LAST_ATTEMPT_FAILED] ?: false,
      baselineFor = this[Keys.BASELINE_FOR],
      regressed = this[Keys.REGRESSED].orEmpty(),
      escalating = this[Keys.ESCALATING].orEmpty(),
      alertedNew = this[Keys.ALERTED_NEW].orEmpty().decode { millis -> millis.toLongOrNull()?.let(Instant::ofEpochMilli) },
      backlog = this[Keys.BACKLOG].orEmpty().decode { name -> AlertKind.entries.firstOrNull { it.name == name } },
      authProblemNotified = this[Keys.AUTH_PROBLEM_NOTIFIED] ?: false,
    )

  /** `id|value` entries back into a map; an entry this version can't read (another version's kind) is skipped. */
  private fun <V> Set<String>.decode(value: (String) -> V?): Map<String, V> =
    mapNotNull { entry ->
      val id = entry.substringBeforeLast(SEPARATOR, missingDelimiterValue = "")
      val decoded = value(entry.substringAfterLast(SEPARATOR))
      if (id.isEmpty() || decoded == null) null else id to decoded
    }.toMap()

  private object Keys {
    val LAST_SUCCESS_AT = longPreferencesKey("last_success_at")
    val LAST_ATTEMPT_FAILED = booleanPreferencesKey("last_attempt_failed")
    val BASELINE_FOR = longPreferencesKey("baseline_for")
    val REGRESSED = stringSetPreferencesKey("regressed")
    val ESCALATING = stringSetPreferencesKey("escalating")
    val ALERTED_NEW = stringSetPreferencesKey("alerted_new")
    val BACKLOG = stringSetPreferencesKey("backlog")
    val AUTH_PROBLEM_NOTIFIED = booleanPreferencesKey("auth_problem_notified")
  }

  private companion object {
    /** Sentry issue ids are numbers, so this never appears inside one. */
    const val SEPARATOR = "|"
  }
}
