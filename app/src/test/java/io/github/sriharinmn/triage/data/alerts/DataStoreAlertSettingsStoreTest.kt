package io.github.sriharinmn.triage.data.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreAlertSettingsStoreTest {
  private val dataStore = InMemoryPreferencesDataStore()
  private val store = DataStoreAlertSettingsStore(dataStore)

  @Test
  fun nothingSaved_readsTheDefaults() = runTest {
    assertEquals(AlertSettings(), store.settings.first())
  }

  @Test
  fun updateQuietHours_changesTheStoredWindow() = runTest {
    store.setEnabled(true)
    store.setInterval(CheckInterval.Hour1)
    store.setQuietHours(QuietHours(enabled = true, startMinute = 23 * 60))

    store.updateQuietHours { it.copy(endMinute = 6 * 60) }

    val settings = store.settings.first()
    assertEquals(QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 6 * 60), settings.quietHours)
    // An edit of the window must not touch the other settings.
    assertTrue(settings.enabled)
    assertEquals(CheckInterval.Hour1, settings.interval)
  }

  @Test
  fun everyChoice_roundTrips() = runTest {
    store.setEnabled(true)
    store.setInterval(CheckInterval.Hour1)
    store.setKind(AlertKind.Regressed, on = false)
    store.setProject("102", on = false)
    store.setQuietHours(QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 6 * 60 + 30))
    store.dismissCard()

    val settings = store.settings.first()

    assertTrue(settings.enabled)
    assertEquals(CheckInterval.Hour1, settings.interval)
    assertEquals(setOf(AlertKind.New, AlertKind.Escalating), settings.kinds)
    assertEquals(setOf("102"), settings.projectsOff)
    assertEquals(QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 6 * 60 + 30), settings.quietHours)
    assertTrue(settings.cardDismissed)
  }

  @Test
  fun switchingSomethingOn_asksForABaseline_butSwitchingOffDoesNot() = runTest {
    store.setEnabled(true)
    assertEquals(1L, store.settings.first().baselineRequest)

    store.setEnabled(true)
    store.setKind(AlertKind.New, on = false)
    store.setProject("102", on = false)
    store.setEnabled(false)
    assertEquals(1L, store.settings.first().baselineRequest)

    store.setKind(AlertKind.New, on = true)
    store.setProject("102", on = true)
    store.setEnabled(true)

    assertEquals(4L, store.settings.first().baselineRequest)
  }

  @Test
  fun switchingOnAKindOrProjectThatIsAlreadyOn_changesNothing() = runTest {
    store.setKind(AlertKind.New, on = true)
    store.setProject("101", on = true)

    assertEquals(AlertSettings(), store.settings.first())
  }

  @Test
  fun valuesThisVersionDoesNotKnow_fallBackToTheDefaults() = runTest {
    dataStore.edit {
      it[stringPreferencesKey("interval")] = "Minutes5"
      it[stringSetPreferencesKey("kinds_off")] = setOf("Fatal")
      it[intPreferencesKey("quiet_start")] = 5000
      it[intPreferencesKey("quiet_end")] = -1
    }

    assertEquals(AlertSettings(), store.settings.first())
  }

  @Test
  fun clear_restoresTheDefaults() = runTest {
    store.setEnabled(true)
    store.setProject("102", on = false)
    store.dismissCard()

    store.clear()

    assertEquals(AlertSettings(), store.settings.first())
  }

  @Test
  fun anUnreadableFile_readsAsTheDefaults() = runTest {
    assertEquals(AlertSettings(), DataStoreAlertSettingsStore(UnreadableDataStore()).settings.first())
  }

  /** Fails its first read with an IOException, then serves [prefs]. */
  private class FlakyDataStore(private val prefs: Preferences) : DataStore<Preferences> {
    private var failuresLeft = 1

    override val data: Flow<Preferences> = flow {
      if (failuresLeft > 0) {
        failuresLeft--
        throw IOException("disk hiccup")
      }
      emit(prefs)
      // Like a real DataStore, the flow stays open for later changes.
      awaitCancellation()
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(prefs)
  }

  @Test
  fun aReadError_readsAsTheDefaults_thenTheFlowKeepsGoingWithTheRealSettings() = runTest {
    val prefs = preferencesOf(booleanPreferencesKey("enabled") to true)
    val values = mutableListOf<AlertSettings>()
    val job = launch { DataStoreAlertSettingsStore(FlakyDataStore(prefs)).settings.toList(values) }

    advanceUntilIdle()

    assertEquals(listOf(AlertSettings(), AlertSettings(enabled = true)), values)
    assertTrue("the flow must not complete after a read error", job.isActive)
    job.cancel()
  }
}
