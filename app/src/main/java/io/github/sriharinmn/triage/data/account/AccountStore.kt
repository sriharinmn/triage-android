package io.github.sriharinmn.triage.data.account

import kotlinx.coroutines.flow.Flow

interface AccountStore {
  /** The stored account, or null when disconnected. */
  val account: Flow<Account?>

  suspend fun save(account: Account)

  suspend fun clear()
}
