package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.toInstantOrNull
import io.github.sriharinmn.triage.remote.EventDto
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Messages, values and code lines longer than this are cut for display; one Sentry string can be megabytes. */
internal const val MAX_TEXT_LENGTH = 2_000

internal fun String.clip(): String = if (length <= MAX_TEXT_LENGTH) this else take(MAX_TEXT_LENGTH) + "…"

/**
 * The screen's view of a Sentry event. Never throws on odd data: unknown entry types are ignored, an entry or item that
 * can't be read is dropped on its own, and every field may be missing.
 */
fun EventDto.toIssueEvent(): IssueEvent {
  val entries = entries.mapNotNull { it as? JsonObject }
  val eventTags = tags.asArray().mapNotNull { it.asObject()?.toEventTag() }
  val exceptions = entries.firstMapped("exception") { it.toExceptions() }.orEmpty()
  return IssueEvent(
    id = id,
    dateCreated = dateCreated.toInstantOrNull(),
    release = release.toReleaseName(),
    environment = eventTags.firstOrNull { it.key == "environment" }?.value,
    stacks = exceptions.ifEmpty { listOfNotNull(entries.firstMapped("threads") { it.toThreadStack() }) },
    message = (entries.firstMapped("message") { it.toMessage() } ?: message)?.takeIf { it.isNotBlank() }?.clip(),
    breadcrumbs = entries.firstMapped("breadcrumbs") { it.toBreadcrumbs() }.orEmpty(),
    tags = eventTags,
    context = toContext(request = entries.firstMapped("request") { it.toRequest() }),
  )
}

/** The data of the first entry of [type] that [map] can read; entries it can't read are skipped. */
private fun <T> List<JsonObject>.firstMapped(type: String, map: (JsonObject) -> T?): T? =
  asSequence()
    .filter { it.string("type") == type }
    .mapNotNull { entry -> entry["data"].asObject()?.let { data -> readSafely { map(data) } } }
    .firstOrNull()

/**
 * Runs [read], treating any exception as "unreadable". The accessors below are already null-safe; this is the backstop
 * that keeps one strange entry from hiding the rest of the event.
 */
private inline fun <T> readSafely(read: () -> T?): T? =
  try {
    read()
  } catch (_: RuntimeException) {
    null
  }

private fun JsonObject.toExceptions(): List<EventStack> =
  this["values"]
    .asArray()
    .mapNotNull { value -> value.asObject()?.let { readSafely { it.toExceptionStack() } } }
    // Sentry lists the cause first; the screen shows the exception that was actually thrown first.
    .reversed()

private fun JsonObject.toExceptionStack() =
  EventStack(type = string("type"), value = string("value")?.clip(), threadName = null, frames = this["stacktrace"].toFrames())

/**
 * The crashed thread's stack, else the current thread's, else the only thread's that has frames; threads without
 * frames don't count. Only the chosen thread's frames are mapped, since a thread dump can list hundreds of threads.
 */
private fun JsonObject.toThreadStack(): EventStack? {
  val threads = this["values"].asArray().mapNotNull { it.asObject() }.filter { it.hasFrames() }
  val thread =
    threads.firstOrNull { it.bool("crashed") == true }
      ?: threads.firstOrNull { it.bool("current") == true }
      ?: threads.singleOrNull()
      ?: return null
  return EventStack(type = null, value = null, threadName = thread.string("name"), frames = thread["stacktrace"].toFrames())
}

/** Whether [toFrames] would find a frame: it maps every frame object, so one object is enough. */
private fun JsonObject.hasFrames(): Boolean = this["stacktrace"].asObject()?.get("frames").asArray().any { it is JsonObject }

private fun JsonElement?.toFrames(): List<StackFrame> =
  asObject()
    ?.get("frames")
    .asArray()
    .mapNotNull { frame -> frame.asObject()?.let { readSafely { it.toFrame() } } }
    // Sentry lists the oldest call first; the crash site belongs at the top.
    .reversed()

private fun JsonObject.toFrame() =
  StackFrame(
    function = string("function"),
    module = string("module"),
    file = string("filename") ?: string("absPath"),
    lineNo = int("lineNo"),
    colNo = int("colNo"),
    inApp = bool("inApp") ?: false,
    context = this["context"].asArray().mapNotNull { it.toContextLine() },
  )

