package io.github.sriharinmn.triage.ui.alerts

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** What Android allows Triage right now: whether its notifications can show, and whether its battery use is limited. */
data class PhoneAlertStatus(val notificationsBlocked: Boolean = false, val batteryRestricted: Boolean = false)

/**
 * Battery-restricted means Android optimizes Triage's battery use (the default for every app, which holds checks back
 * while the phone sleeps) or the user restricted it further. Either way alerts can arrive late.
 */
fun Context.phoneAlertStatus(): PhoneAlertStatus {
  val optimized = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == false
  val restricted =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
      getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
  return PhoneAlertStatus(
    notificationsBlocked = !NotificationManagerCompat.from(this).areNotificationsEnabled(),
    batteryRestricted = optimized || restricted,
  )
}

/** [phoneAlertStatus], read again whenever the screen comes back: the user may have just changed a setting. */
@Composable
fun rememberPhoneAlertStatus(): PhoneAlertStatus {
  val context = LocalContext.current
  var status by remember { mutableStateOf(context.phoneAlertStatus()) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = context.phoneAlertStatus() }
  return status
}

/**
 * Asks Android for permission to post notifications (Android 13+), then calls [onResult] with whether notifications can
 * show. Older Android has no popup, so the answer is whether they're switched on.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (allowed: Boolean) -> Unit): () -> Unit {
  val context = LocalContext.current
  val currentOnResult by rememberUpdatedState(onResult)
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> currentOnResult(granted) }
  return {
    val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val canAsk =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    if (!allowed && canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else currentOnResult(allowed)
  }
}

/** Android's battery-optimization list, where Triage can be exempted; Triage's app info if a phone lacks the list. */
fun Context.openBatterySettings() {
  try {
    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
  } catch (_: ActivityNotFoundException) {
    openAppInfo()
  }
}

/** Android's notification settings for Triage; its app info if a phone lacks that page. */
fun Context.openNotificationSettings() {
  try {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
  } catch (_: ActivityNotFoundException) {
    openAppInfo()
  }
}

private fun Context.openAppInfo() {
  startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
