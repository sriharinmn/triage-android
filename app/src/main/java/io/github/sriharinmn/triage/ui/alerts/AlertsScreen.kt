package io.github.sriharinmn.triage.ui.alerts

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.CheckInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Test tags for the Alerts screen's controls. */
object AlertsTags {
  const val MASTER = "alerts_master"
  const val BATTERY_ROW = "alerts_battery"
  const val NOTIFICATIONS_ROW = "alerts_notifications_off"
  const val BATTERY_SETTINGS = "alerts_battery_settings"
  const val QUIET = "alerts_quiet"
  const val QUIET_FROM = "alerts_quiet_from"
  const val QUIET_TO = "alerts_quiet_to"

  fun interval(interval: CheckInterval) = "alerts_interval_${interval.name}"

  fun kind(kind: AlertKind) = "alerts_kind_${kind.name}"

  fun project(projectId: String) = "alerts_project_$projectId"
}

/** Which end of quiet hours a time picker is choosing. */
private enum class QuietEnd {
  From,
  To,
}

/** ⋮ → Alerts: the master switch, how often to check, which kinds and projects, quiet hours, and the battery guide. */
@Composable
fun AlertsScreen(viewModel: AlertsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val phone = rememberPhoneAlertStatus()
  val context = LocalContext.current
  // Alerts go on only once Android allows notifications; turning them off needs no permission.
  val requestNotifications = rememberNotificationPermissionRequest { allowed -> if (allowed) viewModel.onEnabledChanged(true) }
  val batteryTip = remember { batteryTipFor(Build.MANUFACTURER, Build.BRAND) }

  AlertsContent(
    state = state,
    phone = phone,
    batteryTip = batteryTip,
    onBack = onBack,
    onEnabledChanged = { on -> if (on) requestNotifications() else viewModel.onEnabledChanged(false) },
    onIntervalSelected = viewModel::onIntervalSelected,
    onKindChanged = viewModel::onKindChanged,
    onProjectChanged = viewModel::onProjectChanged,
    onQuietHoursChanged = viewModel::onQuietHoursChanged,
    onQuietStartSelected = viewModel::onQuietStartSelected,
    onQuietEndSelected = viewModel::onQuietEndSelected,
    onOpenBatterySettings = context::openBatterySettings,
    onOpenNotificationSettings = context::openNotificationSettings,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertsContent(
  state: AlertsUiState,
  phone: PhoneAlertStatus,
  batteryTip: BatteryTip,
  onBack: () -> Unit,
  onEnabledChanged: (Boolean) -> Unit,
  onIntervalSelected: (CheckInterval) -> Unit,
  onKindChanged: (AlertKind, Boolean) -> Unit,
  onProjectChanged: (projectId: String, on: Boolean) -> Unit,
  onQuietHoursChanged: (Boolean) -> Unit,
  onQuietStartSelected: (minuteOfDay: Int) -> Unit,
  onQuietEndSelected: (minuteOfDay: Int) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val settings = state.settings
  val quietHours = settings.quietHours
  var showingBatteryHelp by rememberSaveable { mutableStateOf(false) }
  var pickingTime by rememberSaveable { mutableStateOf<QuietEnd?>(null) }
  val masterLabel = stringResource(R.string.alerts_master)

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.alerts_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.detail_back))
          }
        },
        actions = {
          Switch(
            checked = settings.enabled,
            onCheckedChange = onEnabledChanged,
            modifier = Modifier.padding(end = 16.dp).testTag(AlertsTags.MASTER).semantics { contentDescription = masterLabel },
          )
        },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
      Text(
        lastCheckedText(context, state),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )
      if (phone.batteryRestricted) {
        WarningRow(stringResource(R.string.alerts_battery_row), AlertsTags.BATTERY_ROW) { showingBatteryHelp = true }
      }
      if (phone.notificationsBlocked) {
        WarningRow(stringResource(R.string.alerts_notifications_off_row), AlertsTags.NOTIFICATIONS_ROW, onOpenNotificationSettings)
      }

      SectionHeader(R.string.alerts_check_every)
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        CheckInterval.entries.forEachIndexed { index, interval ->
          SegmentedButton(
            selected = settings.interval == interval,
            onClick = { onIntervalSelected(interval) },
            shape = SegmentedButtonDefaults.itemShape(index, CheckInterval.entries.size),
            modifier = Modifier.testTag(AlertsTags.interval(interval)),
          ) {
            Text(stringResource(interval.label))
          }
        }
      }

      SectionHeader(R.string.alerts_kinds)
      AlertKind.entries.forEach { kind ->
        SwitchRow(stringResource(kind.switchLabel), kind in settings.kinds, AlertsTags.kind(kind)) { onKindChanged(kind, it) }
      }

      SectionHeader(R.string.alerts_projects)
      if (state.projects.isEmpty()) {
        Text(
          stringResource(R.string.alerts_projects_empty),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      state.projects.forEach { project ->
        SwitchRow(project.slug, project.id !in settings.projectsOff, AlertsTags.project(project.id)) {
          onProjectChanged(project.id, it)
        }
      }

      HorizontalDivider(Modifier.padding(top = 16.dp))
      SwitchRow(
        stringResource(R.string.alerts_quiet_hours),
        quietHours.enabled,
        AlertsTags.QUIET,
        supporting = stringResource(R.string.alerts_quiet_hours_hint),
        onCheckedChange = onQuietHoursChanged,
      )
      if (quietHours.enabled) {
        TimeRow(stringResource(R.string.alerts_quiet_from, formatMinuteOfDay(context, quietHours.startMinute)), AlertsTags.QUIET_FROM) {
          pickingTime = QuietEnd.From
        }
        TimeRow(stringResource(R.string.alerts_quiet_to, formatMinuteOfDay(context, quietHours.endMinute)), AlertsTags.QUIET_TO) {
          pickingTime = QuietEnd.To
        }
      }
    }
  }

  if (showingBatteryHelp) {
    BatteryHelpSheet(
      tip = batteryTip,
      onOpenSettings = {
        showingBatteryHelp = false
        onOpenBatterySettings()
      },
      onDismiss = { showingBatteryHelp = false },
    )
  }
  pickingTime?.let { end ->
    QuietTimePicker(
      title = stringResource(if (end == QuietEnd.From) R.string.alerts_quiet_from_title else R.string.alerts_quiet_to_title),
      initialMinute = if (end == QuietEnd.From) quietHours.startMinute else quietHours.endMinute,
      onConfirm = { minute ->
        pickingTime = null
        if (end == QuietEnd.From) onQuietStartSelected(minute) else onQuietEndSelected(minute)
      },
      onDismiss = { pickingTime = null },
    )
  }
}

