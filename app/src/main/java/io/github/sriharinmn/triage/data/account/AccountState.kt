package io.github.sriharinmn.triage.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AccountState {
  /** The stored account hasn't been read yet. */
  data object Loading : AccountState

  /** [account] is null when disconnected. */
  data class Ready(val account: Account?) : AccountState
}

/**
 * Shares the stored account app-wide: read and decrypted once, and never back to [AccountState.Loading] after the first
 * read, so recreating the activity doesn't flash an empty frame.
 */
fun Flow<Account?>.asAccountState(scope: CoroutineScope): StateFlow<AccountState> =
  map<Account?, AccountState> { AccountState.Ready(it) }.stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)
