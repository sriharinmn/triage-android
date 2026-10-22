package io.github.sriharinmn.triage.data.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
  private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  private val cipher = KeystoreTokenCipher(alias = ALIAS)

  @After
  fun tearDown() {
    keyStore.deleteEntry(ALIAS)
    keyStore.deleteEntry(BAD_ALIAS)
  }

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

  @Test
  fun unusableExistingKey_isReplacedOnEncrypt() {
    // A key under our alias that can't do AES/GCM: Cipher.init throws InvalidKeyException.
    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      .apply {
        init(
          KeyGenParameterSpec.Builder(BAD_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setKeySize(256)
            .build()
        )
      }
      .generateKey()
    val badKeyCipher = KeystoreTokenCipher(alias = BAD_ALIAS)

    assertEquals("sntryu_secret", badKeyCipher.decrypt(badKeyCipher.encrypt("sntryu_secret")))
  }

  @Test
  fun decrypt_withoutKey_throwsAndDoesNotCreateKey() {
    keyStore.deleteEntry(ALIAS)

    assertThrows(GeneralSecurityException::class.java) { cipher.decrypt("AAAA".repeat(10)) }

    assertFalse(keyStore.containsAlias(ALIAS))
  }

  private companion object {
    const val ALIAS = "triage_test_key"
    const val BAD_ALIAS = "triage_test_key_bad"
  }
}
