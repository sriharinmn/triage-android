package io.github.sriharinmn.triage.ui.connect

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.account.ConnectError
import io.github.sriharinmn.triage.remote.OrganizationDto
import kotlinx.coroutines.launch

object ConnectTags {
  const val SERVER_URL = "connect_server_url"
  const val TOKEN = "connect_token"
  const val CONNECT = "connect_button"
  const val OPEN_TOKEN_PAGE = "connect_open_token_page"
  const val CANCEL = "connect_cancel"
}

@Composable
fun ConnectScreen(viewModel: ConnectViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val uriHandler = LocalUriHandler.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  BackHandler(enabled = state.step == Step.ChooseOrganization) { viewModel.onBackFromOrganizations() }

  ConnectContent(
    state = state,
    onHostChoiceChange = viewModel::onHostChoiceChange,
    onSelfHostedUrlChange = viewModel::onSelfHostedUrlChange,
    onTokenChange = viewModel::onTokenChange,
    onOpenTokenPage = {
      viewModel.requestTokenPageUrl()?.let { url ->
        try {
          uriHandler.openUri(url)
        } catch (e: IllegalArgumentException) {
          // No app can open the URL: AndroidUriHandler wraps ActivityNotFoundException in this. Nothing to recover.
        }
      }
    },
    onPasteToken = {
      scope.launch {
        val clip = clipboard.getClipEntry()?.clipData
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        if (text != null) viewModel.onTokenChange(text.trim())
      }
    },
    onSubmit = viewModel::onSubmit,
    onCancelValidation = viewModel::onCancelValidation,
    onOrganizationChosen = viewModel::onOrganizationChosen,
    onBackFromOrganizations = viewModel::onBackFromOrganizations,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectContent(
  state: ConnectUiState,
  onHostChoiceChange: (HostChoice) -> Unit,
  onSelfHostedUrlChange: (String) -> Unit,
  onTokenChange: (String) -> Unit,
  onOpenTokenPage: () -> Unit,
  onPasteToken: () -> Unit,
  onSubmit: () -> Unit,
  onCancelValidation: () -> Unit,
  onOrganizationChosen: (OrganizationDto) -> Unit,
  onBackFromOrganizations: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (state.step == Step.ChooseOrganization) {
      Text(stringResource(R.string.connect_choose_org_title), style = MaterialTheme.typography.headlineMedium)
      state.organizations.forEach { organization ->
        ListItem(
          headlineContent = { Text(organization.name) },
          supportingContent = { Text(organization.slug) },
          modifier = Modifier.fillMaxWidth().clickable { onOrganizationChosen(organization) },
        )
      }
      TextButton(onClick = onBackFromOrganizations) { Text(stringResource(R.string.connect_back)) }
    } else {
      // The ViewModel ignores edits unless the step is EnterToken, so the controls are disabled to match.
      val inputsEnabled = state.step == Step.EnterToken

      Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium)
      Text(stringResource(R.string.connect_subtitle), style = MaterialTheme.typography.bodyMedium)

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        HostChoice.entries.forEachIndexed { index, choice ->
          SegmentedButton(
            selected = state.hostChoice == choice,
            onClick = { onHostChoiceChange(choice) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = HostChoice.entries.size),
            enabled = inputsEnabled,
          ) {
            Text(stringResource(choice.labelRes()))
          }
        }
      }

      if (state.hostChoice == HostChoice.SelfHosted) {
        OutlinedTextField(
          value = state.selfHostedUrl,
          onValueChange = onSelfHostedUrlChange,
          enabled = inputsEnabled,
          label = { Text(stringResource(R.string.connect_server_url)) },
          placeholder = { Text("https://sentry.example.com") },
          isError = state.urlError,
          supportingText = { if (state.urlError) Text(stringResource(R.string.connect_server_url_error)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          modifier = Modifier.fillMaxWidth().testTag(ConnectTags.SERVER_URL),
        )
      }

      Text(stringResource(R.string.connect_step1_title), style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.connect_step1_body), style = MaterialTheme.typography.bodyMedium)
      OutlinedButton(
        onClick = onOpenTokenPage,
        enabled = inputsEnabled,
        modifier = Modifier.testTag(ConnectTags.OPEN_TOKEN_PAGE),
      ) {
        Text(stringResource(R.string.connect_open_token_page))
      }

      Text(stringResource(R.string.connect_step2_title), style = MaterialTheme.typography.titleMedium)
      OutlinedTextField(
        value = state.token,
        onValueChange = onTokenChange,
        enabled = inputsEnabled,
        label = { Text(stringResource(R.string.connect_token_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
        isError = state.error != null,
        supportingText = {
          state.error?.let {
            Text(it.message(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
          }
        },
        trailingIcon = {
          TextButton(onClick = onPasteToken, enabled = inputsEnabled) { Text(stringResource(R.string.connect_paste)) }
        },
        modifier = Modifier.fillMaxWidth().testTag(ConnectTags.TOKEN),
      )

      Button(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth().testTag(ConnectTags.CONNECT),
      ) {
        if (state.step == Step.Validating) {
          val validatingDescription = stringResource(R.string.connect_validating)
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp).semantics { contentDescription = validatingDescription },
            strokeWidth = 2.dp,
          )
        } else {
          Text(stringResource(R.string.connect_button))
        }
      }

      if (state.canCancel) {
        TextButton(onClick = onCancelValidation, modifier = Modifier.fillMaxWidth().testTag(ConnectTags.CANCEL)) {
          Text(stringResource(R.string.connect_cancel))
        }
      }

      Text(stringResource(R.string.connect_disclaimer), style = MaterialTheme.typography.bodySmall)
    }
  }
}

@StringRes
private fun HostChoice.labelRes(): Int =
  when (this) {
    HostChoice.Cloud -> R.string.connect_host_cloud
    HostChoice.SelfHosted -> R.string.connect_host_self_hosted
  }

@Composable
private fun ConnectError.message(): String =
  when (this) {
    ConnectError.EmptyToken -> stringResource(R.string.connect_error_empty_token)
    ConnectError.OrgTokenNotSupported -> stringResource(R.string.connect_error_org_token)
    ConnectError.InvalidToken -> stringResource(R.string.connect_error_invalid_token)
    ConnectError.MissingScopes -> stringResource(R.string.connect_error_missing_scopes)
    ConnectError.NoOrganizations -> stringResource(R.string.connect_error_no_orgs)
    is ConnectError.RateLimited ->
      retryAfterSeconds?.takeIf { it > 0 }?.let { stringResource(R.string.connect_error_rate_limited_seconds, it) }
        ?: stringResource(R.string.connect_error_rate_limited)
    ConnectError.Network -> stringResource(R.string.connect_error_network)
    ConnectError.Unknown -> stringResource(R.string.connect_error_unknown)
    ConnectError.SaveFailed -> stringResource(R.string.connect_error_save_failed)
  }
