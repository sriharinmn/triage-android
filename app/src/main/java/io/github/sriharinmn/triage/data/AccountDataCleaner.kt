package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore

/** Something kept for the connected account that must be forgotten with it. */
fun interface AccountScoped {
  suspend fun forget()
}

/**
 * Forgets what the app keeps for an account besides the account itself:
 * - cached issues and projects
 * - the inbox filters
 * - any rate-limit hold
 * - the [others], such as unsent issue actions, loaded members and remembered choices
 *
 * It runs on disconnect and again before every connect, because an account can also disappear without a disconnect
 * (a lost Keystore key, or a device transfer that copies the files but not the key).
 */
class AccountDataCleaner(
  private val issues: IssueRepository,
  private val settings: InboxSettingsStore,
  /** Forgotten first, so no unsent action goes out with a token that is being forgotten. */
  private val others: List<AccountScoped> = emptyList(),
) {
  /** Throws if anything can't be cleared, so callers can stop before the next step. */
  suspend fun forgetAccountData() {
    others.forEach { it.forget() }
    issues.clear()
    settings.clear()
  }
}
