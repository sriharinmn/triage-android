package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.OrganizationLinksDto

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
