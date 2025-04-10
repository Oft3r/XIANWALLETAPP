package com.example.xianwalletapp.wallet

import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.json.JSONException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import com.example.xianwalletapp.crypto.XianCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // StateFlow to hold the currently active wallet's public key
    private val _activeWalletPublicKeyFlow = MutableStateFlow<String?>(null)
    val activeWalletPublicKeyFlow: StateFlow<String?> = _activeWalletPublicKeyFlow.asStateFlow()

    // Keys for SharedPreferences
    companion object {
        private const val WALLET_PREFS = "xian_wallet_prefs"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_ENCRYPTED_PRIVATE_KEY = "encrypted_private_key"
        private const val KEY_TOKEN_LIST = "token_list"
        private const val KEY_WALLET_LIST = "wallet_list" // Set of public keys
        private const val KEY_ACTIVE_WALLET_PUBLIC_KEY = "active_wallet_public_key"
        private const val KEY_RPC_URL = "rpc_url"
        private const val KEY_EXPLORER_URL = "explorer_url"
        private const val KEY_REQUIRE_PASSWORD = "require_password"
        private const val KEY_PREFERRED_NFT_CONTRACT = "preferred_nft_contract" // Base key for per-wallet pref
        private const val KEY_WALLET_NAMES = "wallet_names_map" // JSON string map: publicKey -> name
        // Base keys for per-wallet biometric preferences
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY = "biometric_encrypted_private_key"
        private const val KEY_BIOMETRIC_PRIVATE_KEY_IV = "biometric_private_key_iv"
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
        // Initialize the StateFlow with the stored active key or the first available key
        _activeWalletPublicKeyFlow.value = readActiveWalletPublicKeyFromPrefs()
    }

    // Helper function to create wallet-specific preference keys

    // --- Wallet Name Management --- 

    private fun loadWalletNames(): MutableMap<String, String> {
        val jsonString = prefs.getString(KEY_WALLET_NAMES, null)
        val map = mutableMapOf<String, String>()
        if (jsonString != null) {
            try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.keys().forEach { key ->
                    map[key] = jsonObject.getString(key)
                }
            } catch (e: JSONException) {
                Log.e("WalletManager", "Error parsing wallet names JSON", e)
                // Handle error, maybe clear corrupted data?
            }
        }
        return map
    }

    private fun saveWalletNames(names: Map<String, String>) {
        try {
            val jsonObject = JSONObject()
            names.forEach { (key, value) ->
                jsonObject.put(key, value)
            }
            prefs.edit().putString(KEY_WALLET_NAMES, jsonObject.toString()).apply()
        } catch (e: JSONException) {
            Log.e("WalletManager", "Error creating wallet names JSON", e)
        }
    }

    private fun getWalletPrefKey(publicKey: String, baseKey: String): String {
        return "wallet_${publicKey}_${baseKey}"
    }


    /**
     * Check if any wallet exists
     */
    fun hasWallet(): Boolean {
        return getWalletPublicKeys().isNotEmpty()
    }

    /**
     * Create a new wallet and set it as active
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
            assignDefaultWalletName(publicKey) // Assign name

            // Store the encrypted key specific to this wallet
            prefs.edit()
                .putString(getWalletPrefKey(publicKey, KEY_ENCRYPTED_PRIVATE_KEY), encryptedPrivateKey)
                .apply()

            // Add wallet to the list and set as active
            addWalletToList(publicKey)
            setActiveWallet(publicKey) // This updates the flow and prefs

            // Initialize token list for this wallet (if needed, or manage globally)
            // For now, let's assume token list is global or handled elsewhere per wallet
            // if (!prefs.contains(getWalletPrefKey(publicKey, KEY_TOKEN_LIST))) {
            //     prefs.edit().putStringSet(getWalletPrefKey(publicKey, KEY_TOKEN_LIST), setOf(DEFAULT_TOKEN)).apply()
            // }

            return WalletCreationResult(success = true, publicKey = publicKey)
        } catch (e: Exception) {
            return WalletCreationResult(success = false, error = e.message)
        }
    }

    /**
     * Import an existing wallet and set it as active
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

            // Store the encrypted key specific to this wallet
            prefs.edit()
                .putString(getWalletPrefKey(publicKey, KEY_ENCRYPTED_PRIVATE_KEY), encryptedPrivateKey)
                .apply()

            // Add wallet to the list and set as active
            addWalletToList(publicKey)
            setActiveWallet(publicKey) // This updates the flow and prefs
            assignDefaultWalletName(publicKey) // Assign name

            // Initialize token list for this wallet (if needed)
            // See comment in createWallet

            return WalletCreationResult(success = true, publicKey = publicKey)
        } catch (e: Exception) {
            return WalletCreationResult(success = false, error = e.message)
        }
    } // End of importWallet function

    /** Assigns a default name like "My Wallet X" to a newly added wallet */
    private fun assignDefaultWalletName(publicKey: String) {
        val names = loadWalletNames()
        if (!names.containsKey(publicKey)) { // Only assign if no name exists
            val existingCount = names.size // How many wallets already have names
            val defaultName = if (existingCount == 0) "My Wallet" else "My Wallet ${existingCount + 1}"
            names[publicKey] = defaultName
            saveWalletNames(names)
            Log.d("WalletManager", "Assigned default name '$defaultName' to wallet $publicKey")
        }
    }


    /**
     * Get the public key of the currently active wallet
     */
    fun getPublicKey(): String? {
        return getActiveWalletPublicKey() // Use the new function
    }

    /**
     * Unlock the *active* wallet and get the private key
     */

    /** Gets the stored name for a specific wallet public key */
    fun getWalletName(publicKey: String): String? {
        val names = loadWalletNames()
        return names[publicKey]
    }

    /** Gets the name of the currently active wallet */
    fun getActiveWalletName(): String? {
        val activeKey = getActiveWalletPublicKey()
        return if (activeKey != null) {
            getWalletName(activeKey)
        } else {
            null
        }
    }


    /** Renames a specific wallet */
    fun renameWallet(publicKey: String, newName: String): Boolean {
        if (newName.isBlank()) {
            Log.w("WalletManager", "Attempted to rename wallet $publicKey to an empty name.")
            return false // Prevent blank names
        }

        // First, check if the public key belongs to a known wallet
        if (!getWalletPublicKeys().contains(publicKey)) {
             Log.w("WalletManager", "Attempted to rename a wallet with an unknown public key: $publicKey")
             return false // Wallet key itself is not registered
        }

        // Load the current names
        val names = loadWalletNames()

        // Update or add the name in the map
        names[publicKey] = newName.trim() // Update the name, trim whitespace
        saveWalletNames(names) // Save the updated map

        Log.d("WalletManager", "Set name for wallet $publicKey to '$newName'")
        // Optionally, if the renamed wallet is the active one, update the name flow?
        // This might require adding a name flow or triggering a refresh.
        // For now, the name will update next time getActiveWalletName() is called.
        return true
    }

    fun unlockWallet(password: String): ByteArray? {
        val publicKey = getActiveWalletPublicKey() ?: return null // Get active public key
        val encryptedPrivateKey = prefs.getString(getWalletPrefKey(publicKey, KEY_ENCRYPTED_PRIVATE_KEY), null) ?: return null // Get specific encrypted key

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
     * Get the private key of the *active* wallet using the password
     * This is an alias for unlockWallet
     */
    fun getPrivateKey(password: String): ByteArray? {
        return unlockWallet(password)
    }

    /**
     * Get the list of tokens for the *active* wallet
     */
    fun getTokenList(): Set<String> {
        val publicKey = getActiveWalletPublicKey() ?: return setOf(DEFAULT_TOKEN) // Need active key
        return prefs.getStringSet(getWalletPrefKey(publicKey, KEY_TOKEN_LIST), setOf(DEFAULT_TOKEN)) ?: setOf(DEFAULT_TOKEN)
    }

    /**
     * Add a token to the list for the *active* wallet
     */
    fun addToken(contract: String): Boolean {
        if (contract.isBlank()) return false
        val publicKey = getActiveWalletPublicKey() ?: return false // Need active key

        val tokenPrefKey = getWalletPrefKey(publicKey, KEY_TOKEN_LIST)
        val currentTokens = (prefs.getStringSet(tokenPrefKey, setOf(DEFAULT_TOKEN)) ?: setOf(DEFAULT_TOKEN)).toMutableSet()

        if (currentTokens.add(contract)) {
            prefs.edit().putStringSet(tokenPrefKey, currentTokens).apply()
            return true
        }
        return false
    }

    /**
     * Remove a token from the list for the *active* wallet
     */
    fun removeToken(contract: String): Boolean {
        if (contract == DEFAULT_TOKEN) return false // Cannot remove default token
        val publicKey = getActiveWalletPublicKey() ?: return false // Need active key

        val tokenPrefKey = getWalletPrefKey(publicKey, KEY_TOKEN_LIST)
        val currentTokens = (prefs.getStringSet(tokenPrefKey, setOf(DEFAULT_TOKEN)) ?: setOf(DEFAULT_TOKEN)).toMutableSet()

        if (currentTokens.remove(contract)) {
            prefs.edit().putStringSet(tokenPrefKey, currentTokens).apply()
            return true
        }
        return false
    }

    /**
     * Get the RPC URL for the *active* wallet
     */
    fun getRpcUrl(): String {
        val publicKey = getActiveWalletPublicKey() ?: return DEFAULT_RPC_URL // Fallback to default if no active wallet
        return prefs.getString(getWalletPrefKey(publicKey, KEY_RPC_URL), DEFAULT_RPC_URL) ?: DEFAULT_RPC_URL
    }

    /**
     * Set the RPC URL for the *active* wallet
     */
    fun setRpcUrl(url: String) {
        val publicKey = getActiveWalletPublicKey() ?: return // Cannot set if no active wallet
        prefs.edit().putString(getWalletPrefKey(publicKey, KEY_RPC_URL), url).apply()
    }

    /**
     * Get the explorer URL for the *active* wallet
     */
    fun getExplorerUrl(): String {
        val publicKey = getActiveWalletPublicKey() ?: return DEFAULT_EXPLORER_URL // Fallback
        return prefs.getString(getWalletPrefKey(publicKey, KEY_EXPLORER_URL), DEFAULT_EXPLORER_URL) ?: DEFAULT_EXPLORER_URL
    }

    /**
     * Set the explorer URL for the *active* wallet
     */
    fun setExplorerUrl(url: String) {
        val publicKey = getActiveWalletPublicKey() ?: return // Cannot set if no active wallet
        prefs.edit().putString(getWalletPrefKey(publicKey, KEY_EXPLORER_URL), url).apply()
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
     * Delete *all* wallets (Legacy - Keep for potential full reset, but use deleteWallet(publicKey) for specific deletion)
     * WARNING: This clears ALL wallet data and settings.
     */
    fun deleteAllWallets() {
        // Consider clearing biometric keys too if applicable
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            // Attempt to delete the single shared biometric key alias
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                android.util.Log.d("WalletManager", "Deleted shared biometric key during deleteAllWallets.")
            }
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Error clearing biometric keys during deleteAllWallets", e)
        }
        prefs.edit().clear().apply()
        cachedPrivateKey = null // Clear cache
        _activeWalletPublicKeyFlow.value = null // Update the flow
        saveWalletNames(emptyMap()) // Clear the names map as well
        android.util.Log.w("WalletManager", "Deleted all wallets and cleared preferences.")
    }

    /**
     * Delete a specific wallet by its public key
     */
    fun deleteWallet(publicKeyToDelete: String): Boolean {
        val currentKeys = getWalletPublicKeys().toMutableSet()
        if (!currentKeys.contains(publicKeyToDelete)) {
            android.util.Log.w("WalletManager", "Attempted to delete non-existent wallet: $publicKeyToDelete")
            return false // Wallet doesn't exist
        }

        // Remove wallet-specific data
        val editor = prefs.edit()
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_ENCRYPTED_PRIVATE_KEY))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_TOKEN_LIST))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_PREFERRED_NFT_CONTRACT))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_REQUIRE_PASSWORD))
        // Remove biometric data for this wallet (already using getWalletPrefKey)
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_BIOMETRIC_ENABLED))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_BIOMETRIC_PRIVATE_KEY_IV))
        // Remove per-wallet network settings
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_RPC_URL))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_EXPLORER_URL))
        // Remove other per-wallet settings
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_PREFERRED_NFT_CONTRACT))
        editor.remove(getWalletPrefKey(publicKeyToDelete, KEY_REQUIRE_PASSWORD))

        // Remove the wallet from the list
        currentKeys.remove(publicKeyToDelete)
        editor.putStringSet(KEY_WALLET_LIST, currentKeys)

        // Remove the name from the names map
        val names = loadWalletNames()
        if (names.remove(publicKeyToDelete) != null) {
            saveWalletNames(names)
        }


        // Handle active wallet change
        val currentActiveKey = getActiveWalletPublicKey()
        if (currentActiveKey == publicKeyToDelete) {
            clearPrivateKeyCache() // Clear cache if the active wallet is deleted
            if (currentKeys.isNotEmpty()) {
                // Set the first remaining wallet as active
                val newActiveKey = currentKeys.first()
                // Set the new active key using the method that updates the flow and prefs
                // No need to edit prefs here, setActiveWallet handles it
                setActiveWallet(newActiveKey)
                android.util.Log.d("WalletManager", "Deleted active wallet. New active wallet set to: $newActiveKey")
            } else {
                // No wallets left, remove active key preference and update flow
                editor.remove(KEY_ACTIVE_WALLET_PUBLIC_KEY)
                _activeWalletPublicKeyFlow.value = null // Update the flow
                android.util.Log.d("WalletManager", "Deleted the last wallet.")
            }
        }

        // We don't delete the shared BIOMETRIC_KEY_ALIAS here.
        // It might be deleted in deleteAllWallets or if no wallet uses biometrics anymore.

        editor.apply() // Apply all changes
        android.util.Log.d("WalletManager", "Deleted wallet: $publicKeyToDelete")
        return true
    }

    // Removed getBiometricKeyAlias helper function as we use a single shared alias

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
     * Get require password on startup setting for the *active* wallet
     */
    fun getRequirePassword(): Boolean {
        val publicKey = getActiveWalletPublicKey() ?: return false // Default to false if no active wallet
        val value = prefs.getBoolean(getWalletPrefKey(publicKey, KEY_REQUIRE_PASSWORD), false)
        android.util.Log.d("WalletManager", "getRequirePassword for $publicKey read value: $value")
        return value
    }

    /**
     * Set require password on startup setting for the *active* wallet
     */
    fun setRequirePassword(enabled: Boolean) {
        val publicKey = getActiveWalletPublicKey() ?: return // Cannot set if no active wallet
        android.util.Log.d("WalletManager", "setRequirePassword for $publicKey saving value: $enabled")
        prefs.edit().putBoolean(getWalletPrefKey(publicKey, KEY_REQUIRE_PASSWORD), enabled).apply()
    }


    /**
     * Check if biometric unlock is enabled for the *active* wallet
     */
    fun isBiometricEnabled(): Boolean {
        val publicKey = getActiveWalletPublicKey() ?: return false
        return prefs.getBoolean(getWalletPrefKey(publicKey, KEY_BIOMETRIC_ENABLED), false)
    }

    /**
     * Set biometric unlock preference (This function might be less useful now,
     * as enabling is handled by finalizeBiometricEnable and disabling by disableBiometric.
     * Kept for potential direct manipulation if needed, acts on the active wallet).
     */
    // fun setBiometricEnabled(enabled: Boolean) { // Consider removing or clarifying purpose
    //     val publicKey = getActiveWalletPublicKey() ?: return
    //     prefs.edit().putBoolean(getWalletPrefKey(publicKey, KEY_BIOMETRIC_ENABLED), enabled).apply()
    // }

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
      * Finalizes enabling biometrics for the *active* wallet after password verification
      * and successful biometric auth for encryption. Encrypts the active wallet's
      * private key with the authorized cipher and stores it along with the IV.
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

             // 3. Store the biometric-encrypted private key, its IV, and enabled status for the ACTIVE wallet
             val activePublicKey = getActiveWalletPublicKey() ?: run {
                 android.util.Log.e("WalletManager", "Finalize Biometric: No active wallet found.")
                 return false // Should not happen if unlockWallet worked, but safety check
             }
             prefs.edit()
                 .putString(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY), Base64.encodeToString(encryptedPrivateKeyBytes, Base64.DEFAULT))
                 .putString(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_PRIVATE_KEY_IV), Base64.encodeToString(iv, Base64.DEFAULT))
                 .putBoolean(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_ENABLED), true)
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
     * Disables biometric unlock for the *active* wallet by removing its encrypted key data.
     * Does NOT remove the shared Keystore key.
     */
    fun disableBiometric() {
        val activePublicKey = getActiveWalletPublicKey() ?: run {
            android.util.Log.w("WalletManager", "Disable Biometric: No active wallet.")
            return // Nothing to disable
        }
        try {
            prefs.edit()
                .remove(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY))
                .remove(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_PRIVATE_KEY_IV))
                .putBoolean(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_ENABLED), false) // Explicitly set to false
                .apply()

            // We DO NOT delete the shared Keystore key (BIOMETRIC_KEY_ALIAS) here,
            // as other wallets might still be using it.
            // Keystore key deletion could be handled if ALL wallets have biometrics disabled,
            // or during deleteAllWallets.

            android.util.Log.d("WalletManager", "Biometric unlock disabled for wallet: $activePublicKey")
        } catch (e: Exception) {
            android.util.Log.e("WalletManager", "Error disabling biometric unlock for wallet $activePublicKey", e)
            // Ensure flag is false even if other removals failed
            prefs.edit().putBoolean(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_ENABLED), false).apply()
            // Decide whether to re-throw or just log
        }
    }

    /**
     * Gets a Cipher instance initialized for decryption using the shared biometric-bound key.
     * Requires the IV specific to the *active* wallet.
     * Returns null if the key or the active wallet's IV is not found.
     */
    fun getBiometricCipherForDecryption(): Cipher? {
        android.util.Log.d("WalletManager", "Attempting to get biometric cipher for decryption.")
        val activePublicKey = getActiveWalletPublicKey() ?: run {
             android.util.Log.w("WalletManager", "GetBiometricCipher: No active wallet.")
             return null
        }
        try {
            // Retrieve the IV specific to the active wallet
            val ivString = prefs.getString(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_PRIVATE_KEY_IV), null)
            if (ivString == null) {
                android.util.Log.e("WalletManager", "Biometric IV not found for active wallet: $activePublicKey")
                return null
            }
            val iv = Base64.decode(ivString, Base64.DEFAULT)

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Get the secret key bound to biometrics
            val secretKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            if (secretKey == null) {
                 android.util.Log.e("WalletManager", "Shared biometric key alias '$BIOMETRIC_KEY_ALIAS' not found in Keystore.")
                 // Don't disable here, as the key is shared. Log the error.
                 // Consider adding a recovery mechanism if needed.
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
     * Unlocks the *active* wallet using a Cipher obtained after successful biometric authentication.
     * Uses the encrypted private key specific to the active wallet.
     */
    fun unlockWalletWithBiometricCipher(cipher: Cipher): Boolean {
         android.util.Log.d("WalletManager", "Attempting to unlock wallet with biometric cipher.")
        val activePublicKey = getActiveWalletPublicKey() ?: run {
            android.util.Log.w("WalletManager", "UnlockWithBiometric: No active wallet.")
            return false
        }
        try {
            // Retrieve the biometric-encrypted PRIVATE KEY for the active wallet
            val encryptedPrivateKeyString = prefs.getString(getWalletPrefKey(activePublicKey, KEY_BIOMETRIC_ENCRYPTED_PRIVATE_KEY), null)
            if (encryptedPrivateKeyString == null) {
                 android.util.Log.e("WalletManager", "Biometric encrypted private key not found for active wallet: $activePublicKey")
                 return false
            }
            val encryptedPrivateKeyBytes = Base64.decode(encryptedPrivateKeyString, Base64.DEFAULT)

            // Decrypt the private key using the cipher from successful biometric auth
            val decryptedPrivateKeyBytes = cipher.doFinal(encryptedPrivateKeyBytes)

            // Cache the decrypted private key
            cachedPrivateKey = decryptedPrivateKeyBytes
            android.util.Log.d("WalletManager", "Private key for wallet $activePublicKey unlocked and cached via biometrics.")
            return true // Successfully decrypted and cached
        } catch (e: Exception) {
            // This could be UserNotAuthenticatedException if auth timed out, IllegalBlockSizeException, etc.
            android.util.Log.e("WalletManager", "Error unlocking wallet $activePublicKey with biometric cipher (${e.javaClass.simpleName})", e)
            return false
        }
    }


    /**
     * Set the preferred NFT contract address for the *active* wallet
     */
    fun setPreferredNftContract(contract: String?) {
        val publicKey = getActiveWalletPublicKey() ?: return // Cannot set if no active wallet
        prefs.edit().putString(getWalletPrefKey(publicKey, KEY_PREFERRED_NFT_CONTRACT), contract).apply()
        android.util.Log.d("WalletManager", "Preferred NFT contract for $publicKey set to: $contract")
    }

    /**
     * Get the preferred NFT contract address for the *active* wallet
     */
    fun getPreferredNftContract(): String? {
        val publicKey = getActiveWalletPublicKey() ?: return null // No active wallet
        val contract = prefs.getString(getWalletPrefKey(publicKey, KEY_PREFERRED_NFT_CONTRACT), null)
        android.util.Log.d("WalletManager", "getPreferredNftContract for $publicKey read value: $contract")
        return contract
    }
    /**
     * Get the list of all stored wallet public keys
     */
    fun getWalletPublicKeys(): Set<String> {
        return prefs.getStringSet(KEY_WALLET_LIST, emptySet()) ?: emptySet()
    }

    /**
     * Add a wallet public key to the list (Internal helper)
     */
    private fun addWalletToList(publicKey: String) {
        val currentList = getWalletPublicKeys().toMutableSet()
        if (currentList.add(publicKey)) {
            prefs.edit().putStringSet(KEY_WALLET_LIST, currentList).apply()
        }
    }

    /**
     * Set the currently active wallet
     */
    fun setActiveWallet(publicKey: String) {
        val currentKeys = getWalletPublicKeys()
        if (currentKeys.contains(publicKey)) {
            // Only update if the key is different from the current flow value
            if (_activeWalletPublicKeyFlow.value != publicKey) {
                prefs.edit().putString(KEY_ACTIVE_WALLET_PUBLIC_KEY, publicKey).apply()
                clearPrivateKeyCache() // Clear cache when switching wallets
                _activeWalletPublicKeyFlow.value = publicKey // Update the flow
                android.util.Log.d("WalletManager", "Active wallet set to: $publicKey")
            }
        } else {
            android.util.Log.w("WalletManager", "Attempted to set non-existent wallet as active: $publicKey")
        }
    }

    /**
     * Get the public key of the currently active wallet (primarily from the StateFlow).
     * This function is less critical now that the flow is the main source of truth for observers,
     * but can be used for immediate synchronous checks if needed.
     */
    fun getActiveWalletPublicKey(): String? {
        return _activeWalletPublicKeyFlow.value
    }

    /**
     * Reads the active wallet public key directly from SharedPreferences.
     * Used for initialization. It also corrects the stored value if it's invalid
     * but doesn't update the flow directly (the init block does that).
     */
    private fun readActiveWalletPublicKeyFromPrefs(): String? {
        val activeKey = prefs.getString(KEY_ACTIVE_WALLET_PUBLIC_KEY, null)
        val allKeys = getWalletPublicKeys()
        return when {
            activeKey != null && allKeys.contains(activeKey) -> activeKey
            allKeys.isNotEmpty() -> {
                val firstKey = allKeys.first()
                // Correct the stored preference if it was invalid or null
                prefs.edit().putString(KEY_ACTIVE_WALLET_PUBLIC_KEY, firstKey).apply()
                firstKey
            }
            else -> null // No wallets exist
        }
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