package io.github.sriharinmn.triage.data.appearance

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreAppearanceStoreTest {
  private val dataStore = InMemoryPreferencesDataStore()
  private val store = DataStoreAppearanceStore(dataStore)

  @Test
  fun nothingSaved_readsTheDefaults() = runTest {
    assertEquals(
      AppearanceSettings(themeMode = ThemeMode.System, useWallpaperColors = false, welcomeSeen = false),
      store.settings.first(),
    )
  }

  @Test
  fun everyChoice_roundTrips() = runTest {
    store.setThemeMode(ThemeMode.Dark)
    store.setUseWallpaperColors(true)
    store.markWelcomeSeen()

    assertEquals(
      AppearanceSettings(themeMode = ThemeMode.Dark, useWallpaperColors = true, welcomeSeen = true),
      store.settings.first(),
    )
  }

  @Test
  fun choicesCanBeTakenBack() = runTest {
    store.setThemeMode(ThemeMode.Light)
    store.setUseWallpaperColors(true)

    store.setThemeMode(ThemeMode.System)
    store.setUseWallpaperColors(false)

    assertEquals(AppearanceSettings(), store.settings.first())
  }

  @Test
  fun aThemeThisVersionDoesNotKnow_fallsBackToSystem() = runTest {
    dataStore.edit { it[stringPreferencesKey("theme_mode")] = "Sepia" }

    assertEquals(ThemeMode.System, store.settings.first().themeMode)
  }

  @Test
  fun anUnreadableFile_readsAsTheDefaults() = runTest {
    assertEquals(AppearanceSettings(), DataStoreAppearanceStore(UnreadableDataStore()).settings.first())
  }
}
