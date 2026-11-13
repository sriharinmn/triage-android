package io.github.sriharinmn.triage.ui.alerts

/** What Android said about notifications once Triage asked (or found it couldn't ask). */
enum class NotificationAnswer {
  Allowed,
  /** A no the user just gave in Android's popup, or one Android may ask about again; respected, nothing opens. */
  Declined,
  /** Android showed no popup and won't (a final earlier no, or notifications switched off in its settings): only its settings help. */
  Blocked,
}

/**
 * [allowed]: notifications can show now. [askedJustNow]: Android's popup was certainly shown a moment ago (it would have
 * shown a rationale before the request), so a no is the user's answer to it and is respected. [canAskAgain]: Android
 * would still show its popup after this answer. Blocked is only the case where no popup could have been shown.
 */
fun notificationAnswer(allowed: Boolean, askedJustNow: Boolean, canAskAgain: Boolean): NotificationAnswer =
  when {
    allowed -> NotificationAnswer.Allowed
    canAskAgain -> NotificationAnswer.Declined
    askedJustNow -> NotificationAnswer.Declined
    else -> NotificationAnswer.Blocked
  }
