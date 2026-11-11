package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.remote.IssueListParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InboxFilterTest {
  @Test
  fun defaults_areUnresolvedEverythingByLastSeen() {
    assertEquals(InboxFilter(InboxTab.Unresolved, null, null, IssueSort.LastSeen), InboxFilter())
  }

  @Test
  fun tabQueries_matchSentrysOwnTabs() {
    assertEquals("is:unresolved is:for_review assigned_or_suggested:[me, my_teams, none]", InboxTab.ForReview.query)
    assertEquals("is:unresolved", InboxTab.Unresolved.query)
    assertEquals("is:regressed", InboxTab.Regressed.query)
    assertEquals("is:escalating", InboxTab.Escalating.query)
  }

  @Test
  fun sortValues_matchSentrysApi() {
    assertEquals(listOf("date", "new", "freq", "user"), IssueSort.entries.map { it.apiValue })
  }

  @Test
  fun toParams_carriesEveryDimension() {
    val filter = InboxFilter(InboxTab.Regressed, projectId = "102", environment = "production", sort = IssueSort.Users)
    assertEquals(
      IssueListParams(query = "is:regressed", projectIds = listOf("102"), environment = "production", sort = "user"),
      filter.toParams(),
    )
  }

  @Test
  fun listKey_differsForEveryDimension() {
    val base = InboxFilter()
    val keys =
      listOf(
        base,
        base.copy(tab = InboxTab.ForReview),
        base.copy(projectId = "1"),
        base.copy(environment = "production"),
        base.copy(sort = IssueSort.Events),
      ).map { it.listKey }
    assertEquals(keys.size, keys.toSet().size)
  }

  @Test
  fun listKey_cannotBeConfusedByOddEnvironmentNames() {
    assertNotEquals(InboxFilter(environment = "*").listKey, InboxFilter(environment = null).listKey)
    assertNotEquals(
      InboxFilter(projectId = "1|=2", environment = "3").listKey,
      InboxFilter(projectId = "1", environment = "2|=3").listKey,
    )
  }
}
