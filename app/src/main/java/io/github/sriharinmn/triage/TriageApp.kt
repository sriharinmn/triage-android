package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.ui.connect.ConnectScreen
import io.github.sriharinmn.triage.ui.connect.ConnectViewModel

/** Shows Connect until an account is stored, then the main navigation. */
@Composable
fun TriageApp(container: AppContainer) {
  val accountState by container.accountState.collectAsStateWithLifecycle()
  AccountGate(
    state = accountState,
    signedOut = {
      val connectViewModel: ConnectViewModel = viewModel { ConnectViewModel(container.accountRepository) }
      ConnectScreen(connectViewModel)
    },
    signedIn = { account -> TriageNavigation(account = account, container = container) },
  )
}

/**
 * The root for an account state: nothing while loading, [signedOut] without an account, [signedIn] with one. A different
 * account gets a fresh composition, so no screen state carries over from the previous one.
 */
@Composable
fun AccountGate(state: AccountState, signedOut: @Composable () -> Unit, signedIn: @Composable (Account) -> Unit) {
  when (state) {
    AccountState.Loading -> Unit
    is AccountState.Ready -> {
      val account = state.account
      if (account == null) signedOut() else key(account.apiBaseUrl, account.orgSlug) { signedIn(account) }
    }
  }
}
