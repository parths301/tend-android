package com.tend.app.data.vault

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The cryptographic boundary for Memory.
 *
 * ## Shape
 *
 * A single random 256-bit **data key (DEK)** encrypts everything in the vault.
 * The DEK itself is never stored in the clear — it is wrapped twice:
 *
 * ```
 *   password ──PBKDF2──▶ KEK₁ ──AES-GCM──▶ wrapped DEK  ┐
 *                                                        ├─▶ same DEK
 *   recovery code ──PBKDF2──▶ KEK₂ ──AES-GCM──▶ wrapped DEK  ┘
 * ```
 *
 * Two consequences worth being explicit about, because they are the point:
 *
 * - **Changing the password re-wraps, it does not re-encrypt.** The DEK is
 *   unchanged, so a vault with a thousand files changes password instantly.
 * - **There is no third way in.** The password is never stored, and neither is
 *   the DEK. Losing both the password and the recovery code means the contents
 *   are unrecoverable — by construction, not by policy.
 *
 * ## Why not `EncryptedSharedPreferences` for the payloads
 *
 * That class is already used for BYOK API keys and is right for small values,
 * but its key material is held by the Android keystore and unlocked whenever
 * the app runs. A vault that unlocks itself is not password-protected. The
 * user's password has to be an input, which means deriving a key from it.
 *
 * ## Parameters
 *
 * PBKDF2-HMAC-SHA256 at [ITERATIONS], the strongest KDF available on the
 * platform without adding a native dependency. Argon2id would be preferable and
 * is deliberately not worth a new native library here; the iteration count is
 * stored per vault so it can be raised later without invalidating old vaults.
 */
object VaultCrypto {

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val SALT_BYTES = 16

    /** ~300ms on a mid-range phone in 2026. Stored per vault so it can rise. */
    const val ITERATIONS = 210_000

    /** Proves a derived key is the right one without revealing anything. */
    private const val VERIFIER_PLAINTEXT = "tend-vault-v1"

    private val random = SecureRandom()

    // ── key derivation ──────────────────────────────────────────

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(secret: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            // The PBEKeySpec holds its own copy of the characters; clear it so
            // the password does not sit in the heap until the next GC.
            spec.clearPassword()
        }
    }

    fun newDataKey(): SecretKey =
        SecretKeySpec(ByteArray(KEY_BITS / 8).also(random::nextBytes), "AES")

    // ── sealing ─────────────────────────────────────────────────

    /**
     * AES-GCM with a fresh random IV, which is prefixed to the ciphertext.
     *
     * GCM is authenticated, so a tampered or truncated blob fails to decrypt
     * rather than returning plausible garbage.
     */
    fun seal(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /** Returns null when the key is wrong or the blob has been altered. */
    fun open(key: SecretKey, sealed: ByteArray): ByteArray? = try {
        if (sealed.size <= IV_BYTES) null else {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, sealed.copyOfRange(0, IV_BYTES)),
            )
            cipher.doFinal(sealed.copyOfRange(IV_BYTES, sealed.size))
        }
    } catch (e: Exception) {
        null
    }

    fun sealText(key: SecretKey, text: String): String = encode(seal(key, text.toByteArray()))

    fun openText(key: SecretKey, sealed: String): String? =
        open(key, decode(sealed))?.toString(Charsets.UTF_8)

    // ── wrapping ────────────────────────────────────────────────

    fun wrapDataKey(kek: SecretKey, dek: SecretKey): String = encode(seal(kek, dek.encoded))

    fun unwrapDataKey(kek: SecretKey, wrapped: String): SecretKey? =
        open(kek, decode(wrapped))?.let { SecretKeySpec(it, "AES") }

    fun makeVerifier(dek: SecretKey): String = sealText(dek, VERIFIER_PLAINTEXT)

    fun verifies(dek: SecretKey, verifier: String): Boolean =
        openText(dek, verifier) == VERIFIER_PLAINTEXT

    // ── recovery code ───────────────────────────────────────────

    /**
     * 128 bits rendered as 32 characters, grouped for transcription:
     * `A1B2-C3D4-…`.
     *
     * The alphabet omits I, L, O and U, so the code cannot be misread as digits
     * or accidentally spell anything. Input is upper-cased and stripped of
     * separators, so how the user types it back never matters.
     */
    fun newRecoveryCode(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        val body = bytes.joinToString("") { b ->
            val v = b.toInt() and 0xFF
            "${ALPHABET[v shr 4]}${ALPHABET[v and 0x0F]}"
        }
        return body.chunked(4).joinToString("-")
    }

    /** Strips formatting so "a1b2 c3d4" and "A1B2-C3D4" derive the same key. */
    fun normalizeRecoveryCode(input: String): CharArray =
        input.filter { !it.isWhitespace() && it != '-' }.uppercase().toCharArray()

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    // ── encoding ────────────────────────────────────────────────

    // java.util.Base64, not android.util.Base64: the Android one is stubbed in
    // JVM unit tests and would throw "not mocked", taking the crypto tests with
    // it. Available since API 26, which is minSdk.
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = try {
        Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        ByteArray(0)
    }
}
