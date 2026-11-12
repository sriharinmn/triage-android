package io.github.sriharinmn.triage.data.appearance

import io.github.sriharinmn.triage.data.account.AccountState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Once an account is connected, the welcome is behind the user for good: someone who updates to a version with it while
 * connected never sees it, not even after a Disconnect. Returns once that is saved, or once saving failed (the next launch
 * tries again).
 */
suspend fun markWelcomeSeenOnceConnected(accounts: Flow<AccountState>, store: AppearanceStore) {
  accounts.first { it is AccountState.Ready && it.account != null }
  try {
    if (!store.settings.first().welcomeSeen) store.markWelcomeSeen()
  } catch (_: IOException) {
    // Only a convenience: without it, a later Disconnect shows the welcome once more.
  }
}
