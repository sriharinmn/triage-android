package io.github.sriharinmn.triage.data.alerts

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import kotlinx.coroutines.flow.first
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
}
