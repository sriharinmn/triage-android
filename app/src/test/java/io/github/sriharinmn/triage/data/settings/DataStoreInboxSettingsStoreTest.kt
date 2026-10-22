package io.github.sriharinmn.triage.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueSort
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric supplies a real `Build.VERSION.SDK_INT`. Without one, DataStore falls back to `File.renameTo`, which
 * cannot replace an existing file on Windows, so the second write fails there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // SDK 36 needs Java 21; the unit-test JVM here is Java 17
class DataStoreInboxSettingsStoreTest {
  @get:Rule val folder = TemporaryFolder()

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private val dataStore by lazy {
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "inbox.preferences_pb") })
  }
  private val store by lazy { DataStoreInboxSettingsStore(dataStore) }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun nothingSaved_readsTheDefaults() = runBlocking {
    assertEquals(InboxFilter(), store.filter.first())
  }

  @Test
  fun save_roundTrips() = runBlocking {
    val filter = InboxFilter(InboxTab.Escalating, projectId = "102", environment = "production", sort = IssueSort.Users)

    store.save(filter)

    assertEquals(filter, store.filter.first())
  }

  @Test
  fun choosingAllAgain_forgetsTheProjectAndEnvironment() = runBlocking {
    store.save(InboxFilter(projectId = "102", environment = "production"))

    store.save(InboxFilter())

    assertEquals(InboxFilter(), store.filter.first())
  }

  @Test
  fun unknownNames_fromAnOlderOrNewerApp_fallBackToDefaults() = runBlocking {
    dataStore.edit {
      it[stringPreferencesKey("tab")] = "Archived"
      it[stringPreferencesKey("sort")] = "Trends"
    }

    assertEquals(InboxFilter(), store.filter.first())
  }

  @Test
  fun clear_restoresTheDefaults() = runBlocking {
    store.save(InboxFilter(InboxTab.Regressed, projectId = "1"))

    store.clear()

    assertEquals(InboxFilter(), store.filter.first())
  }
}
