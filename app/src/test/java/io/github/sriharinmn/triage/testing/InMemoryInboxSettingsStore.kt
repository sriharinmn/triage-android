package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryInboxSettingsStore(initial: InboxFilter = InboxFilter()) : InboxSettingsStore {
  private val state = MutableStateFlow(initial)
  override val filter: Flow<InboxFilter> = state
  val current: InboxFilter
    get() = state.value

  /** When set, [clear] throws it. */
  var clearError: Exception? = null

  override suspend fun save(filter: InboxFilter) {
    state.value = filter
  }

  override suspend fun clear() {
    clearError?.let { throw it }
    state.value = InboxFilter()
  }
}
