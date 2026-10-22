package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.ui.inbox.InboxScreen
import io.github.sriharinmn.triage.ui.inbox.InboxViewModel

/** Screens available once an account is connected. */
@Composable
fun TriageNavigation(account: Account, container: AppContainer) {
  val backStack = rememberNavBackStack(Inbox)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Each entry gets its own saved state and ViewModels; screens get the account from here, never from a NavKey.
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>()),
    entryProvider =
      entryProvider {
        entry<Inbox> {
          // Keyed by server and org, so a different account never reuses this ViewModel.
          val inboxViewModel: InboxViewModel =
            viewModel(key = "inbox|${account.apiBaseUrl}|${account.orgSlug}") {
              InboxViewModel(account, container.issueRepository, container.inboxSettings, container.disconnector)
            }
          InboxScreen(inboxViewModel)
        }
      },
  )
}
