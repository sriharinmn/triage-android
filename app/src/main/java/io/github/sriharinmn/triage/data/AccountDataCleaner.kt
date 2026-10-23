package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore

/**
 * Forgets what the app keeps for an account besides the account itself: cached issues and projects, the inbox filters,
 * and any rate-limit hold. Runs on disconnect and again before every connect, because an account can also disappear
 * without a disconnect (a lost Keystore key, a device transfer that copies the files but not the key).
 */
class AccountDataCleaner(private val issues: IssueRepository, private val settings: InboxSettingsStore) {
  /** Throws if anything can't be cleared, so callers can stop before the next step. */
  suspend fun forgetAccountData() {
    issues.clear()
    settings.clear()
  }
}
