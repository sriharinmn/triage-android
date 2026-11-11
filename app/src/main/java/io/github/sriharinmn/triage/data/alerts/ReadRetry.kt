package io.github.sriharinmn.triage.data.alerts

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/**
 * An unreadable or corrupt file (CorruptionException is an IOException) reads as empty, then the read is tried again.
 * Retrying instead of catching keeps the flow alive, so the app-wide collectors (the scheduler) follow the file again once
 * it can be read. Same backoff as the account store: one more second per failed read, up to 30 seconds.
 */
internal fun Flow<Preferences>.retryOnReadError(): Flow<Preferences> =
  retryWhen { cause, attempt ->
    if (cause !is IOException) return@retryWhen false
    emit(emptyPreferences())
    delay(READ_RETRY_DELAY_MS * (attempt + 1).coerceAtMost(MAX_RETRY_STEPS))
    true
  }

private const val READ_RETRY_DELAY_MS = 1_000L
private const val MAX_RETRY_STEPS = 30L
