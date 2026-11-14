package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.demo.DemoState
import io.github.sriharinmn.triage.data.demo.DemoStateStore

/** The demo's saved state, in memory. [saves] counts writes, so tests can check that reads don't write. */
class InMemoryDemoStateStore(var current: DemoState? = null) : DemoStateStore {
  var saves = 0
    private set

  override suspend fun load(): DemoState? = current

  override suspend fun save(state: DemoState) {
    current = state
    saves++
  }

  override suspend fun clear() {
    current = null
  }
}
