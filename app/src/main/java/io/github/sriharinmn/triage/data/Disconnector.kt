package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore

/**
 * Forgets everything tied to the connected account. The cached issues and settings go first: if clearing them fails, the
 * account stays connected so the user can retry, and the next account can never see the previous one's issues.
 */
class Disconnector(
  private val accounts: AccountRepository,
  private val issues: IssueRepository,
  private val settings: InboxSettingsStore,
) {
  suspend fun disconnect() {
    issues.clear()
    settings.clear()
    accounts.disconnect()
  }
}
