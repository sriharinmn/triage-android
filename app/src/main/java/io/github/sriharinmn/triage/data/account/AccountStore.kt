package io.github.sriharinmn.triage.data.account

import kotlinx.coroutines.flow.Flow

/** What one read of the saved connection found. */
sealed interface StoredAccount {
  data class Connected(val account: Account) : StoredAccount

  /** Nothing is saved: never connected, or disconnected. */
  data object Missing : StoredAccount

  /**
   * Something is saved but can't be used: the file can't be read, the token won't decrypt (the Keystore key was wiped),
   * or a field is missing.
   */
  data object Unreadable : StoredAccount
}

interface AccountStore {
  /** The stored account, or null when disconnected or unreadable. */
  val account: Flow<Account?>

  /** One read that tells a missing connection from an unreadable one, for actions that must say why they failed. */
  suspend fun read(): StoredAccount

  suspend fun save(account: Account)

  suspend fun clear()
}
