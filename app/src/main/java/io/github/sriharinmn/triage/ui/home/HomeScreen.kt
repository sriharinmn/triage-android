package io.github.sriharinmn.triage.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.account.Account

@Composable
fun HomeScreen(account: Account, onDisconnect: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.home_connected_to, account.orgName), style = MaterialTheme.typography.headlineSmall)
    Text(account.apiBaseUrl, style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.home_coming_soon), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.home_disconnect)) }
  }
}
