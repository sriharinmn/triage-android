package io.github.sriharinmn.triage.ui.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.appearance.ThemeMode
import io.github.sriharinmn.triage.theme.mono
import io.github.sriharinmn.triage.ui.common.DisconnectDialog
import io.github.sriharinmn.triage.ui.common.OrgInitialTile
import io.github.sriharinmn.triage.ui.common.SectionLabel

/** Test tags for the Settings screen. */
object SettingsTags {
  const val DISCONNECT = "settings_disconnect"
  const val LEAVE_DEMO = "settings_leave_demo"
  const val ALERTS = "settings_alerts"
  const val WALLPAPER_COLORS = "settings_wallpaper_colors"
  const val ABOUT = "settings_about"

  fun theme(mode: ThemeMode) = "settings_theme_${mode.name}"
}

/** The inbox's gear: the account and Disconnect (Leave demo for the sample data), Alerts, Appearance, and About. */
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onBack: () -> Unit,
  onOpenAlerts: () -> Unit,
  onOpenAbout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  SettingsContent(
    state = state,
    // Wallpaper colors exist from Android 12.
    wallpaperColorsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    onBack = onBack,
    onOpenAlerts = onOpenAlerts,
    onOpenAbout = onOpenAbout,
    onThemeModeSelected = viewModel::onThemeModeSelected,
    onWallpaperColorsChanged = viewModel::onWallpaperColorsChanged,
    onDisconnectConfirmed = viewModel::onDisconnectConfirmed,
    onDisconnectFailureShown = viewModel::onDisconnectFailureShown,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
  state: SettingsUiState,
  wallpaperColorsAvailable: Boolean,
  onBack: () -> Unit,
  onOpenAlerts: () -> Unit,
  onOpenAbout: () -> Unit,
  onThemeModeSelected: (ThemeMode) -> Unit,
  onWallpaperColorsChanged: (Boolean) -> Unit,
  onDisconnectConfirmed: () -> Unit,
  onDisconnectFailureShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var confirmingDisconnect by rememberSaveable { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val disconnectFailedText = stringResource(if (state.isDemo) R.string.demo_leave_failed else R.string.inbox_disconnect_failed)
  LaunchedEffect(state.disconnectFailed) {
    if (state.disconnectFailed) {
      snackbarHostState.showSnackbar(disconnectFailedText)
      onDisconnectFailureShown()
    }
  }
  val hairline = MaterialTheme.colorScheme.outlineVariant

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.detail_back))
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
      SectionLabel(stringResource(R.string.settings_account))
      ListItem(
        leadingContent = { OrgInitialTile(state.orgName) },
        headlineContent = { Text(state.orgName) },
        supportingContent = {
          if (state.isDemo) Text(stringResource(R.string.settings_demo_account))
          else Text(state.serverHost, style = MaterialTheme.typography.bodyMedium.mono())
        },
      )
      if (state.isDemo) {
        // Nothing of value is lost, so no confirmation: the sample data can be opened again from the welcome.
        ListItem(
          leadingContent = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
          headlineContent = { Text(stringResource(R.string.settings_leave_demo)) },
          supportingContent = { Text(stringResource(R.string.settings_leave_demo_hint)) },
          modifier =
            Modifier.testTag(SettingsTags.LEAVE_DEMO).clickable(enabled = !state.isDisconnecting, role = Role.Button) {
              onDisconnectConfirmed()
            },
        )
      } else {
        ListItem(
          leadingContent = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
          headlineContent = { Text(stringResource(R.string.settings_disconnect)) },
          supportingContent = { Text(stringResource(R.string.settings_disconnect_hint)) },
          modifier =
            Modifier.testTag(SettingsTags.DISCONNECT).clickable(enabled = !state.isDisconnecting, role = Role.Button) {
              confirmingDisconnect = true
            },
        )
      }
      HorizontalDivider(color = hairline)
      NavigationRow(R.drawable.ic_notifications, R.string.settings_alerts, R.string.settings_alerts_hint, SettingsTags.ALERTS, onOpenAlerts)
      HorizontalDivider(color = hairline)

      SectionLabel(stringResource(R.string.settings_appearance))
      Text(
        stringResource(R.string.settings_theme),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
          SegmentedButton(
            selected = state.appearance.themeMode == mode,
            onClick = { onThemeModeSelected(mode) },
            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            modifier = Modifier.testTag(SettingsTags.theme(mode)),
          ) {
            Text(stringResource(mode.labelRes()), maxLines = 1)
          }
        }
      }
      if (wallpaperColorsAvailable) {
        // The whole row toggles, not just the switch: one big target and one TalkBack stop.
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_wallpaper_colors)) },
          supportingContent = { Text(stringResource(R.string.settings_wallpaper_colors_hint)) },
          trailingContent = { Switch(checked = state.appearance.useWallpaperColors, onCheckedChange = null) },
          modifier =
            Modifier.testTag(SettingsTags.WALLPAPER_COLORS)
              .toggleable(value = state.appearance.useWallpaperColors, role = Role.Switch, onValueChange = onWallpaperColorsChanged),
        )
      }
      HorizontalDivider(color = hairline)
      NavigationRow(R.drawable.ic_info, R.string.settings_about, R.string.settings_about_hint, SettingsTags.ABOUT, onOpenAbout)
    }
  }

  if (confirmingDisconnect) {
    DisconnectDialog(
      onConfirm = {
        confirmingDisconnect = false
        onDisconnectConfirmed()
      },
      onDismiss = { confirmingDisconnect = false },
    )
  }
}

/** A row that opens another screen, with a chevron. */
@Composable
private fun NavigationRow(@DrawableRes icon: Int, @StringRes title: Int, @StringRes hint: Int, tag: String, onClick: () -> Unit) {
  ListItem(
    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(hint)) },
    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
    modifier = Modifier.testTag(tag).clickable(role = Role.Button, onClick = onClick),
  )
}

@StringRes
private fun ThemeMode.labelRes(): Int =
  when (this) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
  }
