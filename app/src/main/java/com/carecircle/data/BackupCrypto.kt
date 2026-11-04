package com.carecircle.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed interface DecryptResult {
    data class Ok(val plain: ByteArray) : DecryptResult
    /** The tag did not verify: a wrong password or a file that was changed. The two cannot be told apart. */
    data object WrongPasswordOrDamaged : DecryptResult
    /** Too short or not one of ours. */
    data object Malformed : DecryptResult
}

/**
 * Optional password protection for backup files: PBKDF2-HMAC-SHA256 (210,000 iterations, random salt) to a 256-bit key, then
 * AES-GCM. The header (magic, version, salt, iv) is authenticated too. The password is used for one call and never stored or logged.
 *
 * File layout: "CCBK" | version (1 byte) | salt (16) | iv (12) | ciphertext and 16-byte tag.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val VERSION: Byte = 1
    const val ITERATIONS = 210_000
    const val MIN_PASSWORD = 6
    private const val SALT = 16
    private const val IV = 12
    private const val TAG_BITS = 128
    private const val HEADER = 4 + 1 + SALT + IV

    private val random = SecureRandom()

    fun validPassword(p: String) = p.length >= MIN_PASSWORD

    fun isEncrypted(bytes: ByteArray) = bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, password: String, iterations: Int = ITERATIONS): ByteArray {
        require(validPassword(password)) { "password too short" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt + iv
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password.toCharArray(), salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(header)
        return header + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: String, iterations: Int = ITERATIONS): DecryptResult {
        if (!isEncrypted(data) || data.size < HEADER + TAG_BITS / 8 || data[4] != VERSION) return DecryptResult.Malformed
        val salt = data.copyOfRange(5, 5 + SALT)
        val iv = data.copyOfRange(5 + SALT, HEADER)
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password.toCharArray(), salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            c.updateAAD(data.copyOfRange(0, HEADER))
            DecryptResult.Ok(c.doFinal(data, HEADER, data.size - HEADER))
        } catch (e: javax.crypto.AEADBadTagException) {
            DecryptResult.WrongPasswordOrDamaged
        } catch (e: java.security.GeneralSecurityException) {
            DecryptResult.WrongPasswordOrDamaged
        }
    }
}
