package io.github.sriharinmn.triage.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentryHostTest {
  @Test
  fun normalize_addsHttpsWhenSchemeMissing() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("sentry.example.com"))
  }

  @Test
  fun normalize_trimsWhitespaceAndTrailingSlash() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("  https://sentry.example.com/  "))
  }

  @Test
  fun normalize_stripsApiSuffix() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://sentry.example.com/api/0/"))
  }

  @Test
  fun normalize_keepsSubpathAndCustomPort() {
    assertEquals("https://example.com:8443/sentry", normalizeBaseUrl("https://example.com:8443/sentry/"))
  }

  @Test
  fun normalize_dropsDefaultPort() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://sentry.example.com:443"))
  }

  @Test
  fun normalize_rejectsPlainHttp() {
    assertNull(normalizeBaseUrl("http://sentry.example.com"))
  }

  @Test
  fun normalize_rejectsBlankAndGarbage() {
    assertNull(normalizeBaseUrl(""))
    assertNull(normalizeBaseUrl("   "))
    assertNull(normalizeBaseUrl("not a url"))
  }

  @Test
  fun normalize_rejectsMalformedScheme() {
    assertNull(normalizeBaseUrl("https:/sentry.example.com"))
    assertNull(normalizeBaseUrl("https//sentry.example.com"))
    assertNull(normalizeBaseUrl("http:/sentry.example.com"))
    assertNull(normalizeBaseUrl("https:"))
    assertNull(normalizeBaseUrl("https:443/sentry"))
    assertNull(normalizeBaseUrl("HTTPS:/sentry.example.com"))
  }

  @Test
  fun normalize_acceptsHostsThatStartWithHttp() {
    assertEquals("https://httpbin.org", normalizeBaseUrl("httpbin.org"))
    assertEquals("https://https.example.com", normalizeBaseUrl("https.example.com"))
  }

  @Test
  fun normalize_keepsBracketsForIpv6Host() {
    assertEquals("https://[::1]:8443", normalizeBaseUrl("https://[::1]:8443/"))
    assertEquals("https://[2001:db8::1]", normalizeBaseUrl("[2001:db8::1]"))
  }

  @Test
  fun normalize_lowercasesTheSchemeAndHost() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("HTTPS://Sentry.Example.COM/"))
  }

  @Test
  fun normalize_stripsApiSuffixWithoutATrailingSlash() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("sentry.example.com/api/0"))
  }

  @Test
  fun normalize_dropsCredentialsQueryAndFragment() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://user:secret@sentry.example.com/?project=1#top"))
  }

  @Test
  fun normalize_keepsOnlyTheServerOfAPastedSentryPage() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://sentry.example.com/organizations/acme/issues/?project=1"))
    assertEquals(
      "https://example.com:8443/sentry",
      normalizeBaseUrl("https://example.com:8443/sentry/settings/account/api/auth-tokens/"),
    )
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://sentry.example.com/api/0/organizations/"))
  }

  @Test
  fun normalize_cutsAPastedApiAddressAtItsApiRoot() {
    assertEquals("https://example.com/sentry", normalizeBaseUrl("https://example.com/sentry/api/0/projects/acme/"))
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://sentry.example.com/api/0/projects/acme/backend/issues/"))
  }

  @Test
  fun normalize_keepsOnlyTheServerOfTheLoginPage() {
    assertEquals("https://sentry.example.com", normalizeBaseUrl("https://sentry.example.com/auth/login/"))
    assertEquals("https://example.com:8443/sentry", normalizeBaseUrl("https://example.com:8443/sentry/auth/login/acme/"))
  }

  @Test
  fun normalize_rejectsOtherSchemes() {
    assertNull(normalizeBaseUrl("ftp://sentry.example.com"))
    assertNull(normalizeBaseUrl("file:///etc/hosts"))
  }

  @Test
  fun normalize_rejectsWhitespaceInside() {
    assertNull(normalizeBaseUrl("sentry.example .com"))
    assertNull(normalizeBaseUrl("https://sentry.example.com/\tsentry"))
  }

  @Test
  fun cloudHost_pointsAtSentryIo() {
    assertEquals("https://sentry.io", SentryHost.Cloud.baseUrl)
    assertEquals("https://sentry.io/settings/account/api/auth-tokens/", SentryHost.Cloud.tokenPageUrl)
  }

  @Test
  fun selfHostedHost_buildsTokenPageFromBaseUrl() {
    assertEquals(
      "https://sentry.example.com/sentry/settings/account/api/auth-tokens/",
      SentryHost.SelfHosted("https://sentry.example.com/sentry").tokenPageUrl,
    )
  }
}
