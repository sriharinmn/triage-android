package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueProjectDto
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.OrganizationLinksDto
import io.github.sriharinmn.triage.remote.ProjectDto

val US_ORG =
  OrganizationDto(
    id = "1",
    slug = "acme",
    name = "Acme Inc",
    links = OrganizationLinksDto(organizationUrl = "https://acme.sentry.io", regionUrl = "https://us.sentry.io"),
  )

val EU_ORG =
  OrganizationDto(
    id = "2",
    slug = "beta-gmbh",
    name = "Beta GmbH",
    links = OrganizationLinksDto(organizationUrl = "https://beta-gmbh.sentry.io", regionUrl = "https://de.sentry.io/"),
  )

val TEST_ACCOUNT =
  Account(
    apiBaseUrl = "https://de.sentry.io",
    webBaseUrl = "https://example-org.sentry.io",
    token = "sntryu_test",
    orgSlug = "example-org",
    orgName = "Example Org",
  )

fun issueDto(
  id: String,
  title: String = "Error $id",
  projectId: String = "101",
  substatus: String? = "ongoing",
  permalink: String? = "https://example-org.sentry.io/issues/$id/",
) =
  IssueDto(
    id = id,
    shortId = "DEMO-$id",
    title = title,
    culprit = "handler in app",
    level = "error",
    status = "unresolved",
    substatus = substatus,
    count = 3,
    userCount = 1,
    firstSeen = "2026-10-20T08:00:00Z",
    lastSeen = "2026-10-22T11:00:00Z",
    permalink = permalink,
    project = IssueProjectDto(id = projectId, slug = "project-$projectId"),
    stats = mapOf("24h" to List(24) { hour -> listOf(0.0, hour.toDouble()) }),
  )

fun projectDto(id: String, environments: List<String> = emptyList(), hasAccess: Boolean = true) =
  ProjectDto(id = id, slug = "project-$id", name = "Project $id", environments = environments, hasAccess = hasAccess)
