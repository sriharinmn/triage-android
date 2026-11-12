package io.github.sriharinmn.triage.ui.welcome

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R

/** Test tags for the welcome screen. */
object WelcomeTags {
  const val GET_STARTED = "welcome_get_started"
}

/** The first launch's one screen: what Triage does, three things to know, and Get started, which leads to Connect. No tour. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(
    modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Wordmark()
    Text(stringResource(R.string.welcome_lede), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
      WelcomePoint(R.drawable.ic_notification, R.string.welcome_alerts_title, R.string.welcome_alerts_body)
      WelcomePoint(R.drawable.ic_swipe, R.string.welcome_swipe_title, R.string.welcome_swipe_body)
      WelcomePoint(R.drawable.ic_lock, R.string.welcome_private_title, R.string.welcome_private_body)
    }
    Button(
      onClick = onGetStarted,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(WelcomeTags.GET_STARTED),
    ) {
      Text(stringResource(R.string.welcome_get_started))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(R.string.welcome_token_note), style = MaterialTheme.typography.bodySmall, color = muted)
      Text(stringResource(R.string.connect_disclaimer), style = MaterialTheme.typography.bodySmall, color = muted)
    }
  }
}

/** "triage." with the dot in the brand color: a placeholder until the owner picks the app's final name. */
@Composable
private fun Wordmark() {
  val name = stringResource(R.string.welcome_wordmark)
  val dot = MaterialTheme.colorScheme.primary
  Text(
    buildAnnotatedString {
      append(name)
      withStyle(SpanStyle(color = dot)) { append(".") }
    },
    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
    modifier = Modifier.semantics { heading() },
  )
}

/** One point with its icon tile; TalkBack reads the title and the line under it as one item. */
@Composable
private fun WelcomePoint(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
  Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Box(
      Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(22.dp),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
      Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
