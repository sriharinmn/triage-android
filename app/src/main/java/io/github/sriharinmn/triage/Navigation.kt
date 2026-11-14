package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.ui.about.AboutScreen
import io.github.sriharinmn.triage.ui.about.AboutViewModel
import io.github.sriharinmn.triage.ui.alerts.AlertsScreen
import io.github.sriharinmn.triage.ui.alerts.AlertsViewModel
import io.github.sriharinmn.triage.ui.alerts.DeepLink
import io.github.sriharinmn.triage.ui.detail.IssueDetailScreen
import io.github.sriharinmn.triage.ui.detail.IssueDetailViewModel
import io.github.sriharinmn.triage.ui.inbox.InboxOpening
import io.github.sriharinmn.triage.ui.inbox.InboxScreen
import io.github.sriharinmn.triage.ui.inbox.InboxViewModel
import io.github.sriharinmn.triage.ui.settings.SettingsScreen
import io.github.sriharinmn.triage.ui.settings.SettingsViewModel

/** Screens available once an account is connected, opened where a tapped notification's [deepLink] points. */
@Composable
fun TriageNavigation(account: Account, container: AppContainer, deepLink: DeepLink? = null) {
  // Worked out once: TriageApp clears the link after this composition, but the start must not change with it.
  val start = remember { startFor(deepLink, account.orgSlug) }
  TriageNavDisplay(
    initialStack = start.stack,
    inbox = { onOpenIssue, onOpenSettings ->
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
            opening = start.inboxOpening,
            markWelcomeSeen = container::markWelcomeSeen,
          )
        }
      InboxScreen(inboxViewModel, onOpenIssue = onOpenIssue, onOpenSettings = onOpenSettings)
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
    settings = { onBack, onOpenAlerts, onOpenAbout ->
      val settingsViewModel: SettingsViewModel =
        viewModel(key = "settings|${account.apiBaseUrl}|${account.orgSlug}") {
          // Starts from the appearance already read at app start, so the Theme buttons never show the defaults first.
          SettingsViewModel(
            account,
            container.appearance,
            container.disconnector,
            initialAppearance = container.appearanceSettings.value ?: AppearanceSettings(),
          )
        }
      SettingsScreen(settingsViewModel, onBack = onBack, onOpenAlerts = onOpenAlerts, onOpenAbout = onOpenAbout)
    },
    alerts = { onBack ->
      val alertsViewModel: AlertsViewModel =
        viewModel(key = "alerts|${account.apiBaseUrl}|${account.orgSlug}") {
          AlertsViewModel(container.alertSettings, container.alertState, container.issueRepository.projects)
        }
      AlertsScreen(alertsViewModel, onBack = onBack)
    },
    about = { onBack ->
      val aboutViewModel: AboutViewModel =
        viewModel(key = "about") { AboutViewModel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE) }
      AboutScreen(aboutViewModel, onBack = onBack)
    },
  )
}

/** Where a fresh signed-in start opens: the back stack, and how the inbox opens (a summary's tab and project). */
internal data class Start(val stack: List<NavKey> = listOf(Inbox), val inboxOpening: InboxOpening? = null)

/**
 * A link from a notification applies only to the org it was about; after connecting to another org it is ignored, as
 * is no link at all: both start on the plain inbox.
 */
internal fun startFor(link: DeepLink?, orgSlug: String): Start =
  when (val valid = link?.takeIf { it.orgSlug == orgSlug }) {
    is DeepLink.OpenIssue -> Start(stack = listOf(Inbox, IssueDetail(valid.issueId)))
    is DeepLink.OpenInbox -> Start(inboxOpening = InboxOpening(valid.tab, valid.projectId))
    null -> Start()
  }

/**
 * The signed-in back stack: the inbox, with an issue's detail or Settings on top, and Alerts or About on top of Settings.
 * [initialStack] is where a fresh start opens (a notification's issue opens on top of the inbox). The screens come in as
 * lambdas so tests can drive the real back stack with stand-ins.
 */
@Composable
internal fun TriageNavDisplay(
  inbox: @Composable (onOpenIssue: (issueId: String) -> Unit, onOpenSettings: () -> Unit) -> Unit,
  issueDetail: @Composable (issueId: String, onBack: () -> Unit) -> Unit,
  settings: @Composable (onBack: () -> Unit, onOpenAlerts: () -> Unit, onOpenAbout: () -> Unit) -> Unit,
  alerts: @Composable (onBack: () -> Unit) -> Unit,
  about: @Composable (onBack: () -> Unit) -> Unit,
  initialStack: List<NavKey> = listOf(Inbox),
) {
  val backStack = rememberNavBackStack(*initialStack.toTypedArray())

  // A second tap while a screen is opening must not stack two copies, so a screen opens only from the one it belongs on.
  fun open(from: NavKey, to: NavKey) {
    if (backStack.lastOrNull() == from) backStack.add(to)
  }

  // A second back (a keyboard Enter or a TalkBack double-tap) during the exit animation must not pop the screen below
  // too: an empty back stack crashes NavDisplay.
  fun close(key: NavKey) {
    if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Each entry gets its own saved state and ViewModels; screens get the account from here, never from a NavKey.
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>()),
    entryProvider =
      entryProvider {
        entry<Inbox> { inbox({ issueId -> open(Inbox, IssueDetail(issueId)) }, { open(Inbox, Settings) }) }
        entry<IssueDetail> { key -> issueDetail(key.issueId) { close(key) } }
        entry<Settings> { settings({ close(Settings) }, { open(Settings, Alerts) }, { open(Settings, About) }) }
        entry<Alerts> { alerts { close(Alerts) } }
        entry<About> { about { close(About) } }
      },
  )
}
