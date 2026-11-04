package com.carecircle.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"app":"carecircle","schemaVersion":9}""".toByteArray()
    // A low iteration count keeps the suite quick; the format and the checks are identical.
    private val fast = 1_000

    @Test fun roundTrip() {
        val sealed = BackupCrypto.encrypt(plain, "correct horse", fast)
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("carecircle"))
        val r = BackupCrypto.decrypt(sealed, "correct horse", fast) as DecryptResult.Ok
        assertArrayEquals(plain, r.plain)
    }

    @Test fun twoEncryptionsOfTheSameDataDiffer() {
        assertFalse(BackupCrypto.encrypt(plain, "secret1", fast).contentEquals(BackupCrypto.encrypt(plain, "secret1", fast)))
    }

    @Test fun wrongPasswordIsRejected() {
        val sealed = BackupCrypto.encrypt(plain, "secret1", fast)
        assertEquals(DecryptResult.WrongPasswordOrDamaged, BackupCrypto.decrypt(sealed, "secret2", fast))
    }

    @Test fun truncatedFileIsMalformedOrDamaged() {
        val sealed = BackupCrypto.encrypt(plain, "secret1", fast)
        assertEquals(DecryptResult.Malformed, BackupCrypto.decrypt(sealed.copyOf(20), "secret1", fast))
        assertEquals(DecryptResult.WrongPasswordOrDamaged, BackupCrypto.decrypt(sealed.copyOf(sealed.size - 5), "secret1", fast))
    }

    @Test fun anyChangedByteIsDetected() {
        val sealed = BackupCrypto.encrypt(plain, "secret1", fast)
        for (pos in listOf(5, 20, 30, sealed.size - 1)) { // salt, iv, ciphertext, tag
            val tampered = sealed.copyOf().also { it[pos] = (it[pos].toInt() xor 1).toByte() }
            assertEquals("byte $pos", DecryptResult.WrongPasswordOrDamaged, BackupCrypto.decrypt(tampered, "secret1", fast))
        }
        val badVersion = sealed.copyOf().also { it[4] = 9 }
        assertEquals(DecryptResult.Malformed, BackupCrypto.decrypt(badVersion, "secret1", fast))
    }

    @Test fun plainJsonIsNotTakenForAnEncryptedFile() {
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertEquals(DecryptResult.Malformed, BackupCrypto.decrypt(plain, "secret1", fast))
    }

    @Test fun shortPasswordsAreNotAllowed() {
        assertFalse(BackupCrypto.validPassword("12345"))
        assertTrue(BackupCrypto.validPassword("123456"))
        try { BackupCrypto.encrypt(plain, "abc", fast); throw AssertionError("should refuse") } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun theRealIterationCountWorksToo() {
        val sealed = BackupCrypto.encrypt(plain, "secret1")
        assertTrue(BackupCrypto.decrypt(sealed, "secret1") is DecryptResult.Ok)
        assertEquals(210_000, BackupCrypto.ITERATIONS)
    }
}