/** The whole row toggles, not just the switch, so it's one big target and one TalkBack stop. */
@Composable
private fun SwitchRow(
  headline: String,
  checked: Boolean,
  tag: String,
  supporting: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  ListItem(
    headlineContent = { Text(headline) },
    supportingContent = supporting?.let { { Text(it) } },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    modifier = Modifier.testTag(tag).toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
  )
}

@Composable
private fun WarningRow(text: String, tag: String, onClick: () -> Unit) {
  ListItem(
    headlineContent = { Text(text) },
    leadingContent = {
      Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
    },
    modifier = Modifier.testTag(tag).clickable(onClick = onClick),
  )
}

@Composable
private fun TimeRow(text: String, tag: String, onClick: () -> Unit) {
  ListItem(headlineContent = { Text(text) }, modifier = Modifier.testTag(tag).clickable(onClick = onClick))
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
  Text(
    stringResource(text),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
  )
}

/** A clock dial only (no keyboard mode): minimal typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimePicker(title: String, initialMinute: Int, onConfirm: (minuteOfDay: Int) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val pickerState =
    rememberTimePickerState(
      initialHour = initialMinute / 60,
      initialMinute = initialMinute % 60,
      is24Hour = DateFormat.is24HourFormat(context),
    )
  TimePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) { Text(stringResource(R.string.alerts_time_ok)) }
    },
    title = { Text(title) },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.alerts_time_cancel)) } },
  ) {
    TimePicker(state = pickerState)
  }
}

/** "Last checked 10:42" (or a date if it wasn't today), with "· last attempt failed" when the latest attempt failed. */
@Composable
private fun lastCheckedText(context: Context, state: AlertsUiState): String {
  val checked =
    state.lastSuccessAt?.let { stringResource(R.string.alerts_last_checked, formatCheckTime(context, it)) }
      ?: stringResource(R.string.alerts_not_checked_yet)
  return if (state.lastAttemptFailed) stringResource(R.string.alerts_last_attempt_failed, checked) else checked
}

/** The time if it was today, else the date; following the phone's 12/24-hour setting and time zone. */
private fun formatCheckTime(context: Context, at: Instant): String {
  val date = Date(at.toEpochMilli())
  val today = at.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
  return if (today) DateFormat.getTimeFormat(context).format(date) else DateFormat.getMediumDateFormat(context).format(date)
}

/** "22:00" or "10:00 PM", following the phone's 12/24-hour setting. */
private fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
  val today = LocalDate.now().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(ZoneId.systemDefault())
  return DateFormat.getTimeFormat(context).format(Date(today.toInstant().toEpochMilli()))
}

@get:StringRes
private val CheckInterval.label: Int
  get() =
    when (this) {
      CheckInterval.Minutes15 -> R.string.alerts_interval_15
      CheckInterval.Minutes30 -> R.string.alerts_interval_30
      CheckInterval.Hour1 -> R.string.alerts_interval_60
    }

@get:StringRes
private val AlertKind.switchLabel: Int
  get() =
    when (this) {
      AlertKind.New -> R.string.alerts_kind_new
      AlertKind.Regressed -> R.string.alerts_kind_regressed
      AlertKind.Escalating -> R.string.alerts_kind_escalating
    }
