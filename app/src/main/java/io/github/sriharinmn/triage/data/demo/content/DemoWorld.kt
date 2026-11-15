package io.github.sriharinmn.triage.data.demo.content

import java.time.Duration

/**
 * The made-up shop behind "Try with sample data": its org, people, projects and issues. Every name, host and email is
 * invented (hosts and emails are on example.com).
 */
object DemoWorld {
  const val ORG_SLUG = "example-shop"
  const val ORG_NAME = "Example Shop"

  /** The tester. Starting the demo makes Sam "me", so Assign offers "Assign to me" without asking. */
  val me = DemoPerson(userId = "1001", name = "Sam Rivera", email = "sam@example.com")

  val people: List<DemoPerson> =
    listOf(
      me,
      DemoPerson(userId = "1002", name = "Priya Nair", email = "priya@example.com"),
      DemoPerson(userId = "1003", name = "Diego Alvarez", email = "diego@example.com"),
      DemoPerson(userId = "1004", name = "Mei Tanaka", email = "mei@example.com"),
      DemoPerson(userId = "1005", name = "Jordan Okafor", email = "jordan@example.com"),
    )

  val projects: List<DemoProject> =
    listOf(
      DemoProject(
        id = "4501",
        slug = SHOP_ANDROID,
        name = SHOP_ANDROID,
        platform = "android",
        shortIdPrefix = "SHOP-ANDROID",
        firstShortNumber = 41,
        releases =
          listOf(
            DemoRelease("com.example.shop@2.3.0+230", "2.3.0 (230)", Duration.ofDays(-34)),
            DemoRelease("com.example.shop@2.4.0+240", "2.4.0 (240)", Duration.ofDays(-12)),
            DemoRelease("com.example.shop@2.4.1+241", "2.4.1 (241)", Duration.ofDays(-3)),
            DemoRelease("com.example.shop@2.5.0+250", "2.5.0 (250)", Duration.ofDays(5)),
          ),
      ),
      DemoProject(
        id = "4502",
        slug = SHOP_WEB,
        name = SHOP_WEB,
        platform = "javascript-react",
        shortIdPrefix = "SHOP-WEB",
        firstShortNumber = 18,
        releases =
          listOf(
            DemoRelease("shop-web@5.12.0", "5.12.0", Duration.ofDays(-34)),
            DemoRelease("shop-web@5.13.0", "5.13.0", Duration.ofDays(-12)),
            DemoRelease("shop-web@5.13.2", "5.13.2", Duration.ofDays(-3)),
            DemoRelease("shop-web@5.14.0", "5.14.0", Duration.ofDays(4)),
          ),
      ),
      DemoProject(
        id = "4503",
        slug = SHOP_API,
        name = SHOP_API,
        platform = "python",
        shortIdPrefix = "SHOP-API",
        firstShortNumber = 63,
        releases =
          listOf(
            DemoRelease("shop-api@1.8.0", "1.8.0", Duration.ofDays(-34)),
            DemoRelease("shop-api@1.9.0", "1.9.0", Duration.ofDays(-12)),
            DemoRelease("shop-api@1.9.1", "1.9.1", Duration.ofDays(-3)),
            DemoRelease("shop-api@1.10.0", "1.10.0", Duration.ofDays(8)),
          ),
      ),
    )

  /** The issues there when the demo starts. */
  val startingIssues: List<DemoStartingIssue> = AndroidIssues.starting + WebIssues.starting + ApiIssues.starting

  /** Issues that turn up later, one per "new issue" happening, taking the projects in turn. */
  val arrivals: List<DemoIssueSpec> = interleave(AndroidArrivals.all, WebArrivals.all, ApiArrivals.all)

  private val specsByKey: Map<String, DemoIssueSpec> = (startingIssues.map { it.spec } + arrivals).associateBy { it.key }

  private val projectsBySlug: Map<String, DemoProject> = projects.associateBy { it.slug }

  /** Null for a key no longer in the content (a demo saved by an older version of the app). */
  fun spec(key: String): DemoIssueSpec? = specsByKey[key]

  fun project(slug: String): DemoProject = projectsBySlug.getValue(slug)

  fun person(userId: String): DemoPerson? = people.firstOrNull { it.userId == userId }
}

/** The first of each list, then the second of each, and so on. */
internal fun <T> interleave(vararg lists: List<T>): List<T> {
  val longest = lists.maxOfOrNull { it.size } ?: 0
  return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
}
