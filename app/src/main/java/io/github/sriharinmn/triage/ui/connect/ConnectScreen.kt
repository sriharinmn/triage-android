package io.github.sriharinmn.triage.ui.connect

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import io.github.sriharinmn.triage.theme.mono
import kotlinx.coroutines.launch

object ConnectTags {
  const val SERVER_URL = "connect_server_url"
  const val TOKEN = "connect_token"
  const val PASTE = "connect_paste"
  const val CONNECT = "connect_button"
  const val OPEN_TOKEN_PAGE = "connect_open_token_page"
  const val TOKEN_PAGE_URL = "connect_token_page_url"
  const val COPY_TOKEN_PAGE_URL = "connect_copy_token_page_url"
  const val FORM_ERROR = "connect_form_error"
  const val CANCEL = "connect_cancel"
}

/** The permissions the token needs, as the checklist under step 1. */
private val TOKEN_SCOPES =
  listOf(R.string.connect_scope_org, R.string.connect_scope_project, R.string.connect_scope_issue, R.string.connect_scope_member)

@Composable
fun ConnectScreen(viewModel: ConnectViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val uriHandler = LocalUriHandler.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  BackHandler(enabled = state.step == Step.ChooseOrganization) { viewModel.onBackFromOrganizations() }
  // Back while a token is being checked cancels the check instead of leaving the app.
  BackHandler(enabled = state.canCancel) { viewModel.onCancelValidation() }

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
          // No app can open it (AndroidUriHandler wraps ActivityNotFoundException in this): show the address to copy.
          viewModel.onTokenPageUnavailable(url)
        }
      }
    },
    onCopyTokenPageUrl = { url -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(url, url))) } },
    onPasteToken = {
      scope.launch {
        val clip = clipboard.getClipEntry()?.clipData
        viewModel.onPasteToken(if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null)
      }
    },
    onSubmit = viewModel::onSubmit,
    onCancelValidation = viewModel::onCancelValidation,
    onOrganizationChosen = viewModel::onOrganizationChosen,
    onBackFromOrganizations = viewModel::onBackFromOrganizations,
    modifier = modifier,
  )
}

