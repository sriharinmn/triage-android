package io.github.sriharinmn.triage.data.appearance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Light or dark: following the phone (the default), or always one of them. */
enum class ThemeMode {
  System,
  Light,
  Dark,
}

/** Whether the screens use the dark scheme, given whether the phone is in dark mode. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean =
  when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
  }

/** How Triage looks, and whether the welcome is behind the user (DataStore file `appearance`). */
data class AppearanceSettings(
  val themeMode: ThemeMode = ThemeMode.System,
  /** Android 12+'s colors from the wallpaper instead of Triage's cobalt; off by default. */
  val useWallpaperColors: Boolean = false,
  /** Get started was tapped, or an account was connected: without an account the app goes straight to Connect. */
  val welcomeSeen: Boolean = false,
)

/**
 * The phone-wide look and first-launch choices. Unlike every other store, this one is **not** forgotten on Disconnect:
 * none of it is about the account.
 */
interface AppearanceStore {
  val settings: Flow<AppearanceSettings>

  suspend fun setThemeMode(mode: ThemeMode)

  suspend fun setUseWallpaperColors(on: Boolean)

  /** DataStore skips the write when the flag is already set. */
  suspend fun markWelcomeSeen()
}

internal val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "appearance",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAppearanceStore(private val dataStore: DataStore<Preferences>) : AppearanceStore {
  override val settings: Flow<AppearanceSettings> =
    dataStore.data
      // An unreadable file reads as the defaults: the phone's theme, cobalt, and the welcome if there's no account.
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { prefs ->
        AppearanceSettings(
          themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] } ?: ThemeMode.System,
          useWallpaperColors = prefs[Keys.WALLPAPER_COLORS] ?: false,
          welcomeSeen = prefs[Keys.WELCOME_SEEN] ?: false,
        )
      }

  override suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  override suspend fun setUseWallpaperColors(on: Boolean) {
    dataStore.edit { it[Keys.WALLPAPER_COLORS] = on }
  }

  override suspend fun markWelcomeSeen() {
    dataStore.edit { it[Keys.WELCOME_SEEN] = true }
  }

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val WALLPAPER_COLORS = booleanPreferencesKey("use_wallpaper_colors")
    val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
  }
}
