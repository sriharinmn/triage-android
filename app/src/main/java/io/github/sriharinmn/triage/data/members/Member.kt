package io.github.sriharinmn.triage.data.members

/** Someone in the organization who can be assigned issues; [userId] is what Sentry's `assignedTo` takes. */
data class Member(val userId: String, val name: String, val email: String?)
