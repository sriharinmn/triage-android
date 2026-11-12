package io.github.sriharinmn.triage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
      TriageTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          TriageApp(container, deepLink)
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    // Leaving the app ends every Undo window: send what's pending while the process is still sure to be alive. A
    // rotation also stops the activity, but the user is still here, so the window keeps running.
    if (!isChangingConfigurations) (application as TriageApplication).container.pendingActions.flushNow()
  }
}
