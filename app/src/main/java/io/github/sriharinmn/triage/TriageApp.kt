package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.ui.connect.ConnectScreen
import io.github.sriharinmn.triage.ui.connect.ConnectViewModel
import kotlinx.coroutines.launch

/** Shows Connect until an account is stored, then the main navigation. */
@Composable
fun TriageApp(container: AppContainer) {
  val accountState by container.accountState.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()

  when (val state = accountState) {
    AccountState.Loading -> Unit
    is AccountState.Ready -> {
      val account = state.account
      if (account == null) {
        val connectViewModel: ConnectViewModel = viewModel { ConnectViewModel(container.accountRepository) }
        ConnectScreen(connectViewModel)
      } else {
        TriageNavigation(account = account, onDisconnect = { scope.launch { container.disconnector.disconnect() } })
      }
    }
  }
}
