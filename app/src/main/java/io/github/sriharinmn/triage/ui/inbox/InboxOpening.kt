package io.github.sriharinmn.triage.ui.inbox

import io.github.sriharinmn.triage.data.issues.InboxTab

/**
 * How a notification asks the inbox to open: on [tab] and on project [projectId] (null means All projects), with All
 * environments. It applies to that opening only; the remembered filter stays as the user left it.
 */
data class InboxOpening(val tab: InboxTab, val projectId: String?)
