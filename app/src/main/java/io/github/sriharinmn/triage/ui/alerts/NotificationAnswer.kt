package io.github.sriharinmn.triage.ui.alerts

/** What Android said about notifications once Triage asked (or found it couldn't ask). */
enum class NotificationAnswer {
  Allowed,
  /** No for now; Android may show its popup again next time. */
  Declined,
  /** Android won't show its popup (a final no, or notifications switched off in its settings): only its settings help. */
  Blocked,
}

/** [allowed]: notifications can show now. [canAskAgain]: Android would still show its popup after this answer. */
fun notificationAnswer(allowed: Boolean, canAskAgain: Boolean): NotificationAnswer =
  when {
    allowed -> NotificationAnswer.Allowed
    canAskAgain -> NotificationAnswer.Declined
    else -> NotificationAnswer.Blocked
  }
