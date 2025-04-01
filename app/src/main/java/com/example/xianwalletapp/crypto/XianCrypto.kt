package com.example.xianwalletapp.crypto

import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility class for cryptographic operations in the Xian wallet
 * Implemented using BouncyCastle instead of libsodium
 */
class XianCrypto {
    private val secureRandom: SecureRandom
    
    init {
        // Register BouncyCastle as a security provider
        Security.addProvider(BouncyCastleProvider())
        secureRandom = SecureRandom()
    }

    /**
     * Convert a byte array to a hex string
     */
    fun toHexString(byteArray: ByteArray): String {
        return byteArray.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert a hex string to a byte array
     */
    fun fromHexString(hexString: String): ByteArray {
        return hexString.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    /**
     * Create a new key pair for the wallet
     */
    fun createKeyPair(): Pair<ByteArray, ByteArray> {
        val keyPairGenerator = Ed25519KeyPairGenerator()
        keyPairGenerator.init(Ed25519KeyGenerationParameters(secureRandom))
        val keyPair = keyPairGenerator.generateKeyPair()
        
        val privateKey = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val publicKey = (keyPair.public as Ed25519PublicKeyParameters).encoded
        
        return Pair(privateKey, publicKey)
    }

    /**
     * Create a key pair from a private key
     */
    fun createKeyPairFromSeed(privateKey: ByteArray): Pair<ByteArray, ByteArray> {
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKey, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        
        return Pair(privateKeyParams.encoded, publicKeyParams.encoded)
    }

    /**
     * Encrypt a private key with a password
     */
    fun encryptPrivateKey(privateKey: ByteArray, password: String): String {
        // Generate a random salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        
        // Generate a key from the password
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secretKey = factory.generateSecret(spec)
        val key = SecretKeySpec(secretKey.encoded, "AES")
        
        // Generate a random IV
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        
        // Encrypt the private key
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val ciphertext = cipher.doFinal(privateKey)
        
        // Return salt + iv + ciphertext as hex string
        return toHexString(salt) + toHexString(iv) + toHexString(ciphertext)
    }

    /**
     * Decrypt a private key with a password
     */
    fun decryptPrivateKey(encryptedPrivateKey: String, password: String, publicKeyHex: String): ByteArray? {
        try {
            // Extract salt, iv, and ciphertext
            val salt = fromHexString(encryptedPrivateKey.substring(0, 32)) // 16 bytes = 32 hex chars
            val iv = fromHexString(encryptedPrivateKey.substring(32, 64)) // 16 bytes = 32 hex chars
            val ciphertext = fromHexString(encryptedPrivateKey.substring(64))
            
            // Generate a key from the password
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
            val secretKey = factory.generateSecret(spec)
            val key = SecretKeySpec(secretKey.encoded, "AES")
            
            // Decrypt the private key
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(ciphertext)
            
            // Verify the private key by deriving the public key
            val keyPair = createKeyPairFromSeed(decrypted)
            if (toHexString(keyPair.second) == publicKeyHex) {
                return decrypted
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Sign a transaction with a private key
     * This implementation follows exactly the same process as in xian.js
     */
    fun signTransaction(transactionJson: String, privateKey: ByteArray, publicKey: String): String {
        // Log para depuración
        android.util.Log.d("XianCrypto", "Firmando transacción: $transactionJson")
        
        try {
            // IMPORTANTE: En xian.js, el orden exacto de los bytes es crítico
            // Convertimos el mensaje a bytes usando UTF-8 explícitamente
            val messageBytes = transactionJson.toByteArray(Charsets.UTF_8)
            
            // DEBUG: Ver los primeros bytes del mensaje
            val bytesPreview = messageBytes.take(20).joinToString(", ") { it.toString() }
            android.util.Log.d("XianCrypto", "Primeros bytes del mensaje: $bytesPreview")
            
            // Debug: Ver hex del mensaje
            val messageHex = toHexString(messageBytes)
            android.util.Log.d("XianCrypto", "Mensaje en hex: ${messageHex.take(50)}...")
            
            // Combinar la clave privada y pública como se hace en la versión web
            // En xian.js: combinedKey.set(privateKey); combinedKey.set(fromHexString(transaction.payload.sender), 32);
            val combinedKey = ByteArray(64)
            System.arraycopy(privateKey, 0, combinedKey, 0, 32)
            System.arraycopy(fromHexString(publicKey), 0, combinedKey, 32, 32)
            
            // Crear un signer Ed25519 nuevo 
            val signer = Ed25519Signer()
            val privateKeyParams = Ed25519PrivateKeyParameters(combinedKey, 0)
            signer.init(true, privateKeyParams)
            signer.update(messageBytes, 0, messageBytes.size)
            val signature = signer.generateSignature()
            
            // Convertir a formato hexadecimal
            val signatureHex = toHexString(signature)
            
            // Log para depuración
            android.util.Log.d("XianCrypto", "Firma generada (hex): $signatureHex")
            android.util.Log.d("XianCrypto", "Longitud de firma: ${signatureHex.length}")
            
            return signatureHex
        } catch (e: Exception) {
            android.util.Log.e("XianCrypto", "Error al firmar transacción", e)
            throw e
        }
    }

    /**
     * Sign a message with a private key (overloaded version that doesn't require publicKey)
     */
    fun signMessage(message: ByteArray, privateKey: ByteArray): String {
        val signer = Ed25519Signer()
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKey, 0)
        
        signer.init(true, privateKeyParams)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()
        
        return toHexString(signature)
    }

    companion object {
        // Singleton instance
        @Volatile
        private var instance: XianCrypto? = null
        
        fun getInstance(): XianCrypto {
            return instance ?: synchronized(this) {
                instance ?: XianCrypto().also { instance = it }
            }
        }
        
        /**
         * Static method to sign a transaction with a private key and nonce
         * Used for direct access from other classes
         */
        fun signTransaction(transactionJson: String, privateKey: ByteArray, publicKey: String, nonce: Int): String {
            // Add nonce to the transaction payload
            // This is just a simple implementation - you might need to adjust it based on your exact requirements
            val xianCrypto = getInstance()
            
            // For simplicity, we assume the transactionJson is a valid JSON string
            // In a real implementation, you might want to add more validation and proper JSON handling
            val transactionWithNonce = transactionJson.trimEnd('}') + 
                ", \"nonce\": $nonce, \"sender\": \"$publicKey\"}"
            
            // Sign the transaction
            return xianCrypto.signTransaction(transactionWithNonce, privateKey, publicKey)
        }
    }
}