package io.github.sriharinmn.triage

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.data.appearance.isDark
import io.github.sriharinmn.triage.theme.TriageTheme
import io.github.sriharinmn.triage.ui.alerts.toDeepLink

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val container = (application as TriageApplication).container
    // A tapped notification opens its issue or inbox tab, only on a fresh start: after a rotation or process death the
    // saved back stack already shows where the user went from there. Notifications open a fresh task, so they always
    // arrive here, never in onNewIntent.
    val deepLink = if (savedInstanceState == null) intent.toDeepLink() else null
    setContent {
      val appearance by container.appearanceSettings.collectAsStateWithLifecycle()
      val settings = appearance ?: AppearanceSettings()
      val dark = settings.themeMode.isDark(isSystemInDarkTheme())
      // The status and navigation bar icons follow Triage's own theme, which can differ from the phone's.
      DisposableEffect(dark) {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
          navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
      }
      TriageTheme(themeMode = settings.themeMode, useWallpaperColors = settings.useWallpaperColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          TriageApp(container, deepLink)
        }
      }
    }
    holdFirstFrameUntilAppearanceIsRead(container)
  }

  override fun onStop() {
    super.onStop()
    // Leaving the app ends every Undo window: send what's pending while the process is still sure to be alive. A
    // rotation also stops the activity, but the user is still here, so the window keeps running.
    if (!isChangingConfigurations) (application as TriageApplication).container.pendingActions.flushNow()
  }

  /**
   * Keeps the first frame (the splash on Android 12+, the window background before that) until the `appearance` file has
   * been read, so the app never flashes the wrong theme. The read starts with the app and takes milliseconds; after
   * [FIRST_FRAME_WAIT_MS] the app draws anyway, with the defaults.
   */
  private fun holdFirstFrameUntilAppearanceIsRead(container: AppContainer) {
    val content = findViewById<View>(android.R.id.content)
    val startedAt = SystemClock.uptimeMillis()
    content.viewTreeObserver.addOnPreDrawListener(
      object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          val ready =
            container.appearanceSettings.value != null || SystemClock.uptimeMillis() - startedAt > FIRST_FRAME_WAIT_MS
          if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
          return ready
        }
      }
    )
  }

  private companion object {
    const val FIRST_FRAME_WAIT_MS = 1_000L

    // enableEdgeToEdge's own default scrims, for phones with a three-button navigation bar.
    val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
    val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
  }
}
