package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.remote.EventDto
import io.github.sriharinmn.triage.remote.SentryJson
import io.github.sriharinmn.triage.testing.readFixture
import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMappingTest {
  private fun event(fixture: String) = SentryJson.decodeFromString<EventDto>(readFixture(fixture)).toIssueEvent()

  private fun eventFromJson(json: String) = SentryJson.decodeFromString<EventDto>(json).toIssueEvent()

  @Test
  fun jsException_framesAreNewestFirstWithInAppFlagsAndCode() {
    val stack = event("event_js.json").stacks.single()

    assertEquals("TypeError", stack.type)
    assertEquals("Cannot read properties of undefined (reading 'price')", stack.value)
    assertNull(stack.threadName)
    assertEquals(
      listOf("cartTotal", "handleCheckout", "Layer.handle [as handle_request]", "process.processTicksAndRejections"),
      stack.frames.map { it.function },
    )
    assertEquals(listOf(true, true, false, false), stack.frames.map { it.inApp })
    val top = stack.frames.first()
    assertEquals("shop/cart.js", top.file)
    assertEquals("shop:cart", top.module)
    assertEquals(42, top.lineNo)
    assertEquals(23, top.colNo)
    assertEquals(listOf(40, 41, 42, 43, 44), top.context.map { it.lineNo })
    assertEquals(ContextLine(42, "    total += item.price * item.quantity"), top.context[2])
  }

  @Test
  fun jsEvent_readsItsIdTimeReleaseEnvironmentAndTags() {
    val event = event("event_js.json")

    assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90", event.id)
    assertEquals(Instant.parse("2026-10-22T11:58:00.512Z"), event.dateCreated)
    assertEquals("1.4.2", event.release)
    assertEquals("production", event.environment)
    assertEquals(4, event.tags.size)
    assertEquals(EventTag("route", "POST /api/checkout"), event.tags[2])
    // The top-level message is empty for exceptions.
    assertNull(event.message)
  }

  @Test
  fun breadcrumbs_areNewestFirstWithHttpAndNavigationText() {
    val crumbs = event("event_js.json").breadcrumbs

    assertEquals(listOf("exception", "ui.click", "http", "navigation", "console"), crumbs.map { it.category })
    assertEquals(IssueLevel.Error, crumbs[0].level)
    assertEquals("button#checkout", crumbs[1].text)
    assertEquals(Instant.parse("2026-10-22T11:58:00.300Z"), crumbs[1].timestamp)
    assertEquals("GET https://api.example-shop.test/v1/cart → 200", crumbs[2].text)
    assertEquals("/products → /cart", crumbs[3].text)
    assertEquals(IssueLevel.Info, crumbs[4].level)
  }

  @Test
  fun context_keepsTheUserAndOnlyTheRequestsMethodAndUrl() {
    val context = event("event_js.json").context

    assertEquals("node v20.11.0", context.runtime)
    assertEquals("Linux 6.1", context.os)
    assertNull(context.device)
    assertNull(context.browser)
    assertNull(context.app)
    assertEquals(EventUser(id = "4821", email = "user@example.com", username = null, ipAddress = "203.0.113.7"), context.user)
    assertEquals(EventRequest(method = "POST", url = "https://shop.example.test/api/checkout"), context.request)
  }

  @Test
  fun javaChain_showsTheThrownExceptionFirstThenItsCause() {
    val event = event("event_java_chained.json")

    assertEquals(listOf("IllegalStateException", "SocketTimeoutException"), event.stacks.map { it.type })
    assertEquals(listOf("fetchOrders", "refresh", "main"), event.stacks[0].frames.map { it.function })
    val top = event.stacks[0].frames[0]
    assertEquals("com.example.shop.data.OrdersRepository", top.module)
    assertEquals("OrdersRepository.kt", top.file)
    assertEquals(70, top.lineNo)
    assertTrue(top.context.isEmpty())
    assertEquals("2.3.1", event.release)
  }

  @Test
  fun javaContexts_describeTheDeviceOsAndApp() {
    val context = event("event_java_chained.json").context

    assertEquals("Google Pixel 8", context.device)
    assertEquals("Android 14", context.os)
    assertEquals("2.3.1 (231)", context.app)
    assertEquals(EventUser(id = "1007", email = null, username = null, ipAddress = null), context.user)
    assertNull(context.request)
  }

  @Test
  fun aModelThatStartsWithItsMaker_isNotRepeated() {
    val event = eventFromJson("""{"contexts":{"device":{"manufacturer":"samsung","model":"Samsung Galaxy S24"}}}""")

    assertEquals("Samsung Galaxy S24", event.context.device)
  }

  @Test
  fun withoutAnException_theCrashedThreadsStackShows() {
    val stack = event("event_threads_only.json").stacks.single()

    assertNull(stack.type)
    assertEquals("FinalizerWatchdogDaemon", stack.threadName)
    assertEquals(listOf("finalize", "run"), stack.frames.map { it.function })
  }

  @Test
  fun withoutACrashedThread_theCurrentThreadsStackShows() {
    val event =
      eventFromJson(
        """
        {"entries":[{"type":"threads","data":{"values":[
          {"id":2,"name":"idle","crashed":false,"current":false,"stacktrace":{"frames":[{"function":"park"}]}},
          {"id":1,"name":"main","crashed":false,"current":true,"stacktrace":{"frames":[{"function":"loop"}]}},
          {"id":3,"name":"empty","crashed":true,"current":false,"stacktrace":{"frames":[]}}
        ]}}]}
        """
      )

    assertEquals("main", event.stacks.single().threadName)
  }

  @Test
  fun messageEvent_hasAMessageAndNoStack() {
    val event = event("event_message.json")

    assertTrue(event.stacks.isEmpty())
    assertEquals("Slow checkout: took longer than the 5s budget", event.message)
    assertEquals("staging", event.environment)
    assertEquals(EventUser(id = "600", email = null, username = "demo-user", ipAddress = null), event.context.user)
    assertEquals(listOf("Payment provider slow"), event.breadcrumbs.map { it.text })
    assertEquals(IssueLevel.Warning, event.breadcrumbs.single().level)
  }

  @Test
  fun oddEvent_mapsWhatItCanAndNeverThrows() {
    val event = event("event_odd.json")

    assertNull(event.dateCreated)
    assertEquals("shop@0.9.0", event.release)
    assertNull(event.environment)
    assertEquals(listOf(EventTag("custom", "x")), event.tags)
    assertEquals(EventContext(), event.context)
    val stack = event.stacks.single()
    assertNull(stack.type)
    assertNull(stack.value)
    val frame = stack.frames.single()
    assertNull(frame.function)
    assertEquals(12, frame.lineNo)
    assertFalse(frame.inApp)
    assertEquals(listOf(ContextLine(2, ""), ContextLine(3, "ok")), frame.context)
    val crumb = event.breadcrumbs.single()
    assertEquals("GET", crumb.text)
    assertEquals(Instant.ofEpochMilli(1_761_134_400_500), crumb.timestamp)
    assertEquals(IssueLevel.Unknown, crumb.level)
  }

  @Test
  fun longTexts_areCut() {
    val dto =
      EventDto(
        entries =
          listOf(
            buildJsonObject {
              put("type", "message")
              putJsonObject("data") { put("formatted", "x".repeat(5_000)) }
            }
          )
      )

    val message = dto.toIssueEvent().message!!

    assertEquals(MAX_TEXT_LENGTH + 1, message.length)
    assertTrue(message.endsWith("…"))
  }

  @Test
  fun anEmptyEvent_isEmpty() {
    assertEquals(
      IssueEvent(
        id = null,
        dateCreated = null,
        release = null,
        environment = null,
        stacks = emptyList(),
        message = null,
        breadcrumbs = emptyList(),
        tags = emptyList(),
        context = EventContext(),
      ),
      EventDto().toIssueEvent(),
    )
  }
}
