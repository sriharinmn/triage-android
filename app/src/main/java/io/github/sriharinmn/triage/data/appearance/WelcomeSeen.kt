package io.github.sriharinmn.triage.data.appearance

import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.demo.isDemo
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Once a real account is connected, the welcome is behind the user for good: someone who updates to a version with it
 * while connected never sees it, not even after a Disconnect. The demo doesn't count: leaving it shows the welcome again,
 * with both of its choices. Returns once that is saved, or once saving failed (the next launch tries again).
 */
suspend fun markWelcomeSeenOnceConnected(accounts: Flow<AccountState>, store: AppearanceStore) {
  accounts.first { state -> (state as? AccountState.Ready)?.account?.let { !it.isDemo } == true }
  try {
    if (!store.settings.first().welcomeSeen) store.markWelcomeSeen()
  } catch (_: IOException) {
    // Only a convenience: without it, a later Disconnect shows the welcome once more.
  }
}