@Composable
internal fun ConnectContent(
  state: ConnectUiState,
  onHostChoiceChange: (HostChoice) -> Unit,
  onSelfHostedUrlChange: (String) -> Unit,
  onTokenChange: (String) -> Unit,
  onOpenTokenPage: () -> Unit,
  onCopyTokenPageUrl: (String) -> Unit,
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
    if (state.step == Step.ChooseOrganization || state.step == Step.Saving) {
      OrganizationList(state, onOrganizationChosen, onBackFromOrganizations)
    } else {
      ConnectForm(
        state,
        onHostChoiceChange,
        onSelfHostedUrlChange,
        onTokenChange,
        onOpenTokenPage,
        onCopyTokenPageUrl,
        onPasteToken,
        onSubmit,
        onCancelValidation,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ConnectForm(
  state: ConnectUiState,
  onHostChoiceChange: (HostChoice) -> Unit,
  onSelfHostedUrlChange: (String) -> Unit,
  onTokenChange: (String) -> Unit,
  onOpenTokenPage: () -> Unit,
  onCopyTokenPageUrl: (String) -> Unit,
  onPasteToken: () -> Unit,
  onSubmit: () -> Unit,
  onCancelValidation: () -> Unit,
) {
  // The ViewModel ignores edits unless the step is EnterToken, so the controls are disabled to match.
  val inputsEnabled = state.step == Step.EnterToken
  val muted = MaterialTheme.colorScheme.onSurfaceVariant

  Text(
    stringResource(R.string.connect_title),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() },
  )
  Text(stringResource(R.string.connect_subtitle), style = MaterialTheme.typography.bodyMedium, color = muted)

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
    val serverError = state.serverUrlError
    OutlinedTextField(
      value = state.selfHostedUrl,
      onValueChange = onSelfHostedUrlChange,
      enabled = inputsEnabled,
      label = { Text(stringResource(R.string.connect_server_url)) },
      placeholder = { Text("https://sentry.example.com") },
      isError = state.urlError || serverError != null,
      supportingText = {
        when {
          state.urlError -> Text(stringResource(R.string.connect_server_url_error))
          serverError != null ->
            Text(serverError.message(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      modifier = Modifier.fillMaxWidth().testTag(ConnectTags.SERVER_URL),
    )
  }

  StepHeader(1, R.string.connect_step1_title)
  Text(stringResource(R.string.connect_step1_body), style = MaterialTheme.typography.bodyMedium)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { TOKEN_SCOPES.forEach { ScopeRow(it) } }
  OutlinedButton(onClick = onOpenTokenPage, enabled = inputsEnabled, modifier = Modifier.testTag(ConnectTags.OPEN_TOKEN_PAGE)) {
    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.connect_open_token_page))
  }
  state.tokenPageUrlToCopy?.let { url -> TokenPageAddress(url, onCopy = { onCopyTokenPageUrl(url) }) }

  StepHeader(2, R.string.connect_step2_title)
  val tokenError = state.tokenError
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
    // Only a problem with the token itself marks this field; anything else shows under the button.
    isError = tokenError != null,
    supportingText = {
      when {
        tokenError != null ->
          Text(tokenError.message(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        state.pasteEmpty ->
          Text(stringResource(R.string.connect_paste_empty), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      }
    },
    trailingIcon = {
      TextButton(onClick = onPasteToken, enabled = inputsEnabled, modifier = Modifier.testTag(ConnectTags.PASTE)) {
        Text(stringResource(R.string.connect_paste))
      }
    },
    modifier = Modifier.fillMaxWidth().testTag(ConnectTags.TOKEN),
  )

  Button(
    onClick = onSubmit,
    enabled = state.canSubmit,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ConnectTags.CONNECT),
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
  state.formError?.let {
    Text(
      it.message(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.testTag(ConnectTags.FORM_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
    )
  }
  if (state.canCancel) {
    TextButton(onClick = onCancelValidation, modifier = Modifier.fillMaxWidth().testTag(ConnectTags.CANCEL)) {
      Text(stringResource(R.string.connect_cancel))
    }
  }

  Text(stringResource(R.string.connect_disclaimer), style = MaterialTheme.typography.bodySmall, color = muted)
}

/** The organizations to choose from; while the chosen one saves, the list stays with a progress bar and can't be tapped. */
@Composable
private fun ColumnScope.OrganizationList(
  state: ConnectUiState,
  onOrganizationChosen: (OrganizationDto) -> Unit,
  onBack: () -> Unit,
) {
  val saving = state.step == Step.Saving
  Text(
    stringResource(R.string.connect_choose_org_title),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() },
  )
  if (saving) {
    val connecting = stringResource(R.string.connect_connecting)
    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = connecting })
  }
  state.organizations.forEach { organization ->
    ListItem(
      headlineContent = { Text(organization.name) },
      supportingContent = { Text(organization.slug, style = MaterialTheme.typography.bodyMedium.mono()) },
      modifier = Modifier.fillMaxWidth().clickable(enabled = !saving, role = Role.Button) { onOrganizationChosen(organization) },
    )
  }
  TextButton(onClick = onBack, enabled = !saving) { Text(stringResource(R.string.connect_back)) }
}

/** "① Create a personal token": the number in a small brand circle, the step as a heading. */
@Composable
private fun StepHeader(number: Int, @StringRes title: Int) {
  Row(
    modifier = Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) { heading() },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun ScopeRow(@StringRes label: Int) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(
      painterResource(R.drawable.ic_check),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
  }
}

/** The token page's address when no browser could open it: selectable, with a button that copies it. */
@Composable
private fun TokenPageAddress(url: String, onCopy: () -> Unit) {
  Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  ) {
    Text(stringResource(R.string.connect_open_failed), style = MaterialTheme.typography.bodyMedium)
    SelectionContainer {
      Text(url, style = MaterialTheme.typography.bodySmall.mono(), modifier = Modifier.testTag(ConnectTags.TOKEN_PAGE_URL))
    }
    TextButton(onClick = onCopy, modifier = Modifier.testTag(ConnectTags.COPY_TOKEN_PAGE_URL)) {
      Text(stringResource(R.string.connect_copy_address))
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
      retryAfterSeconds?.takeIf { it > 0 }?.let { seconds ->
        pluralStringResource(
          R.plurals.connect_error_rate_limited_seconds,
          seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
          seconds,
        )
      } ?: stringResource(R.string.connect_error_rate_limited)
    ConnectError.Network -> stringResource(R.string.connect_error_network)
    ConnectError.InsecureConnection -> stringResource(R.string.connect_error_insecure)
    ConnectError.ServerNotFound -> stringResource(R.string.connect_error_server_not_found)
    ConnectError.NotSentryServer -> stringResource(R.string.connect_error_not_sentry)
    ConnectError.Unknown -> stringResource(R.string.connect_error_unknown)
    ConnectError.SaveFailed -> stringResource(R.string.connect_error_save_failed)
  }
