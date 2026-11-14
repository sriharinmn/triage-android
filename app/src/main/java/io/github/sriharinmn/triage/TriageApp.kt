package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.ui.alerts.DeepLink
import io.github.sriharinmn.triage.ui.connect.ConnectScreen
import io.github.sriharinmn.triage.ui.connect.ConnectViewModel
import io.github.sriharinmn.triage.ui.welcome.SampleDataButton
import io.github.sriharinmn.triage.ui.welcome.SampleDataViewModel
import io.github.sriharinmn.triage.ui.welcome.SignedOutScreens

/**
 * Without an account: the welcome on a first launch, then Connect; both offer the sample data. With one: the main
 * navigation, opened where a tapped notification points.
 */
@Composable
fun TriageApp(container: AppContainer, deepLink: DeepLink? = null) {
  val accountState by container.accountState.collectAsStateWithLifecycle()
  val appearance by container.appearanceSettings.collectAsStateWithLifecycle()
  // A notification's link is used once: after the first signed-in composition has taken it, a Disconnect and Connect in
  // the same activity starts on the plain inbox instead of replaying it.
  var pending by remember { mutableStateOf(deepLink) }
  AccountGate(
    state = accountState,
    signedOut = {
      // In the signed-out session, so it goes once the demo's account is stored.
      val sampleDataViewModel: SampleDataViewModel = viewModel { SampleDataViewModel(container.demoStarter::start) }
      val sampleStatus by sampleDataViewModel.status.collectAsStateWithLifecycle()
      val sampleData = SampleDataButton(sampleStatus, sampleDataViewModel::onTrySampleData)
      SignedOutScreens(appearance, onGetStarted = container::markWelcomeSeen, sampleData = sampleData) {
        val connectViewModel: ConnectViewModel = viewModel { ConnectViewModel(container.accountRepository) }
        ConnectScreen(connectViewModel, sampleData = sampleData)
      }
    },
    signedIn = { account ->
      TriageNavigation(account = account, container = container, deepLink = pending)
      SideEffect { pending = null }
    },
  )
}

/**
 * The root for an account state: nothing while loading, [signedOut] without an account, [signedIn] with one. Each gets a
 * ViewModel store of its own (see [SessionViewModelStores]), cleared as soon as a settled state no longer matches it:
 * - a signed-in account's ViewModels never outlive that account;
 * - the signed-out screens' ViewModels go once an account connects, so Connect's (with the token typed into it) never
 *   comes back after a later Disconnect, and Connect can leave its progress showing until the inbox replaces it.
 */
@Composable
fun AccountGate(state: AccountState, signedOut: @Composable () -> Unit, signedIn: @Composable (Account) -> Unit) {
  // From the activity's store (before any override below), so the session stores survive rotation.
  val sessions: SessionViewModelStores = viewModel()
  when (state) {
    AccountState.Loading -> Unit
    is AccountState.Ready -> {
      val account = state.account
      val sessionKey = account?.sessionKey() ?: SIGNED_OUT_SESSION
      // Every settled state drops the other sessions' ViewModels; this also covers an account that disappeared while
      // the activity was being recreated, when the old composition's disposal could not tell it was gone for good.
      SideEffect { sessions.retainOnly(sessionKey) }
      key(sessionKey) {
        val owner = remember { SessionStoreOwner(sessions.storeFor(sessionKey)) }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
          if (account == null) signedOut() else signedIn(account)
        }
      }
    }
  }
}

/** The signed-out screens' session. Account sessions are "apiBaseUrl|orgSlug", so none can equal it. */
private const val SIGNED_OUT_SESSION = "signed-out"

private fun Account.sessionKey() = "$apiBaseUrl|$orgSlug"

/**
 * Hands a session's [viewModelStore] to the composition. Navigation 3 keeps each entry's ViewModels in the nearest
 * owner's store and does not clear them when NavDisplay leaves composition, so this is what scopes them to the session.
 * It provides no default factory or SavedStateHandle extras, so ViewModels under the gate must be created through a
 * Navigation entry or with an explicit initializer.
 */
private class SessionStoreOwner(override val viewModelStore: ViewModelStore) : ViewModelStoreOwner

/**
 * The ViewModelStores of the sessions (signed in or signed out), kept in the activity's store so they survive rotation.
 * [retainOnly] clears every session but the current one, so a ViewModel never outlives the account it was made for.
 */
internal class SessionViewModelStores : ViewModel() {
  private val stores = mutableMapOf<String, ViewModelStore>()

  fun storeFor(sessionKey: String): ViewModelStore = stores.getOrPut(sessionKey) { ViewModelStore() }

  /** Clears and forgets every session's ViewModels except [sessionKey]'s; all of them when it is null. */
  fun retainOnly(sessionKey: String?) {
    stores.keys.filter { it != sessionKey }.forEach { stores.remove(it)?.clear() }
  }

  override fun onCleared() = retainOnly(null)
}
