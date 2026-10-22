package io.github.sriharinmn.triage.data.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenCipher {
  fun encrypt(plaintext: String): String

  fun decrypt(ciphertext: String): String
}

/** AES-256-GCM with a key that lives in (and never leaves) the Android Keystore. Output: base64(iv + ciphertext). */
class KeystoreTokenCipher(private val alias: String = "triage_token_key") : TokenCipher {

  override fun encrypt(plaintext: String): String {
    val cipher = encryptCipher()
    val sealed = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(sealed, Base64.NO_WRAP)
  }

  override fun decrypt(ciphertext: String): String {
    val sealed = Base64.decode(ciphertext, Base64.NO_WRAP)
    val key = existingKey() ?: throw KeyStoreException("No key")
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
    return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
  }

  /** An existing key that is unusable (invalidated, wrong parameters, broken Keystore state) is replaced once. */
  private fun encryptCipher(): Cipher =
    try {
      initEncryptCipher()
    } catch (e: Exception) {
      if (e !is GeneralSecurityException && e !is ProviderException) throw e
      deleteKey()
      initEncryptCipher()
    }

  private fun initEncryptCipher(): Cipher =
    Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }

  private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

  private fun existingKey(): SecretKey? = (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

  private fun getOrCreateKey(): SecretKey =
    synchronized(lock) {
      existingKey() ?: run {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
          KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        )
        generator.generateKey()
      }
    }

  private fun deleteKey() {
    synchronized(lock) { keyStore().deleteEntry(alias) }
  }

  private companion object {
    const val KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128

    /** Shared by all instances so two ciphers on the same alias can't race to create the key. */
    val lock = Any()
  }
}
