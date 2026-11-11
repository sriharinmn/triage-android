package io.github.sriharinmn.triage.ui.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sriharinmn.triage.data.alerts.AlertAction
import io.github.sriharinmn.triage.data.alerts.AlertFinding
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertPost
import io.github.sriharinmn.triage.data.alerts.NotificationActionRequest
import io.github.sriharinmn.triage.data.detail.DetailError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds Triage's notifications and checks what they show. Uses its own `test_` channels and made-up issue ids, so the
 * owner's real channels and notifications are left alone. The one test that posts runs only if notifications are allowed.
 */
@RunWith(AndroidJUnit4::class)
class AlertNotifierTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val notifier = AlertNotifier(context, channelPrefix = TEST_PREFIX)
  private val system = context.getSystemService(NotificationManager::class.java)
  private val finding =
    AlertFinding("test-1", "DEMO-1", "DatabaseTimeoutError: Query timed out", "triage-demo", AlertKind.Regressed, 36, 6)
  private val request = NotificationActionRequest("test-1", "DEMO-1", "example-org", AlertAction.Resolve)

  @Before
  fun setUp() {
    notifier.createChannels()
  }

  @After
  fun tearDown() {
    notifier.cancelIssue("test-1")
    CHANNELS.forEach { system.deleteNotificationChannel(TEST_PREFIX + it) }
  }

  private fun Notification.title(): String? = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

  private fun Notification.text(): String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

  private fun Notification.buttons(): List<String> = actions.orEmpty().map { it.title.toString() }

  @Test
  fun theChannels_areOnePerKind_plusAlertStatus() {
    assertEquals("New issues", system.getNotificationChannel("test_alerts_new").name.toString())
    assertEquals("Regressed issues", system.getNotificationChannel("test_alerts_regressed").name.toString())
    assertEquals("Escalating issues", system.getNotificationChannel("test_alerts_escalating").name.toString())
    assertEquals("Alert status", system.getNotificationChannel("test_alerts_status").name.toString())
  }

  @Test
  fun aSingle_showsItsKindIdTitleCountsAndThreeButtons_hiddenOnTheLockScreen() {
    val notification = notifier.buildSingle("example-org", finding)

    assertEquals("test_alerts_regressed", notification.channelId)
    assertEquals("Regressed · DEMO-1", notification.title())
    assertEquals("DatabaseTimeoutError: Query timed out", notification.text())
    assertEquals("triage-demo", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
    assertEquals(
      "DatabaseTimeoutError: Query timed out\n36 events · 6 users",
      notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
    )
    assertEquals(listOf("Resolve", "Archive", "Open"), notification.buttons())
    assertEquals(AlertNotifier.GROUP_KEY, notification.group)
    assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
    assertEquals("1 regressed issue", notification.publicVersion.title())
  }

  @Test
  fun everyPendingIntent_isImmutable() {
    assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    val single = notifier.buildSingle("example-org", finding)

    assertTrue(single.contentIntent.isImmutable)
    single.actions.forEach { assertTrue(it.actionIntent.isImmutable) }
    notifier.buildPending(request, original = null).actions.forEach { assertTrue(it.actionIntent.isImmutable) }
  }

  @Test
  fun aSummary_countsEachKind_namesTheProject_andHasNoButtons() {
    val summary = AlertPost.Summary(mapOf(AlertKind.New to 12, AlertKind.Regressed to 1), "triage-demo")

    val notification = notifier.buildSummary("example-org", summary)

    assertEquals("12 new issues in triage-demo, 1 regressed", notification.title())
    assertEquals("Tap to open the inbox", notification.text())
    assertTrue(notification.buttons().isEmpty())
    assertEquals("test_alerts_regressed", notification.channelId)
    assertEquals("12 new issues, 1 regressed", notification.publicVersion.title())
  }

  @Test
  fun aSummaryWithoutOneProject_leavesItOut() {
    val notification = notifier.buildSummary("example-org", AlertPost.Summary(mapOf(AlertKind.Escalating to 4), null))

    assertEquals("4 escalating issues", notification.title())
  }

  @Test
  fun pending_saysWhatIsGoingOut_offersUndo_andIsSilent() {
    val notification = notifier.buildPending(request, original = null)

    assertEquals("Resolving DEMO-1…", notification.title())
    assertEquals(listOf("Undo"), notification.buttons())
    assertEquals("test_alerts_status", notification.channelId)
    assertEquals(
      "Archiving DEMO-1…",
      notifier.buildPending(request.copy(action = AlertAction.Archive), original = null).title(),
    )
  }

  @Test
  fun pending_carriesTheOriginalNotification_soUndoCanBringItBack() {
    val original = notifier.buildSingle("example-org", finding)

    val pending = notifier.buildPending(request, original)

    // Read back the way restore() does, without posting anything.
    assertEquals("Regressed � DEMO-1", AlertNotifier.originalOf(pending)?.title())
    assertNull(AlertNotifier.originalOf(original))
  }

  @Test
  fun aSecondTap_keepsTheInnermostOriginal_notThePendingNotification() {
    val original = notifier.buildSingle("example-org", finding)
    val firstPending = notifier.buildPending(request, original)

    val kept = AlertNotifier.originalToKeep(firstPending)
    val secondPending = notifier.buildPending(request.copy(action = AlertAction.Archive), kept)

    assertEquals("Regressed � DEMO-1", AlertNotifier.originalOf(secondPending)?.title())
    // A notification that isn't pending is its own original; with nothing showing there is nothing to keep.
    assertEquals("Regressed � DEMO-1", AlertNotifier.originalToKeep(original)?.title())
    assertNull(AlertNotifier.originalToKeep(null))
  }

  @Test
  fun aFailure_saysWhyInPlainWords_withOpen() {
    val failed = notifier.buildFailed(request, DetailError.Network)

    assertEquals("Couldn't resolve DEMO-1.", failed.title())
    assertEquals("Couldn't reach Sentry. Check your connection.", failed.text())
    assertEquals(listOf("Open"), failed.buttons())
    assertEquals("DEMO-1 no longer exists.", notifier.buildFailed(request, DetailError.NotFound).title())
  }

  @Test
  fun theAuthProblem_asksToReconnect() {
    val notification = notifier.buildAuthProblem()

    assertEquals("Triage can't check Sentry", notification.title())
    assertEquals("Open Triage to reconnect.", notification.text())
  }

  @Test
  fun posting_undoingAndCancelling_inTheShade() {
    assumeTrue("Allow Triage's notifications to run this test", notifier.canPost())
    val tag = AlertNotifier.issueTag("test-1")

    notifier.post("example-org", AlertPost.Singles(listOf(finding)))
    awaitTitle(tag, "Regressed · DEMO-1")
    notifier.showPending(request)
    awaitTitle(tag, "Resolving DEMO-1…")
    notifier.restore("test-1")
    awaitTitle(tag, "Regressed · DEMO-1")
    notifier.cancelIssue("test-1")
    awaitTitle(tag, null)
  }

  /** Android posts asynchronously and drops updates that come faster than about five a second, so pace and poll. */
  private fun awaitTitle(tag: String, expected: String?) {
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (true) {
      val title = system.activeNotifications.firstOrNull { it.tag == tag }?.notification?.title()
      if (title == expected) break
      if (SystemClock.uptimeMillis() > deadline) fail("expected \"$expected\" for $tag, saw \"$title\"")
      Thread.sleep(50)
    }
    Thread.sleep(300)
  }

  private companion object {
    const val TEST_PREFIX = "test_"
    val CHANNELS = listOf("alerts_new", "alerts_regressed", "alerts_escalating", "alerts_status")
  }
}
