package io.github.sriharinmn.triage

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
 * account gets a fresh composition, and its [signedIn] screens get their own ViewModel store that is cleared when the
 * account goes away (see [SignedInSession]), so a ViewModel never outlives the account it was made for.
 */
@Composable
fun AccountGate(state: AccountState, signedOut: @Composable () -> Unit, signedIn: @Composable (Account) -> Unit) {
  when (state) {
    AccountState.Loading -> Unit
    is AccountState.Ready -> {
      val account = state.account
      if (account == null) signedOut()
      else key(account.apiBaseUrl, account.orgSlug) { SignedInSession(account) { signedIn(account) } }
    }
  }
}

/**
 * Gives [content] a ViewModel store that lasts as long as [account] stays connected.
 *
 * Navigation 3 keeps each entry's ViewModels in the activity's store and does not clear them when NavDisplay leaves
 * composition, so without this a disconnect followed by a reconnect to the same org would get the old ViewModel back,
 * still holding the old token. This store survives rotation (it lives in the activity's store) but is cleared when
 * [content] leaves composition for any reason other than a configuration change, i.e. on disconnect or when switching
 * accounts.
 */
@Composable
private fun SignedInSession(account: Account, content: @Composable () -> Unit) {
  val activityOwner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner in composition" }
  // Keyed per server and org, so clearing the old account's store can never wipe a newly composed one.
  val session: SessionViewModelStore =
    viewModel(viewModelStoreOwner = activityOwner, key = "session|${account.apiBaseUrl}|${account.orgSlug}") {
      SessionViewModelStore()
    }
  val activity = LocalActivity.current
  DisposableEffect(session) {
    onDispose {
      if (activity?.isChangingConfigurations != true) session.viewModelStore.clear()
    }
  }
  CompositionLocalProvider(LocalViewModelStoreOwner provides session) { content() }
}

/** A [ViewModelStoreOwner] kept in the activity's store, so the ViewModels placed in it survive rotation. */
internal class SessionViewModelStore : ViewModel(), ViewModelStoreOwner {
  override val viewModelStore = ViewModelStore()

  override fun onCleared() = viewModelStore.clear()
}
