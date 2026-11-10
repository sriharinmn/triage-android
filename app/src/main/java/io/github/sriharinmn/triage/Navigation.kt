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
import io.github.sriharinmn.triage.ui.detail.IssueDetailScreen
import io.github.sriharinmn.triage.ui.detail.IssueDetailViewModel
import io.github.sriharinmn.triage.ui.inbox.InboxScreen
import io.github.sriharinmn.triage.ui.inbox.InboxViewModel

/** Screens available once an account is connected. */
@Composable
fun TriageNavigation(account: Account, container: AppContainer) {
  TriageNavDisplay(
    inbox = { onOpenIssue ->
      // AccountGate gives these screens a per-account ViewModel store, so this ViewModel is dropped on disconnect;
      // the key also names the account.
      val inboxViewModel: InboxViewModel =
        viewModel(key = "inbox|${account.apiBaseUrl}|${account.orgSlug}") {
          InboxViewModel(account, container.issueRepository, container.inboxSettings, container.disconnector)
        }
      InboxScreen(inboxViewModel, onOpenIssue = onOpenIssue)
    },
    issueDetail = { issueId, onBack ->
      // Each detail entry has its own store (the nav-entry decorator), so leaving the screen drops its ViewModel and the
      // event data it holds.
      val detailViewModel: IssueDetailViewModel =
        viewModel(key = "issue|${account.apiBaseUrl}|${account.orgSlug}|$issueId") {
          IssueDetailViewModel(
            account,
            issueId,
            container.issueDetailRepository,
            container.disconnector,
            container.pendingActions,
            container.members,
          )
        }
      IssueDetailScreen(detailViewModel, onBack = onBack)
    },
  )
}

/**
 * The signed-in back stack: the inbox, with an issue's detail pushed on top. The screens come in as lambdas so tests
 * can drive the real back stack with stand-ins.
 */
@Composable
internal fun TriageNavDisplay(
  inbox: @Composable (onOpenIssue: (issueId: String) -> Unit) -> Unit,
  issueDetail: @Composable (issueId: String, onBack: () -> Unit) -> Unit,
) {
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
          inbox { issueId ->
            // A second tap while the detail is opening must not stack two copies.
            if (backStack.lastOrNull() == Inbox) backStack.add(IssueDetail(issueId))
          }
        }
        entry<IssueDetail> { key ->
          issueDetail(key.issueId) {
            // A second back (a keyboard Enter or a TalkBack double-tap) during the exit animation must not pop the inbox
            // too: an empty back stack crashes NavDisplay.
            if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
          }
        }
      },
  )
}
