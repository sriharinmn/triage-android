package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import io.github.sriharinmn.triage.data.settings.AccountPrefsStore

/** "Try with sample data": swaps whatever account was there for a fresh demo. */
class DemoStarter(
  private val accounts: AccountRepository,
  private val demo: DemoSentryApi,
  private val prefs: AccountPrefsStore,
) {
  /**
   * Forgets any previous account's data, writes a fresh demo started now, makes Sam "me" (so Assign never asks "Which one
   * is you?"), then stores the demo account. Throws, storing no account, if a step fails.
   */
  suspend fun start() {
    accounts.useSampleData {
      demo.startFresh()
      prefs.saveMyUserId(DemoWorld.me.userId)
    }
  }
}
