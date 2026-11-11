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
import io.github.sriharinmn.triage.ui.alerts.AlertsScreen
import io.github.sriharinmn.triage.ui.alerts.AlertsViewModel
import io.github.sriharinmn.triage.ui.alerts.DeepLink
import io.github.sriharinmn.triage.ui.detail.IssueDetailScreen
import io.github.sriharinmn.triage.ui.detail.IssueDetailViewModel
import io.github.sriharinmn.triage.ui.inbox.InboxScreen
import io.github.sriharinmn.triage.ui.inbox.InboxViewModel

/** Screens available once an account is connected, opened where a tapped notification's [deepLink] points. */
@Composable
fun TriageNavigation(account: Account, container: AppContainer, deepLink: DeepLink? = null) {
  // A link from a notification applies only to the org it was about; after connecting to another org it is ignored.
  val link = deepLink?.takeIf { it.orgSlug == account.orgSlug }
  TriageNavDisplay(
    initialStack = if (link is DeepLink.OpenIssue) listOf(Inbox, IssueDetail(link.issueId)) else listOf(Inbox),
    inbox = { onOpenIssue, onOpenAlerts ->
      // AccountGate gives these screens a per-account ViewModel store, so this ViewModel is dropped on disconnect;
      // the key also names the account.
      val inboxViewModel: InboxViewModel =
        viewModel(key = "inbox|${account.apiBaseUrl}|${account.orgSlug}") {
          InboxViewModel(
            account,
            container.issueRepository,
            container.inboxSettings,
            container.disconnector,
            container.pendingActions,
            container.alertSettings,
            container.alertState,
            openTab = (link as? DeepLink.OpenInbox)?.tab,
          )
        }
      InboxScreen(inboxViewModel, onOpenIssue = onOpenIssue, onOpenAlerts = onOpenAlerts)
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
    alerts = { onBack ->
      val alertsViewModel: AlertsViewModel =
        viewModel(key = "alerts|${account.apiBaseUrl}|${account.orgSlug}") {
          AlertsViewModel(container.alertSettings, container.alertState, container.issueRepository.projects)
        }
      AlertsScreen(alertsViewModel, onBack = onBack)
    },
  )
}

/**
 * The signed-in back stack: the inbox, with an issue's detail or the Alerts screen pushed on top. [initialStack] is where a
 * fresh start opens (a notification's issue opens on top of the inbox). The screens come in as lambdas so tests can drive
 * the real back stack with stand-ins.
 */
@Composable
internal fun TriageNavDisplay(
  inbox: @Composable (onOpenIssue: (issueId: String) -> Unit, onOpenAlerts: () -> Unit) -> Unit,
  issueDetail: @Composable (issueId: String, onBack: () -> Unit) -> Unit,
  alerts: @Composable (onBack: () -> Unit) -> Unit,
  initialStack: List<NavKey> = listOf(Inbox),
) {
  val backStack = rememberNavBackStack(*initialStack.toTypedArray())

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Each entry gets its own saved state and ViewModels; screens get the account from here, never from a NavKey.
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>()),
    entryProvider =
      entryProvider {
        entry<Inbox> {
          inbox(
            { issueId ->
              // A second tap while the detail is opening must not stack two copies.
              if (backStack.lastOrNull() == Inbox) backStack.add(IssueDetail(issueId))
            },
            { if (backStack.lastOrNull() == Inbox) backStack.add(Alerts) },
          )
        }
        entry<IssueDetail> { key ->
          issueDetail(key.issueId) {
            // A second back (a keyboard Enter or a TalkBack double-tap) during the exit animation must not pop the inbox
            // too: an empty back stack crashes NavDisplay.
            if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
          }
        }
        entry<Alerts> { alerts { if (backStack.lastOrNull() == Alerts) backStack.removeLastOrNull() } }
      },
  )
}
