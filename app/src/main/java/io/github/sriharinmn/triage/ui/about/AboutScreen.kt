package io.github.sriharinmn.triage.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.ui.common.SectionLabel
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test tags for the About screen. */
object AboutTags {
  const val LIST = "about_list"
  const val LICENSE_TEXT = "about_license_text"

  fun library(name: String) = "about_library_$name"
}

private val PRIVACY_POINTS =
  listOf(
    R.string.about_privacy_no_server,
    R.string.about_privacy_no_tracking,
    R.string.about_privacy_token,
    R.string.about_privacy_data,
  )

/** Settings → About: the version, the not-affiliated disclaimer, a privacy summary, and the open-source licenses. */
@Composable
fun AboutScreen(viewModel: AboutViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  AboutContent(viewModel.state, onBack, modifier)
}

/**
 * [licenseReadContext] is where a license's text is read: off the main thread by default. Tests pass
 * [kotlin.coroutines.EmptyCoroutineContext] to keep the read on the main thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutContent(
  state: AboutUiState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  licenseReadContext: CoroutineContext = Dispatchers.IO,
) {
  var shownLicense by rememberSaveable { mutableStateOf<OpenSourceLicense?>(null) }
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.about_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.detail_back))
          }
        },
      )
    },
  ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag(AboutTags.LIST)) {
      item(key = "header") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            stringResource(R.string.welcome_wordmark) + ".",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
          )
          Text(versionLabel(state), style = MaterialTheme.typography.bodyMedium, color = muted)
          Text(
            stringResource(R.string.connect_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
      item(key = "privacy_label") { SectionLabel(stringResource(R.string.about_privacy)) }
      items(PRIVACY_POINTS) { point -> PrivacyPoint(point) }
      item(key = "licenses_label") { SectionLabel(stringResource(R.string.about_licenses)) }
      item(key = "licenses_intro") {
        Text(
          stringResource(R.string.about_licenses_intro),
          style = MaterialTheme.typography.bodyMedium,
          color = muted,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      items(state.libraries, key = { it.name }) { library ->
        ListItem(
          headlineContent = { Text(library.name) },
          supportingContent = { Text("${library.author} · ${library.license.title}") },
          modifier =
            Modifier.testTag(AboutTags.library(library.name)).clickable(role = Role.Button) { shownLicense = library.license },
        )
      }
    }
  }
  shownLicense?.let { LicenseDialog(it, licenseReadContext, onDismiss = { shownLicense = null }) }
}

/** "Version 1.0 (1)", plus " · debug" on a build that isn't a release. */
@Composable
private fun versionLabel(state: AboutUiState): String {
  val type = state.buildType
  return if (type == null) stringResource(R.string.about_version, state.versionName, state.versionCode)
  else stringResource(R.string.about_version_with_type, state.versionName, state.versionCode, type)
}

@Composable
private fun PrivacyPoint(@StringRes text: Int) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
  }
}

/** The license's full text, read from res/raw in [readContext] (off the main thread in the app). */
@Composable
private fun LicenseDialog(license: OpenSourceLicense, readContext: CoroutineContext, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val text by
    produceState<String?>(initialValue = null, license, readContext) {
      value = withContext(readContext) { context.resources.openRawResource(license.textRes).bufferedReader().use { it.readText() } }
    }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(license.title) },
    text = {
      Text(
        text.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.verticalScroll(rememberScrollState()).testTag(AboutTags.LICENSE_TEXT),
      )
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } },
  )
}
