package net.xian.xianwalletapp.crypto

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import org.json.JSONArray
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
        // Log for debugging
        android.util.Log.d("XianCrypto", "Signing transaction: $transactionJson")
        
        try {
            // IMPORTANT: In xian.js, the exact byte order is critical
            // Convert the message to bytes using UTF-8 explicitly
            val messageBytes = transactionJson.toByteArray(Charsets.UTF_8)
            
            // DEBUG: Ver los primeros bytes del mensaje
            val bytesPreview = messageBytes.take(20).joinToString(", ") { it.toString() }
            android.util.Log.d("XianCrypto", "Primeros bytes del mensaje: $bytesPreview")
            
            // Debug: Ver hex del mensaje
            val messageHex = toHexString(messageBytes)
            android.util.Log.d("XianCrypto", "Mensaje en hex: ${messageHex.take(50)}...")
            
            // Combine the private and public key as done in the web version
            // In xian.js: combinedKey.set(privateKey); combinedKey.set(fromHexString(transaction.payload.sender), 32);
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
            
            // Log for debugging
            android.util.Log.d("XianCrypto", "Signature generated (hex): $signatureHex")
            android.util.Log.d("XianCrypto", "Longitud de firma: ${signatureHex.length}")
            
            return signatureHex
        } catch (e: Exception) {
            android.util.Log.e("XianCrypto", "Error signing transaction", e)
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

        // Helper to sort JSONObjects for consistent signing
        private fun sortJsonObject(jsonObj: JSONObject): JSONObject {
            val sortedJson = JSONObject()
            val keys = jsonObj.keys().asSequence().sorted().toList()
            keys.forEach { key ->
                when (val value = jsonObj.get(key)) {
                    is JSONObject -> sortedJson.put(key, sortJsonObject(value))
                    is JSONArray -> sortedJson.put(key, sortJsonArray(value))
                    else -> sortedJson.put(key, value)
                }
            }
            return sortedJson
        }

        // Helper to sort JSONArrays for consistent signing
        private fun sortJsonArray(jsonArr: JSONArray): JSONArray {
            val sortedList = mutableListOf<Any>()
            for (i in 0 until jsonArr.length()) {
                when (val item = jsonArr.get(i)) {
                    is JSONObject -> sortedList.add(sortJsonObject(item))
                    is JSONArray -> sortedList.add(sortJsonArray(item))
                    else -> sortedList.add(item)
                }
            }
            // Note: Sorting arrays might depend on specific requirements.
            // If array elements need specific sorting logic, implement it here.
            // For now, we just rebuild the array with potentially sorted nested objects.
            return JSONArray(sortedList)
        }


        /**
         * Static method to sign a transaction payload and return the full signed transaction object.
         *
         * @param payload The transaction payload as a Map.
         * @param privateKey The user's private key bytes.
         * @param publicKey The user's public key hex string.
         * @param nonce The nonce for the transaction.
         * @return The complete signed transaction as a JSONObject.
         */
        fun signTransaction(payload: Map<String, Any?>, privateKey: ByteArray, publicKey: String, nonce: Int): JSONObject {
            val xianCrypto = getInstance()
            val gson = GsonBuilder().disableHtmlEscaping().create() // Use Gson for reliable Map to JSON

            // Create JSONObject from payload Map, add nonce and sender
            val payloadJsonObj = JSONObject(gson.toJson(payload))
            payloadJsonObj.put("nonce", nonce)
            payloadJsonObj.put("sender", publicKey)

            // Sort the payload JSON for consistent signing
            val sortedPayloadJsonObj = sortJsonObject(payloadJsonObj)
            val sortedPayloadString = sortedPayloadJsonObj.toString()

            android.util.Log.d("XianCrypto", "Signing payload string: $sortedPayloadString")

            // Sign the sorted payload string
            val signatureHex = xianCrypto.signTransaction(sortedPayloadString, privateKey, publicKey)

            // Construct the final signed transaction JSONObject
            val signedTransaction = JSONObject()
            signedTransaction.put("payload", sortedPayloadJsonObj) // Use the sorted payload object
            signedTransaction.put("metadata", JSONObject().apply {
                put("signature", signatureHex)
            })

            android.util.Log.d("XianCrypto", "Final Signed TX: ${signedTransaction.toString()}")
            return signedTransaction
        }
    }
}