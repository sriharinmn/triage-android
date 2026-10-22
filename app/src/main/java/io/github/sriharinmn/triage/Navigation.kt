package io.github.sriharinmn.triage

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.ui.home.HomeScreen

/** Screens available once an account is connected. */
@Composable
fun TriageNavigation(account: Account, onDisconnect: () -> Unit) {
  val backStack = rememberNavBackStack(Home)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider { entry<Home> { HomeScreen(account = account, onDisconnect = onDisconnect) } },
  )
}
