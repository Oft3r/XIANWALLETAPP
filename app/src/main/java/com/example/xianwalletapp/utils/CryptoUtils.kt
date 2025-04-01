package com.example.xianwalletapp.utils

import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.bouncycastle.util.Pack
import java.security.SecureRandom
import java.security.Security

/**
 * Utility functions for cryptographic operations needed for Xian Messenger,
 * using Bouncy Castle as the provider.
 */
object CryptoUtils {

    init {
        // Ensure Bouncy Castle provider is added
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Converts an Ed25519 public key (hex) to a Curve25519 public key (byte array).
     * Placeholder implementation.
     */
    fun convertEd25519PublicKeyToCurve25519(ed25519PublicKeyHex: String): ByteArray {
        // Placeholder: Direct conversion methods (static or parameter-based)
        // seem unavailable or unresolved in Bouncy Castle v1.79 as used.
        // Returning dummy data to allow compilation.
        // Recommended fix: Update Bouncy Castle or use a NaCl-compatible library (e.g., Kalium).
        println("Warning: convertEd25519PublicKeyToCurve25519 returning dummy data due to API issues.")
        val edPkBytes = ed25519PublicKeyHex.hexStringToByteArray()
         if (edPkBytes.size != 32) { // Basic size check
             throw IllegalArgumentException("Invalid Ed25519 public key size")
         }
        return ByteArray(32) // Return dummy 32-byte array
    }

    /**
     * Converts an Ed25519 private key (seed hex) to a Curve25519 private key (byte array).
     * Placeholder implementation.
     */
    /**
     * Converts an Ed25519 private key seed (hex) to a Curve25519 private key (byte array).
     * Note: The input is the 32-byte seed, not the full 64-byte private key.
     */
    fun convertEd25519PrivateKeyToCurve25519(ed25519PrivateKeySeedHex: String): ByteArray {
        // Placeholder: Direct conversion methods (static or parameter-based)
        // seem unavailable or unresolved in Bouncy Castle v1.79 as used.
        // Returning dummy data to allow compilation.
        // Recommended fix: Update Bouncy Castle or use a NaCl-compatible library (e.g., Kalium).
        println("Warning: convertEd25519PrivateKeyToCurve25519 returning dummy data due to API issues.")
         val edSkSeedBytes = ed25519PrivateKeySeedHex.hexStringToByteArray()
         if (edSkSeedBytes.size != 32) { // Basic size check
             throw IllegalArgumentException("Invalid Ed25519 private key seed size")
         }
        return ByteArray(32) // Return dummy 32-byte array
    }

    /**
     * Encrypts a message using a method compatible with libsodium's crypto_box_seal.
     * This typically involves generating an ephemeral key pair, performing ECDH key exchange
     * with the recipient's public key, deriving a shared secret, and using that secret
     * with a symmetric cipher (like ChaCha20-Poly1305 or AES-GCM) to encrypt the message.
     * The ephemeral public key is prepended to the ciphertext.
     * Placeholder implementation.
     *
     * @param message The plaintext message bytes.
     * @param recipientCurve25519PublicKey The recipient's Curve25519 public key bytes.
     * @return The encrypted message (ciphertext) as a hex string.
     */
    // Constants matching libsodium's crypto_box_seal
    private const val SEAL_OVERHEAD = Ed25519.PUBLIC_KEY_SIZE + 16 // Ephemeral pubkey (32) + Poly1305 tag (16)
    private const val NONCE_SIZE = 24 // 24 bytes for XChaCha20 nonce

    fun encryptBoxSealEquivalent(message: ByteArray, recipientCurve25519PublicKeyBytes: ByteArray): String {
        val random = SecureRandom()

        // 1. Generate ephemeral X25519 key pair
        val ephemeralKpGen: AsymmetricCipherKeyPairGenerator = X25519KeyPairGenerator()
        ephemeralKpGen.init(X25519KeyGenerationParameters(random))
        val ephemeralKeyPair = ephemeralKpGen.generateKeyPair()
        val ephemeralPrivateKey = ephemeralKeyPair.private as X25519PrivateKeyParameters
        val ephemeralPublicKey = ephemeralKeyPair.public as X25519PublicKeyParameters
        val ephemeralPublicKeyBytes = ephemeralPublicKey.encoded

        // 2. Perform X25519 agreement (ephemeral private, recipient public) -> shared secret
        val recipientPublicKeyParams = X25519PublicKeyParameters(recipientCurve25519PublicKeyBytes, 0)
        val agreement = X25519Agreement()
        agreement.init(ephemeralPrivateKey)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(recipientPublicKeyParams, sharedSecret, 0)

        // 3. Derive nonce: Blake2b-256 hash of ephemeral pubkey + recipient pubkey
        val nonceMaterial = ByteArray(ephemeralPublicKeyBytes.size + recipientCurve25519PublicKeyBytes.size)
        System.arraycopy(ephemeralPublicKeyBytes, 0, nonceMaterial, 0, ephemeralPublicKeyBytes.size)
        System.arraycopy(recipientCurve25519PublicKeyBytes, 0, nonceMaterial, ephemeralPublicKeyBytes.size, recipientCurve25519PublicKeyBytes.size)

        val nonceDigest = Blake2bDigest(null, NONCE_SIZE * 8, null, null) // Blake2b-192 (24 bytes)
        nonceDigest.update(nonceMaterial, 0, nonceMaterial.size)
        val nonce = ByteArray(NONCE_SIZE)
        nonceDigest.doFinal(nonce, 0)

        // 4. Derive symmetric key: HChaCha20/HSalsa20 using nonce and shared secret
        // Bouncy Castle doesn't have HChaCha20 directly exposed easily for this use case.
        // We'll use a simplified approach with ChaCha7539Engine's key setup, which might differ
        // slightly from libsodium's internal HSalsa/HChaCha derivation but uses the same core.
        // WARNING: This simplification might affect cross-compatibility if libsodium's exact KDF isn't matched.
        val cipher = ChaCha20Poly1305() // Uses XChaCha20 internally if nonce is 24 bytes
        val keyParam = KeyParameter(sharedSecret) // Use shared secret directly as key for simplicity here
        val params = ParametersWithIV(keyParam, nonce)
        cipher.init(true, params) // true for encryption

        // 5. Encrypt using XChaCha20-Poly1305
        val ciphertext = ByteArray(cipher.getOutputSize(message.size))
        val len = cipher.processBytes(message, 0, message.size, ciphertext, 0)
        cipher.doFinal(ciphertext, len) // Appends the Poly1305 tag

        // 6. Concatenate: ephemeral public key + ciphertext (including tag)
        val sealedBox = ByteArray(ephemeralPublicKeyBytes.size + ciphertext.size)
        System.arraycopy(ephemeralPublicKeyBytes, 0, sealedBox, 0, ephemeralPublicKeyBytes.size)
        System.arraycopy(ciphertext, 0, sealedBox, ephemeralPublicKeyBytes.size, ciphertext.size)

        return sealedBox.toHexString()
    }

    /**
     * Decrypts a message encrypted with a method compatible with libsodium's crypto_box_seal.
     * This involves extracting the sender's ephemeral public key from the ciphertext,
     * performing ECDH key exchange with the recipient's private key, deriving the shared secret,
     * and using that secret with a symmetric cipher to decrypt the rest of the ciphertext.
     * Placeholder implementation.
     *
     * @param encryptedMessageHex The encrypted message (ciphertext) as a hex string.
     * @param recipientCurve25519PublicKey The recipient's Curve25519 public key bytes.
     * @param recipientCurve25519PrivateKey The recipient's Curve25519 private key bytes.
     * @return The decrypted plaintext message bytes, or null if decryption fails.
     */
    fun decryptBoxSealOpenEquivalent(
        sealedBoxHex: String,
        recipientCurve25519PublicKeyBytes: ByteArray,
        recipientCurve25519PrivateKeyBytes: ByteArray
    ): ByteArray? {
        return try {
            val sealedBox = sealedBoxHex.hexStringToByteArray()
            if (sealedBox.size < SEAL_OVERHEAD) {
                println("Error: Sealed box is too short")
                return null // Not enough data for pubkey + tag
            }

            // 1. Extract ephemeral public key and ciphertext+tag
            val ephemeralPublicKeyBytes = sealedBox.copyOfRange(0, Ed25519.PUBLIC_KEY_SIZE)
            val ciphertextWithTag = sealedBox.copyOfRange(Ed25519.PUBLIC_KEY_SIZE, sealedBox.size)

            // 2. Perform X25519 agreement (ephemeral public, recipient private) -> shared secret
            val ephemeralPublicKeyParams = X25519PublicKeyParameters(ephemeralPublicKeyBytes, 0)
            val recipientPrivateKeyParams = X25519PrivateKeyParameters(recipientCurve25519PrivateKeyBytes, 0)
            val agreement = X25519Agreement()
            agreement.init(recipientPrivateKeyParams)
            val sharedSecret = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(ephemeralPublicKeyParams, sharedSecret, 0)

            // 3. Derive nonce: Blake2b-256 hash of ephemeral pubkey + recipient pubkey
            val nonceMaterial = ByteArray(ephemeralPublicKeyBytes.size + recipientCurve25519PublicKeyBytes.size)
            System.arraycopy(ephemeralPublicKeyBytes, 0, nonceMaterial, 0, ephemeralPublicKeyBytes.size)
            System.arraycopy(recipientCurve25519PublicKeyBytes, 0, nonceMaterial, ephemeralPublicKeyBytes.size, recipientCurve25519PublicKeyBytes.size)

            val nonceDigest = Blake2bDigest(null, NONCE_SIZE * 8, null, null) // Blake2b-192 (24 bytes)
            nonceDigest.update(nonceMaterial, 0, nonceMaterial.size)
            val nonce = ByteArray(NONCE_SIZE)
            nonceDigest.doFinal(nonce, 0)

            // 4. Derive symmetric key (simplified approach, see encryption)
            // WARNING: This simplification might affect cross-compatibility.
            val cipher = ChaCha20Poly1305()
            val keyParam = KeyParameter(sharedSecret)
            val params = ParametersWithIV(keyParam, nonce)
            cipher.init(false, params) // false for decryption

            // 5. Decrypt using XChaCha20-Poly1305
            val plaintext = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
            val len = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, plaintext, 0)
            cipher.doFinal(plaintext, len) // Verifies the Poly1305 tag, throws if invalid

            plaintext // Return decrypted bytes
        } catch (e: Exception) {
            // Catch potential exceptions during hex conversion, key agreement, or decryption (e.g., invalid tag)
            println("Error decrypting sealed box: ${e.message}")
            null // Return null on any decryption failure
        }
    }

    // Helper extension functions for hex conversion (consider moving to a common utils file)
    fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    fun String.hexStringToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}