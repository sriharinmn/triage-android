package io.github.sriharinmn.triage.testing

private object FixtureAnchor

/** A JSON file from `src/test/resources/fixtures/`. Fixtures are synthetic, in Sentry's documented response shape. */
fun readFixture(name: String): String =
  checkNotNull(FixtureAnchor::class.java.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()
