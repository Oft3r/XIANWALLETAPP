package net.xian.xianwalletapp.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.lang.reflect.Type

// No need for @Serializable with Gson, but keep the data class structure
// Using a simple Map directly for Gson serialization/deserialization

class FaviconCacheManager(private val context: Context) {

    private val cacheFileName = "favicon_cache_gson.json" // Use a different name to avoid conflicts
    private var memoryCache: MutableMap<String, String> = mutableMapOf()
    private val gson = Gson() // Create Gson instance

    init {
        // Load cache on initialization
        loadCacheFromFile()
        Log.d("FaviconCacheManager", "Initialized (Gson). Loaded ${memoryCache.size} items from cache.")
    }

    private fun loadCacheFromFile() {
        try {
            val cacheFile = File(context.filesDir, cacheFileName)
            if (cacheFile.exists()) {
                val jsonString = cacheFile.readText()
                if (jsonString.isNotBlank()) {
                    // Define the type for Gson deserialization of a Map<String, String>
                    val type: Type = object : TypeToken<Map<String, String>>() {}.type
                    val loadedMap: Map<String, String> = gson.fromJson(jsonString, type)
                    memoryCache.clear() // Clear existing memory cache before loading from file
                    memoryCache.putAll(loadedMap)
                     Log.d("FaviconCacheManager", "Successfully loaded ${memoryCache.size} items from $cacheFileName")
                } else {
                    Log.d("FaviconCacheManager", "Cache file '$cacheFileName' is empty.")
                }
            } else {
                 Log.d("FaviconCacheManager", "Cache file '$cacheFileName' does not exist. Starting fresh.")
            }
        } catch (e: Exception) { // Catch broader exceptions during file read/parse
            Log.e("FaviconCacheManager", "Error loading favicon cache from $cacheFileName", e)
            // Optionally clear memory cache if file is corrupt
             memoryCache.clear()
        }
    }

    private suspend fun saveCacheToFile() {
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.filesDir, cacheFileName)
                // Convert the current memory cache (which is a Map) to JSON
                val jsonString = gson.toJson(memoryCache)
                cacheFile.writeText(jsonString)
                 Log.d("FaviconCacheManager", "Saved ${memoryCache.size} items to cache file '$cacheFileName'.")
            } catch (e: IOException) {
                Log.e("FaviconCacheManager", "Error saving favicon cache to $cacheFileName", e)
            } catch (e: Exception) { // Catch serialization errors etc.
                 Log.e("FaviconCacheManager", "Error during cache serialization or saving", e)
            }
        }
    }

    /**
     * Gets the cached favicon URL for a given website URL.
     * Tries exact key first, then a normalized key (host-based) to avoid mismatch issues.
     */
    fun getFaviconUrl(websiteUrl: String): String? {
        memoryCache[websiteUrl]?.let { return it }
        val normalizedKey = normalizeUrlForCache(websiteUrl)
        return memoryCache[normalizedKey]
    }

    /**
     * Saves or updates the favicon URL for a given website URL in the cache.
     * Consider normalizing websiteUrl if consistency is needed.
     */
    suspend fun saveFaviconUrl(websiteUrl: String, faviconUrl: String) {
        val normalizedKey = normalizeUrlForCache(websiteUrl)
        var changed = false
        if (memoryCache[websiteUrl] != faviconUrl) {
            memoryCache[websiteUrl] = faviconUrl
            changed = true
        }
        if (memoryCache[normalizedKey] != faviconUrl) {
            memoryCache[normalizedKey] = faviconUrl
            changed = true
        }
        if (changed) saveCacheToFile() // Save changes asynchronously
    }

     /**
      * Clears both the in-memory cache and the persistent cache file.
      */
    suspend fun clearCache() {
        val hadItems = memoryCache.isNotEmpty()
        memoryCache.clear()
        Log.d("FaviconCacheManager", "In-memory cache cleared.")
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.filesDir, cacheFileName)
                if (cacheFile.exists()) {
                    if (cacheFile.delete()) {
                        Log.d("FaviconCacheManager", "Cache file '$cacheFileName' deleted successfully.")
                    } else {
                        Log.w("FaviconCacheManager", "Failed to delete cache file '$cacheFileName'.")
                    }
                } else if (hadItems) {
                     // Log only if we expected a file to be there (i.e., memory cache wasn't empty)
                     Log.d("FaviconCacheManager", "Cache file '$cacheFileName' did not exist, nothing to delete.")
                }
            } catch (e: Exception) {
                Log.e("FaviconCacheManager", "Error deleting cache file '$cacheFileName'", e)
            }
        }
    }

    // Helper function to normalize URL to a stable cache key (lowercased host, optionally scheme)
    private fun normalizeUrlForCache(url: String): String {
        return try {
            val trimmed = url.trim()
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            val u = java.net.URL(withScheme)
            // Key by host only — favicons are typically per-site, not per-path
            u.host.lowercase()
        } catch (_: Exception) {
            url.trim().lowercase().removeSuffix("/")
        }
    }
}
