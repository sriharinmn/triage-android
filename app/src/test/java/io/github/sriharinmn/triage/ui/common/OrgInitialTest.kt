package io.github.sriharinmn.triage.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class OrgInitialTest {
  @Test
  fun theFirstLetterOrDigit_uppercased() {
    assertEquals("S", orgInitial("srihari"))
    assertEquals("A", orgInitial("  acme inc"))
    assertEquals("4", orgInitial("42 labs"))
    assertEquals("É", orgInitial("élan"))
  }

  @Test
  fun aNameWithoutLettersOrDigits_getsAQuestionMark() {
    assertEquals("?", orgInitial(""))
    assertEquals("?", orgInitial("--"))
  }
}
