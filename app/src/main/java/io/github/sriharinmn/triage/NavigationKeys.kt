package io.github.sriharinmn.triage

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Keys are saved into the activity's state: they must never hold a token or an Account.
@Serializable data object Inbox : NavKey