/** One `[lineNo, text]` pair; a null text is an empty line. */
private fun JsonElement.toContextLine(): ContextLine? {
  val pair = (this as? JsonArray)?.takeIf { it.size == 2 } ?: return null
  val lineNo = (pair[0] as? JsonPrimitive)?.intOrNull ?: return null
  return ContextLine(lineNo, (pair[1] as? JsonPrimitive)?.contentOrNull.orEmpty().clip())
}

private fun JsonObject.toMessage(): String? = string("formatted")?.takeIf { it.isNotBlank() } ?: string("message")

private fun JsonObject.toBreadcrumbs(): List<Breadcrumb> =
  this["values"]
    .asArray()
    .mapNotNull { crumb -> crumb.asObject()?.let { readSafely { it.toBreadcrumb() } } }
    // Sentry lists the oldest first; the screen starts with what happened last.
    .reversed()

private fun JsonObject.toBreadcrumb(): Breadcrumb {
  val data = this["data"].asObject()
  val text =
    when {
      string("type") == "http" -> data?.httpText()
      string("category") == "navigation" -> data?.navigationText()
      else -> null
    } ?: string("message")
  return Breadcrumb(
    timestamp = this["timestamp"].toTimestamp(),
    category = string("category"),
    level = IssueLevel.fromApi(string("level")),
    text = text?.takeIf { it.isNotBlank() }?.clip(),
  )
}

/** "GET https://… → 200", with whichever parts exist. */
private fun JsonObject.httpText(): String? {
  val request = listOfNotNull(string("method"), string("url")).joinToString(" ")
  if (request.isEmpty()) return null
  return string("status_code")?.let { "$request → $it" } ?: request
}

/** "/products → /cart" */
private fun JsonObject.navigationText(): String? {
  val from = string("from")
  val to = string("to")
  if (from == null && to == null) return null
  return "${from ?: "?"} → ${to ?: "?"}"
}

/** Sentry's API sends ISO-8601 strings; raw SDK payloads use epoch seconds. */
private fun JsonElement?.toTimestamp(): Instant? {
  val primitive = this as? JsonPrimitive ?: return null
  if (primitive.isString) return primitive.content.toInstantOrNull()
  return primitive.doubleOrNull?.let { Instant.ofEpochMilli((it * 1000).toLong()) }
}

private fun JsonObject.toRequest(): EventRequest? = string("url")?.let { EventRequest(method = string("method"), url = it.clip()) }

private fun JsonObject.toEventTag(): EventTag? {
  val key = string("key") ?: return null
  val value = string("value") ?: return null
  return EventTag(key, value.clip())
}

/** `{version, shortVersion}` from Sentry's API, or a plain string from some servers. */
private fun JsonElement?.toReleaseName(): String? =
  when (this) {
    is JsonObject -> string("shortVersion") ?: string("version")
    is JsonPrimitive -> contentOrNull
    else -> null
  }

private fun EventDto.toContext(request: EventRequest?): EventContext {
  val all = contexts.asObject()
  return EventContext(
    device = all?.get("device").asObject()?.deviceName(),
    os = all?.get("os").asObject()?.nameAndVersion(),
    runtime = all?.get("runtime").asObject()?.nameAndVersion(),
    browser = all?.get("browser").asObject()?.nameAndVersion(),
    app = all?.get("app").asObject()?.appVersion(),
    user = user.asObject()?.toUser(),
    request = request,
  )
}

/** "Google Pixel 8": the maker (unless the model already starts with it) and the model; the family if there's no model. */
private fun JsonObject.deviceName(): String? {
  val model = string("model") ?: return string("family")
  val maker = string("manufacturer") ?: string("brand") ?: return model
  return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
}

private fun JsonObject.nameAndVersion(): String? =
  listOfNotNull(string("name"), string("version")).joinToString(" ").ifEmpty { null }

/** "2.3.1 (231)", or whichever part exists. */
private fun JsonObject.appVersion(): String? {
  val version = string("app_version")
  val build = string("app_build")
  return if (version != null && build != null) "$version ($build)" else version ?: build
}

private fun JsonObject.toUser(): EventUser? {
  val user = EventUser(id = string("id"), email = string("email"), username = string("username"), ipAddress = string("ip_address"))
  return user.takeIf { listOf(it.id, it.email, it.username, it.ipAddress).any { value -> value != null } }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

/** A string, number or boolean as text; null for JSON null, objects and arrays. */
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
