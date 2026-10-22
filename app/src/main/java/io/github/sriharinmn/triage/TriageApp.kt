package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.ui.connect.ConnectScreen
import io.github.sriharinmn.triage.ui.connect.ConnectViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface AccountState {
  data object Loading : AccountState

  data class Ready(val account: Account?) : AccountState
}

/** Shows Connect until an account is stored, then the main navigation. */
@Composable
fun TriageApp(container: AppContainer) {
  val accountState by
    remember(container) { container.accountRepository.account.map<Account?, AccountState> { AccountState.Ready(it) } }
      .collectAsStateWithLifecycle(initialValue = AccountState.Loading)
  val scope = rememberCoroutineScope()

  when (val state = accountState) {
    AccountState.Loading -> Unit
    is AccountState.Ready -> {
      val account = state.account
      if (account == null) {
        val connectViewModel: ConnectViewModel = viewModel { ConnectViewModel(container.accountRepository) }
        ConnectScreen(connectViewModel)
      } else {
        TriageNavigation(account = account, onDisconnect = { scope.launch { container.accountRepository.disconnect() } })
      }
    }
  }
}
