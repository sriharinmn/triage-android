package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Calls [onTurnedOn] each time alerts go from off to on while the demo is the account, so a new issue arrives soon after.
 * What holds when this starts is only a starting point: an app that opens with alerts already on doesn't kick again.
 */
suspend fun kickDemoWhenAlertsTurnOn(accounts: Flow<AccountState>, settings: Flow<AlertSettings>, onTurnedOn: suspend () -> Unit) {
  var previous: Boolean? = null
  combine(accounts.filterIsInstance<AccountState.Ready>(), settings) { account, alerts -> account.account?.isDemo == true && alerts.enabled }
    .distinctUntilChanged()
    .collect { on ->
      if (on && previous == false) onTurnedOn()
      previous = on
    }
}
