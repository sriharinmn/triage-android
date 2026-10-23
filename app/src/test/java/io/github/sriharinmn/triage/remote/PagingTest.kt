package io.github.sriharinmn.triage.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagingTest {
  @Test
  fun nextCursor_whenNextPageHasResults() {
    val header =
      """<https://sentry.io/api/0/organizations/acme/issues/?cursor=0:0:1>; rel="previous"; results="false"; """ +
        """cursor="0:0:1", <https://sentry.io/api/0/organizations/acme/issues/?cursor=0:25:0>; rel="next"; """ +
        """results="true"; cursor="0:25:0""""
    assertEquals("0:25:0", nextCursorFromLink(header))
  }

  @Test
  fun noNextCursor_whenNextPageIsEmpty() {
    val header =
      """<https://x/?cursor=0:0:1>; rel="previous"; results="false"; cursor="0:0:1", """ +
        """<https://x/?cursor=0:25:0>; rel="next"; results="false"; cursor="0:25:0""""
    assertNull(nextCursorFromLink(header))
  }

  @Test
  fun noNextCursor_withoutHeader() {
    assertNull(nextCursorFromLink(null))
    assertNull(nextCursorFromLink(""))
  }

  @Test
  fun noNextCursor_whenOnlyPreviousIsListed() {
    assertNull(nextCursorFromLink("""<https://x/?cursor=1>; rel="previous"; results="true"; cursor="1""""))
  }

  @Test
  fun commasAndSemicolonsInsideTheUrl_doNotSplitEntries() {
    val header =
      """<https://x/?query=a,b;c&cursor=1>; rel="previous"; results="true"; cursor="1", """ +
        """<https://x/?query=a,b;c&cursor=2>; rel="next"; results="true"; cursor="1700000000000:0:0""""
    assertEquals("1700000000000:0:0", nextCursorFromLink(header))
  }

  @Test
  fun toleratesMissingSpaces() {
    assertEquals("c2", nextCursorFromLink("""<https://x/>;rel="next";results="true";cursor="c2""""))
  }

  @Test
  fun emptyCursor_meansNoNextPage() {
    assertNull(nextCursorFromLink("""<https://x/>; rel="next"; results="true"; cursor="""""))
  }
}
