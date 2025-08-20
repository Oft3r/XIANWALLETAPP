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
import okhttp3.OkHttpClient
import okhttp3.Cache

/**
 * Improved Token Logo Cache Manager
 * Based on best practices from Coil documentation and community examples
 * 
 * Key improvements:
 * - Persistent disk cache configuration
 * - Proper cache key verification
 * - Better offline support
 * - Memory and disk cache coordination
 */
class ImprovedTokenLogoCacheManager(private val context: Context) {
    
    companion object {
        private const val MEMORY_CACHE_PERCENT = 0.25 // 25% of available memory
        private const val DISK_CACHE_SIZE_MB = 50L // 50 MB disk cache
        private const val HTTP_CACHE_SIZE_MB = 20L // 20 MB OkHttp HTTP cache for validator-based revalidation
        private const val TAG = "ImprovedTokenCache"
    }
    
    /**
     * Single ImageLoader instance shared across the app
     * This is the recommended approach by Coil for optimal performance
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    // Use filesDir for persistent cache (survives app restarts)
                    .directory(File(context.filesDir, "token_logos_cache"))
                    .maxSizeBytes(DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            // Configure OkHttp with an on-disk HTTP cache to support ETag/Last-Modified revalidation
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_SIZE_MB * 1024 * 1024))
                    .build()
            }
            // Respect server cache headers to enable deterministic validator-based caching
            .respectCacheHeaders(true)
            .build()
    }

    /**
     * Check if a token logo is cached (memory or disk)
     * Uses Coil's internal cache mechanism for accurate results
     */
    suspend fun isLogoCached(logoUrl: String?): Boolean {
        if (logoUrl.isNullOrBlank()) return false
        
        return withContext(Dispatchers.IO) {
            try {
                // Create the same request that would be used for loading
                val request = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .build()
                
                // Check memory cache first (fastest)
                request.memoryCacheKey?.let { memoryKey ->
                    if (imageLoader.memoryCache?.get(memoryKey) != null) {
                        Log.d(TAG, "Logo found in memory cache: $logoUrl")
                        return@withContext true
                    }
                }
                
                // Check disk cache
                request.diskCacheKey?.let { diskKey ->
                    val snapshot = imageLoader.diskCache?.openSnapshot(diskKey)
                    val isInDiskCache = snapshot != null
                    snapshot?.close()
                    
                    if (isInDiskCache) {
                        Log.d(TAG, "Logo found in disk cache: $logoUrl")
                        return@withContext true
                    }
                }
                
                Log.d(TAG, "Logo not cached: $logoUrl")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cache for $logoUrl", e)
                false
            }
        }
    }
    
    /**
     * Preload and cache a token logo
     * This downloads the image and stores it in both memory and disk cache
     */
    suspend fun cacheTokenLogo(logoUrl: String?, tokenSymbol: String = "Unknown"): Boolean {
        if (logoUrl.isNullOrBlank()) {
            Log.d(TAG, "Skipping cache for $tokenSymbol: empty URL")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // Check if already cached to avoid unnecessary network calls
                if (isLogoCached(logoUrl)) {
                    Log.d(TAG, "Logo already cached for $tokenSymbol: $logoUrl")
                    return@withContext true
                }
                
                // Create request for caching
                val request = ImageRequest.Builder(context)
                    .data(logoUrl)
                    // These settings ensure the image is cached
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
                
                // Execute the request - this will download and cache the image
                val result = imageLoader.execute(request)
                
                if (result is SuccessResult) {
                    Log.d(TAG, "Successfully cached logo for $tokenSymbol: $logoUrl")
                    true
                } else {
                    Log.w(TAG, "Failed to cache logo for $tokenSymbol: $logoUrl")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching logo for $tokenSymbol: $logoUrl", e)
                false
            }
        }
    }
    
    /**
     * Batch cache multiple token logos efficiently
     */
    suspend fun cacheTokenLogos(tokens: List<Pair<String, String?>>): Int {
        var successCount = 0
        
        for ((tokenSymbol, logoUrl) in tokens) {
            if (cacheTokenLogo(logoUrl, tokenSymbol)) {
                successCount++
            }
        }
        
        Log.d(TAG, "Batch cached $successCount/${tokens.size} token logos")
        return successCount
    }
    
    /**
     * Cache logo in background without blocking
     */
    suspend fun cacheTokenLogoInBackground(logoUrl: String?, tokenSymbol: String = "Unknown") {
        if (logoUrl.isNullOrBlank()) return
        
        withContext(Dispatchers.IO) {
            try {
                cacheTokenLogo(logoUrl, tokenSymbol)
            } catch (e: Exception) {
                Log.e(TAG, "Background caching failed for $tokenSymbol", e)
            }
        }
    }
    
    /**
     * Clear all caches (useful for troubleshooting)
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                Log.d(TAG, "Cleared all token logo caches")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache", e)
            }
        }
    }
    
    /**
     * Get cache statistics for monitoring
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            val diskCache = imageLoader.diskCache
            val memoryCache = imageLoader.memoryCache
            
            CacheStats(
                diskCacheSize = diskCache?.size ?: 0L,
                diskCacheMaxSize = diskCache?.maxSize ?: 0L,
                memoryCacheSize = memoryCache?.size ?: 0,
                memoryCacheMaxSize = memoryCache?.maxSize ?: 0,
                diskCacheDirectory = diskCache?.directory?.absolutePath ?: "Unknown"
            )
        }
    }
    
    data class CacheStats(
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long,
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int,
        val diskCacheDirectory: String
    )
}