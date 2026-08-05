package com.tend.app

import com.tend.app.data.vault.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vault's security claims, as tests.
 *
 * A low iteration count is used throughout — these assert the *construction*,
 * not the work factor, and 210k iterations per derivation would make the suite
 * take minutes. The real count is asserted separately.
 */
class VaultCryptoTest {

    private val fast = 1_000

    @Test
    fun `the shipped iteration count is not accidentally lowered`() {
        assertTrue(
            "PBKDF2 below ~200k is too cheap to brute-force against in 2026",
            VaultCrypto.ITERATIONS >= 200_000,
        )
    }

    @Test
    fun `the same password and salt derive the same key`() {
        val salt = VaultCrypto.newSalt()
        val a = VaultCrypto.deriveKey("correct horse".toCharArray(), salt, fast)
        val b = VaultCrypto.deriveKey("correct horse".toCharArray(), salt, fast)
        assertArrayEquals(a.encoded, b.encoded)
    }

    @Test
    fun `a different salt derives a different key from the same password`() {
        val password = "correct horse"
        val a = VaultCrypto.deriveKey(password.toCharArray(), VaultCrypto.newSalt(), fast)
        val b = VaultCrypto.deriveKey(password.toCharArray(), VaultCrypto.newSalt(), fast)
        assertFalse(a.encoded.contentEquals(b.encoded))
    }

    @Test
    fun `round trips text`() {
        val key = VaultCrypto.newDataKey()
        val sealed = VaultCrypto.sealText(key, "spare key is with Sam")
        assertEquals("spare key is with Sam", VaultCrypto.openText(key, sealed))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val key = VaultCrypto.newDataKey()
        val secret = "spare key is with Sam"
        assertFalse(VaultCrypto.sealText(key, secret).contains(secret))
    }

    @Test
    fun `the same plaintext seals differently every time`() {
        // A fresh IV per seal. Without it, identical entries would be visibly
        // identical in the database even while encrypted.
        val key = VaultCrypto.newDataKey()
        assertNotEquals(VaultCrypto.sealText(key, "same"), VaultCrypto.sealText(key, "same"))
    }

    @Test
    fun `the wrong key does not decrypt`() {
        val sealed = VaultCrypto.sealText(VaultCrypto.newDataKey(), "secret")
        assertNull(VaultCrypto.openText(VaultCrypto.newDataKey(), sealed))
    }

    @Test
    fun `tampered ciphertext is rejected rather than half-decrypted`() {
        // GCM is authenticated: a flipped bit must fail, not yield garbage.
        val key = VaultCrypto.newDataKey()
        val bytes = VaultCrypto.decode(VaultCrypto.sealText(key, "secret")).copyOf()
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex] + 1).toByte()
        assertNull(VaultCrypto.openText(key, VaultCrypto.encode(bytes)))
    }

    @Test
    fun `truncated ciphertext is rejected`() {
        val key = VaultCrypto.newDataKey()
        assertNull(VaultCrypto.openText(key, VaultCrypto.encode(ByteArray(4))))
        assertNull(VaultCrypto.openText(key, ""))
        assertNull(VaultCrypto.openText(key, "not base64 at all !!!"))
    }

    // ── the two doors ───────────────────────────────────────────

    @Test
    fun `password and recovery code unwrap the same data key`() {
        val dek = VaultCrypto.newDataKey()
        val passwordSalt = VaultCrypto.newSalt()
        val recoverySalt = VaultCrypto.newSalt()
        val code = VaultCrypto.newRecoveryCode()

        val wrappedByPassword = VaultCrypto.wrapDataKey(
            VaultCrypto.deriveKey("hunter2hunter2".toCharArray(), passwordSalt, fast), dek,
        )
        val wrappedByCode = VaultCrypto.wrapDataKey(
            VaultCrypto.deriveKey(VaultCrypto.normalizeRecoveryCode(code), recoverySalt, fast), dek,
        )

        val viaPassword = VaultCrypto.unwrapDataKey(
            VaultCrypto.deriveKey("hunter2hunter2".toCharArray(), passwordSalt, fast), wrappedByPassword,
        )
        val viaCode = VaultCrypto.unwrapDataKey(
            VaultCrypto.deriveKey(VaultCrypto.normalizeRecoveryCode(code), recoverySalt, fast), wrappedByCode,
        )

        assertArrayEquals(dek.encoded, viaPassword!!.encoded)
        assertArrayEquals(dek.encoded, viaCode!!.encoded)
    }

    @Test
    fun `the wrapped key is never the key itself`() {
        val dek = VaultCrypto.newDataKey()
        val wrapped = VaultCrypto.wrapDataKey(
            VaultCrypto.deriveKey("password".toCharArray(), VaultCrypto.newSalt(), fast), dek,
        )
        assertFalse(
            "The data key must not be recoverable from stored material alone",
            VaultCrypto.decode(wrapped).contentEquals(dek.encoded),
        )
    }

    @Test
    fun `a wrong password fails to unwrap`() {
        val salt = VaultCrypto.newSalt()
        val wrapped = VaultCrypto.wrapDataKey(
            VaultCrypto.deriveKey("right".toCharArray(), salt, fast), VaultCrypto.newDataKey(),
        )
        assertNull(
            VaultCrypto.unwrapDataKey(VaultCrypto.deriveKey("wrong".toCharArray(), salt, fast), wrapped)
        )
    }

    @Test
    fun `the verifier accepts the right key and rejects any other`() {
        val dek = VaultCrypto.newDataKey()
        val verifier = VaultCrypto.makeVerifier(dek)
        assertTrue(VaultCrypto.verifies(dek, verifier))
        assertFalse(VaultCrypto.verifies(VaultCrypto.newDataKey(), verifier))
    }

    // ── recovery code ───────────────────────────────────────────

    @Test
    fun `recovery codes are unique, grouped, and carry full entropy`() {
        val codes = (1..200).map { VaultCrypto.newRecoveryCode() }
        assertEquals(200, codes.toSet().size)
        // 16 bytes rendered as 32 characters, in 8 groups of 4.
        assertEquals(8, codes.first().split("-").size)
        assertEquals(32, VaultCrypto.normalizeRecoveryCode(codes.first()).size)
    }

    @Test
    fun `recovery code entry ignores case and separators`() {
        val code = VaultCrypto.newRecoveryCode()
        val typedBack = code.lowercase().replace("-", " ")
        assertArrayEquals(
            VaultCrypto.normalizeRecoveryCode(code),
            VaultCrypto.normalizeRecoveryCode(typedBack),
        )
    }

    @Test
    fun `the recovery alphabet excludes characters that get misread`() {
        val code = VaultCrypto.newRecoveryCode().replace("-", "")
        assertTrue(
            "I, L, O and U must not appear — they are misread as 1, 0 and V",
            code.none { it in "ILOU" },
        )
    }
}
