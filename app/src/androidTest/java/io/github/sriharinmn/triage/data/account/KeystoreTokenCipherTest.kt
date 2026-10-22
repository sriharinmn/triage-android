package io.github.sriharinmn.triage.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
  private val cipher = KeystoreTokenCipher(alias = "triage_test_key")

  @Test
  fun encryptThenDecrypt_returnsOriginal() {
    assertEquals("sntryu_secret", cipher.decrypt(cipher.encrypt("sntryu_secret")))
  }

  @Test
  fun encrypt_hidesPlaintextAndUsesFreshIv() {
    val first = cipher.encrypt("sntryu_secret")
    val second = cipher.encrypt("sntryu_secret")
    assertFalse(first.contains("sntryu_secret"))
    assertNotEquals(first, second)
  }
}
