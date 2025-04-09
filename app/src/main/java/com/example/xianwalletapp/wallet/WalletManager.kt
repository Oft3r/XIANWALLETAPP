package com.example.xianwalletapp.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import com.example.xianwalletapp.crypto.XianCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager class for wallet operations
 * Handles wallet creation, import, and secure storage
 */
class WalletManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val crypto = XianCrypto.getInstance()
    private val prefs: SharedPreferences
    private var cachedPrivateKey: ByteArray? = null // Variable to cache the decrypted key

    // Keys for SharedPreferences
    companion object {
        private const val WALLET_PREFS = "xian_wallet_prefs"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_ENCRYPTED_PRIVATE_KEY = "encrypted_private_key"
        private const val KEY_TOKEN_LIST = "token_list"
        private const val KEY_RPC_URL = "rpc_url"
        private const val KEY_EXPLORER_URL = "explorer_url"
        private const val KEY_REQUIRE_PASSWORD = "require_password"
        private const val KEY_PREFERRED_NFT_CONTRACT = "preferred_nft_contract" // Correctly placed constant
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY = "biometric_encrypted_private_key" // Renamed
        private const val KEY_BIOMETRIC_PRIVATE_KEY_IV = "biometric_private_key_iv" // Renamed
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "xian_biometric_key"

        // Default token
        private const val DEFAULT_TOKEN = "currency"

        // Default URLs
        private const val DEFAULT_RPC_URL = "https://node.xian.org"
        private const val DEFAULT_EXPLORER_URL = "https://explorer.xian.org"

        @Volatile
        private var instance: WalletManager? = null

        fun getInstance(context: Context): WalletManager {
            return instance ?: synchronized(this) {
                instance ?: WalletManager(context).also { instance = it }
            }
        }
    }

    init {
        // Create or retrieve the master key for encryption
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Initialize encrypted SharedPreferences
        prefs = EncryptedSharedPreferences.create(
            appContext,
            WALLET_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Check if a wallet exists
     */
    fun hasWallet(): Boolean {
        return prefs.contains(KEY_PUBLIC_KEY) && prefs.contains(KEY_ENCRYPTED_PRIVATE_KEY)
    }

    /**
     * Create a new wallet
     */
    fun createWallet(password: String): WalletCreationResult {
        try {
            // Generate a new key pair
            val keyPair = crypto.createKeyPair()

            // Convert keys to format needed for storage
            val publicKey = crypto.toHexString(keyPair.second) // public key
            val privateKeyBytes = keyPair.first // private key

            // Encrypt the private key
            val encryptedPrivateKey = crypto.encryptPrivateKey(privateKeyBytes, password)

            // Store the keys
            prefs.edit()
                .putString(KEY_PUBLIC_KEY, publicKey)
                .putString(KEY_ENCRYPTED_PRIVATE_KEY, encryptedPrivateKey)
                .apply()

            // Initialize token list with default token
            if (!prefs.contains(KEY_TOKEN_LIST)) {
                prefs.edit().putStringSet(KEY_TOKEN_LIST, setOf(DEFAULT_TOKEN)).apply()
            }

            return WalletCreationResult(success = true, publicKey = publicKey)
        } catch (e: Exception) {
            return WalletCreationResult(success = false, error = e.message)
        }
    }

    /**
     * Import an existing wallet
     */
    fun importWallet(privateKeyHex: String, password: String): WalletCreationResult {
        try {
            // Convert hex private key to bytes
            val privateKeyBytes = crypto.fromHexString(privateKeyHex)

            // Create key pair from private key
            val keyPair = crypto.createKeyPairFromSeed(privateKeyBytes)

            // Convert public key to hex
            val publicKey = crypto.toHexString(keyPair.second) // public key

            // Encrypt the private key
            val encryptedPrivateKey = crypto.encryptPrivateKey(privateKeyBytes, password)

            // Store the keys
            prefs.edit()
                .putString(KEY_PUBLIC_KEY, publicKey)
                .putString(KEY_ENCRYPTED_PRIVATE_KEY, encryptedPrivateKey)
                .apply()

            // Initialize token list with default token
            if (!prefs.contains(KEY_TOKEN_LIST)) {
                prefs.edit().putStringSet(KEY_TOKEN_LIST, setOf(DEFAULT_TOKEN)).apply()
            }

            return WalletCreationResult(success = true, publicKey = publicKey)
        } catch (e: Exception) {
            return WalletCreationResult(success = false, error = e.message)
        }
    }

    /**
     * Get the public key
     */
    fun getPublicKey(): String? {
        return prefs.getString(KEY_PUBLIC_KEY, null)
    }

    /**
     * Unlock the wallet and get the private key
     */
    fun unlockWallet(password: String): ByteArray? {
        val publicKey = prefs.getString(KEY_PUBLIC_KEY, null) ?: return null
        val encryptedPrivateKey = prefs.getString(KEY_ENCRYPTED_PRIVATE_KEY, null) ?: return null

        val decryptedKey = crypto.decryptPrivateKey(encryptedPrivateKey, password, publicKey)
        if (decryptedKey != null) {
            // Cache the key on successful unlock
            cachedPrivateKey = decryptedKey
            android.util.Log.d("WalletManager", "Private key unlocked and cached.")
        } else {
            android.util.Log.w("WalletManager", "Failed to unlock private key.")
        }
        return decryptedKey
    }

    /**
     * Get the private key using the password
     * This is an alias for unlockWallet to maintain compatibility
     */
    fun getPrivateKey(password: String): ByteArray? {
        return unlockWallet(password)
    }

    /**
     * Get the list of tokens
     */
    fun getTokenList(): Set<String> {
        return prefs.getStringSet(KEY_TOKEN_LIST, setOf(DEFAULT_TOKEN)) ?: setOf(DEFAULT_TOKEN)
    }

    /**
     * Add a token to the list
     */
    fun addToken(contract: String): Boolean {
        if (contract.isBlank()) return false

        val currentTokens = getTokenList().toMutableSet()
        if (currentTokens.add(contract)) {
            prefs.edit().putStringSet(KEY_TOKEN_LIST, currentTokens).apply()
            return true
        }
        return false
    }

    /**
     * Remove a token from the list
     */
    fun removeToken(contract: String): Boolean {
        if (contract == DEFAULT_TOKEN) return false // Cannot remove default token

        val currentTokens = getTokenList().toMutableSet()
        if (currentTokens.remove(contract)) {
            prefs.edit().putStringSet(KEY_TOKEN_LIST, currentTokens).apply()
            return true
        }
        return false
    }

    /**
     * Get the RPC URL
     */
    fun getRpcUrl(): String {
        return prefs.getString(KEY_RPC_URL, DEFAULT_RPC_URL) ?: DEFAULT_RPC_URL
    }

    /**
     * Set the RPC URL
     */
    fun setRpcUrl(url: String) {
        prefs.edit().putString(KEY_RPC_URL, url).apply()
    }

    /**
     * Get the explorer URL
     */
    fun getExplorerUrl(): String {
        return prefs.getString(KEY_EXPLORER_URL, DEFAULT_EXPLORER_URL) ?: DEFAULT_EXPLORER_URL
    }

    /**
     * Set the explorer URL
     */
    fun setExplorerUrl(url: String) {
        prefs.edit().putString(KEY_EXPLORER_URL, url).apply()
    }

    /**
     * Get the default RPC URL
     */
    fun getDefaultRpcUrl(): String {
        return DEFAULT_RPC_URL
    }

    /**
     * Get the default explorer URL
     */
    fun getDefaultExplorerUrl(): String {
        return DEFAULT_EXPLORER_URL
    }

    /**
     * Delete the wallet
     */
    fun deleteWallet() {
        prefs.edit().clear().apply()
    }

    /**
     * Get the cached private key if available (doesn't require password)
     */
    fun getUnlockedPrivateKey(): ByteArray? {
        android.util.Log.d("WalletManager", "getUnlockedPrivateKey called. Cached key is ${if (cachedPrivateKey != null) "available" else "null"}.")
        return cachedPrivateKey
    }

    /**
     * Clear the cached private key from memory
     */
    fun clearPrivateKeyCache() {
        cachedPrivateKey = null
        android.util.Log.d("WalletManager", "Private key cache cleared.")
    }


    /**
     * Get require password on startup setting
     */
     // Note: Corrected the duplicate return statement here
    fun getRequirePassword(): Boolean {
        val value = prefs.getBoolean(KEY_REQUIRE_PASSWORD, false)
        android.util.Log.d("WalletManager", "getRequirePassword read value: $value") // Add logging
        return value
    }

    /**
     * Set require password on startup setting
     */
     // Note: Corrected the duplicate apply call here
    fun setRequirePassword(enabled: Boolean) {
        android.util.Log.d("WalletManager", "setRequirePassword saving value: $enabled") // Add logging
        prefs.edit().putBoolean(KEY_REQUIRE_PASSWORD, enabled).apply()
    }


    /**
     * Check if biometric unlock is enabled
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Set biometric unlock preference
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    // --- Biometric Keystore Methods ---

    /**
     * Generates the biometric key if needed and returns a Cipher for encryption.
     * This cipher requires biometric authentication before it can be used successfully.
     */
    fun prepareBiometricEncryption(): Cipher? {
        android.util.Log.d("WalletManager", "Preparing biometric encryption cipher.")
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Generate key if it doesn't exist
            if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    BIOMETRIC_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    // Optional: Set auth validity duration if needed, e.g., 10 seconds
                    // .setUserAuthenticationValidityDurationSeconds(10)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                android.util.Log.d("WalletManager", "Generated new biometric key for encryption preparation.")
            } else {
                 android.util.Log.d("WalletManager", "Biometric key already exists for encryption preparation.")
            }

            val secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey) // Initialize for encryption
            android.util.Log.d("WalletManager", "Successfully initialized biometric cipher for encryption.")
            return cipher
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Error preparing biometric encryption cipher (${e.javaClass.simpleName})", e)
            return null
        }
    }

     /**
      * Finalizes enabling biometrics after password verification and successful biometric auth for encryption.
      * Encrypts the private key with the authorized cipher and stores it.
      */
    fun finalizeBiometricEnable(password: String, cipher: Cipher): Boolean {
         android.util.Log.d("WalletManager", "Finalizing biometric enable.")
         // 1. Verify password again to get private key bytes (ensure it wasn't tampered with)
         val decryptedPrivateKeyBytes = unlockWallet(password)
         if (decryptedPrivateKeyBytes == null) {
             android.util.Log.e("WalletManager", "Finalize Biometric: Invalid password provided during finalization.")
             clearPrivateKeyCache() // Clear cache even on failure here
             return false
         }
         clearPrivateKeyCache() // Clear cache immediately after getting bytes

         try {
             // 2. Encrypt the private key using the BIOMETRIC-AUTHORIZED cipher
             val iv = cipher.iv // Get IV from the cipher used in the prompt
             val encryptedPrivateKeyBytes = cipher.doFinal(decryptedPrivateKeyBytes)
             android.util.Log.d("WalletManager", "Encrypted private key with AUTHORIZED biometric cipher.")

             // 3. Store the biometric-encrypted private key and its IV
             prefs.edit()
                 .putString(KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY, Base64.encodeToString(encryptedPrivateKeyBytes, Base64.DEFAULT))
                 .putString(KEY_BIOMETRIC_PRIVATE_KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                 .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                 .apply()

             android.util.Log.d("WalletManager", "Biometric unlock finalized and enabled.")
             return true
         } catch (e: Exception) {
             // This could include IllegalBlockSizeException if auth timed out between prompt and this call
             android.util.Log.e("WalletManager", "Error finalizing biometric enable (${e.javaClass.simpleName})", e)
             // Attempt to clean up
             disableBiometric()
             return false
         }
    }


    /**
     * Disables biometric unlock by removing the encrypted key data and the Keystore key.
     */
    fun disableBiometric() {
        try {
            prefs.edit()
                .remove(KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY) // Remove biometric key data
                .remove(KEY_BIOMETRIC_PRIVATE_KEY_IV)
                .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                .apply()

            // Delete the Keystore key
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                android.util.Log.d("WalletManager", "Biometric Keystore key deleted.")
            }
            android.util.Log.d("WalletManager", "Biometric unlock disabled.")
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Error disabling biometric unlock", e)
            // Still set flag to false even if key deletion fails
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply()
            throw e // Re-throw for the UI to handle
        }
    }

    /**
     * Gets a Cipher instance initialized for decryption using the biometric-bound key.
     * Returns null if the key or IV is not found.
     */
    fun getBiometricCipherForDecryption(): Cipher? {
        android.util.Log.d("WalletManager", "Attempting to get biometric cipher for decryption.")
        try {
            // Retrieve the IV used for the biometric encryption
            val ivString = prefs.getString(KEY_BIOMETRIC_PRIVATE_KEY_IV, null)
            if (ivString == null) {
                android.util.Log.e("WalletManager", "Biometric IV not found in prefs.")
                return null
            }
            val iv = Base64.decode(ivString, Base64.DEFAULT)

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Get the secret key bound to biometrics
            val secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            if (secretKey == null) {
                 android.util.Log.e("WalletManager", "Biometric key alias '$BIOMETRIC_KEY_ALIAS' not found in Keystore.")
                 // Attempt to clean up inconsistent state
                 disableBiometric()
                 return null
            }

            // Initialize the cipher for DECRYPTION
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            android.util.Log.d("WalletManager", "Successfully initialized biometric cipher for decryption.")
            return cipher
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Error getting biometric cipher (${e.javaClass.simpleName})", e)
            return null
        }
    }

    /**
     * Unlocks the wallet using a Cipher obtained after successful biometric authentication.
     */
    fun unlockWalletWithBiometricCipher(cipher: Cipher): Boolean {
         android.util.Log.d("WalletManager", "Attempting to unlock wallet with biometric cipher.")
        try {
            // Retrieve the biometric-encrypted PRIVATE KEY
            val encryptedPrivateKeyString = prefs.getString(KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY, null)
            if (encryptedPrivateKeyString == null) {
                 android.util.Log.e("WalletManager", "Biometric encrypted private key not found in prefs.")
                 return false
            }
            val encryptedPrivateKeyBytes = Base64.decode(encryptedPrivateKeyString, Base64.DEFAULT)

            // Decrypt the private key using the cipher from successful biometric auth
            val decryptedPrivateKeyBytes = cipher.doFinal(encryptedPrivateKeyBytes)

            // Cache the decrypted private key
            cachedPrivateKey = decryptedPrivateKeyBytes
            android.util.Log.d("WalletManager", "Private key unlocked and cached via biometrics.")
            return true // Successfully decrypted and cached
        } catch (e: Exception) {
            // This could be UserNotAuthenticatedException if auth timed out, or other crypto errors
            android.util.Log.e("WalletManager", "Error unlocking wallet with biometric cipher (${e.javaClass.simpleName})", e)
            return false
        }
    }


    /**
     * Set the preferred NFT contract address to display in the header
     */
    fun setPreferredNftContract(contract: String?) {
        prefs.edit().putString(KEY_PREFERRED_NFT_CONTRACT, contract).apply() // Now correctly references prefs and KEY_PREFERRED_NFT_CONTRACT
        android.util.Log.d("WalletManager", "Preferred NFT contract set to: $contract")
    }

    /**
     * Get the preferred NFT contract address
     */
    fun getPreferredNftContract(): String? {
        val contract = prefs.getString(KEY_PREFERRED_NFT_CONTRACT, null) // Now correctly references prefs and KEY_PREFERRED_NFT_CONTRACT
        android.util.Log.d("WalletManager", "Retrieved preferred NFT contract: $contract")
        return contract
    }

} // End of WalletManager class

/**
 * Result class for wallet creation/import operations
 */
data class WalletCreationResult(
    val success: Boolean,
    val publicKey: String? = null,
    val error: String? = null
)