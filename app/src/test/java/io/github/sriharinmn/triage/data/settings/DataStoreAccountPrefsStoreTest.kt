package io.github.sriharinmn.triage.data.settings

import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreAccountPrefsStoreTest {
  private val store = DataStoreAccountPrefsStore(InMemoryPreferencesDataStore())

  @Test
  fun nothingSaved_meansUnknown() = runTest {
    assertNull(store.myUserId.first())
  }

  @Test
  fun myUserId_roundTrips_andClears() = runTest {
    store.saveMyUserId("41")
    assertEquals("41", store.myUserId.first())

    store.saveMyUserId(null)
    assertNull(store.myUserId.first())

    store.saveMyUserId("42")
    store.clear()
    assertNull(store.myUserId.first())
  }
}
