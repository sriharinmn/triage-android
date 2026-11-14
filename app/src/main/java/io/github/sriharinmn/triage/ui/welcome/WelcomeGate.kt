package io.github.sriharinmn.triage.ui.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings

/** What shows without an account. */
enum class SignedOutScreen {
  Welcome,
  Connect,
}

/**
 * The welcome on a first launch; Connect once Get started was tapped, as saved in [appearance] or tapped in this visit
 * ([getStartedTapped], in case saving failed). Null until the appearance file has been read, so the first frame is never
 * the wrong screen.
 */
fun signedOutScreen(appearance: AppearanceSettings?, getStartedTapped: Boolean): SignedOutScreen? =
  when {
    getStartedTapped -> SignedOutScreen.Connect
    appearance == null -> null
    appearance.welcomeSeen -> SignedOutScreen.Connect
    else -> SignedOutScreen.Welcome
  }

/**
 * The signed-out slot: the welcome, then [connect] once Get started is tapped (now, or on an earlier launch).
 * [onGetStarted] saves that it was tapped; the screen moves on at once even if saving fails.
 */
@Composable
fun SignedOutScreens(appearance: AppearanceSettings?, onGetStarted: () -> Unit, connect: @Composable () -> Unit) {
  var getStartedTapped by rememberSaveable { mutableStateOf(false) }
  when (signedOutScreen(appearance, getStartedTapped)) {
    SignedOutScreen.Welcome ->
      WelcomeScreen(
        onGetStarted = {
          getStartedTapped = true
          onGetStarted()
        }
      )
    SignedOutScreen.Connect -> connect()
    // The appearance file hasn't been read yet; MainActivity usually holds the first frame until it has.
    null -> Unit
  }
}
