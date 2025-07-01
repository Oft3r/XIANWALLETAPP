package net.xian.xianwalletapp.data

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Manager for caching token logo images permanently on device storage.
 * Uses Coil's disk cache system for efficient image storage and retrieval.
 */
class TokenLogoCacheManager(private val context: Context) {
    
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of app's memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.filesDir, "token_logos_cache")) // Use filesDir for permanent cache
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB disk cache for token logos
                    .build()
            }
            .build()
    }

    /**
     * Check if a token logo is already cached
     */
    fun isLogoCached(logoUrl: String?): Boolean {
        if (logoUrl.isNullOrBlank()) return false
        // Coil's default disk cache key is the MD5 hash of the data's URI.
        // We check if a snapshot for this key exists in the disk cache.
        val key = generateCacheKey(logoUrl)
        val snapshot = imageLoader.diskCache?.openSnapshot(key)
        return snapshot != null.also {
            // Make sure to close the snapshot to avoid resource leaks.
            snapshot?.close()
        }
    }
    
    /**
     * Preload and cache a token logo image
     * @param logoUrl The URL of the token logo to cache
     * @param tokenSymbol The token symbol for logging purposes
     * @return true if successfully cached, false otherwise
     */
    suspend fun cacheTokenLogo(logoUrl: String?, tokenSymbol: String = "Unknown"): Boolean {
        if (logoUrl.isNullOrBlank()) {
            Log.d("TokenLogoCacheManager", "Skipping cache for $tokenSymbol: empty URL")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // Check if already cached to avoid unnecessary operations
                if (isLogoCached(logoUrl)) {
                    Log.d("TokenLogoCacheManager", "Logo already cached for $tokenSymbol: $logoUrl")
                    return@withContext true
                }
                
                // Coil's ImageLoader is smart enough not to re-download if the image
                // is already in the disk or memory cache. We just need to execute the request.
                val request = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .build()
                
                // Execute the request to download and cache the image if it's not already present.
                val result = imageLoader.execute(request)
                
                if (result is SuccessResult) {
                    Log.d("TokenLogoCacheManager", "Successfully cached logo for $tokenSymbol: $logoUrl")
                    true
                } else {
                    Log.w("TokenLogoCacheManager", "Failed to cache logo for $tokenSymbol: $logoUrl")
                    false
                }
            } catch (e: Exception) {
                Log.e("TokenLogoCacheManager", "Error caching logo for $tokenSymbol: $logoUrl", e)
                false
            }
        }
    }
    
    /**
     * Cache logo in background without blocking UI
     * @param logoUrl The URL of the token logo to cache
     * @param tokenSymbol The token symbol for logging purposes
     */
    suspend fun cacheTokenLogoInBackground(logoUrl: String?, tokenSymbol: String = "Unknown") {
        if (logoUrl.isNullOrBlank()) return
        
        // Launch background coroutine for caching without blocking
        withContext(Dispatchers.IO) {
            try {
                cacheTokenLogo(logoUrl, tokenSymbol)
            } catch (e: Exception) {
                Log.e("TokenLogoCacheManager", "Background caching failed for $tokenSymbol", e)
            }
        }
    }
    
    /**
     * Batch cache multiple token logos
     * @param tokens List of token info containing logo URLs
     */
    suspend fun cacheTokenLogos(tokens: List<Pair<String, String?>>): Int {
        var successCount = 0
        
        for ((tokenSymbol, logoUrl) in tokens) {
            if (cacheTokenLogo(logoUrl, tokenSymbol)) {
                successCount++
            }
        }
        
        Log.d("TokenLogoCacheManager", "Batch cached $successCount/${tokens.size} token logos")
        return successCount
    }
    
    /**
     * Clear all cached token logos
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                // Clear memory cache
                imageLoader.memoryCache?.clear()
                
                // Clear disk cache
                imageLoader.diskCache?.clear()
                
                Log.d("TokenLogoCacheManager", "Cleared all token logo caches")
            } catch (e: Exception) {
                Log.e("TokenLogoCacheManager", "Error clearing cache", e)
            }
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            val diskCache = imageLoader.diskCache
            val memoryCache = imageLoader.memoryCache
            
            CacheStats(
                diskCacheSize = diskCache?.size ?: 0L,
                diskCacheMaxSize = diskCache?.maxSize ?: 0L,
                memoryCacheSize = memoryCache?.size ?: 0,
                memoryCacheMaxSize = memoryCache?.maxSize ?: 0
            )
        }
    }
    
    data class CacheStats(
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long,
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int
    )
    
    /**
     * Generate a cache key for a given URL (used internally by Coil)
     */
    private fun generateCacheKey(url: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}