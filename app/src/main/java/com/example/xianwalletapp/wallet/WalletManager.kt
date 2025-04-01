package com.example.xianwalletapp.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    
    // Keys for SharedPreferences
    companion object {
        private const val WALLET_PREFS = "xian_wallet_prefs"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_ENCRYPTED_PRIVATE_KEY = "encrypted_private_key"
        private const val KEY_TOKEN_LIST = "token_list"
        private const val KEY_RPC_URL = "rpc_url"
        private const val KEY_EXPLORER_URL = "explorer_url"
        private const val KEY_REQUIRE_PASSWORD = "require_password"
        
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
        
        return crypto.decryptPrivateKey(encryptedPrivateKey, password, publicKey)
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
     * Get require password on startup setting
     */
    fun getRequirePassword(): Boolean {
        return prefs.getBoolean(KEY_REQUIRE_PASSWORD, false)
    }

    /**
     * Set require password on startup setting
     */
    fun setRequirePassword(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_PASSWORD, enabled).apply()
    }
}

/**
 * Result class for wallet creation/import operations
 */
data class WalletCreationResult(
    val success: Boolean,
    val publicKey: String? = null,
    val error: String? = null
)