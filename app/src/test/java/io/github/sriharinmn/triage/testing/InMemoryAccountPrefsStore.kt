package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.settings.AccountPrefsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryAccountPrefsStore(initial: String? = null) : AccountPrefsStore {
  private val state = MutableStateFlow(initial)
  override val myUserId: Flow<String?> = state
  val current: String?
    get() = state.value

  override suspend fun saveMyUserId(userId: String?) {
    state.value = userId
  }

  override suspend fun clear() {
    state.value = null
  }
}
