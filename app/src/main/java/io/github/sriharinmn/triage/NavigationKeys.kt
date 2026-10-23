package io.github.sriharinmn.triage

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The inbox. Keys are saved into the activity's state, so they must never hold a token or an Account. */
@Serializable data object Inbox : NavKey

/** One issue's detail, on top of the inbox. Only the id goes in: keys are saved into the activity's state. */
@Serializable data class IssueDetail(val issueId: String) : NavKey
