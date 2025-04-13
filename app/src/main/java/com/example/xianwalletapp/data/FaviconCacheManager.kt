package com.example.xianwalletapp.data

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
     * Consider normalizing websiteUrl if consistency is needed (e.g., removing trailing slashes).
     */
    fun getFaviconUrl(websiteUrl: String): String? {
        // Example normalization (optional, implement based on how URLs are stored/fetched):
        // val normalizedKey = normalizeUrlForCache(websiteUrl)
        return memoryCache[websiteUrl] // Use original or normalized key
    }

    /**
     * Saves or updates the favicon URL for a given website URL in the cache.
     * Consider normalizing websiteUrl if consistency is needed.
     */
    suspend fun saveFaviconUrl(websiteUrl: String, faviconUrl: String) {
        // Example normalization (optional):
        // val normalizedKey = normalizeUrlForCache(websiteUrl)
        val keyToSave = websiteUrl // Use original or normalized key

        if (memoryCache[keyToSave] != faviconUrl) {
            memoryCache[keyToSave] = faviconUrl
            saveCacheToFile() // Save changes asynchronously
        }
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

    // Optional: Helper function for URL normalization if needed
    // private fun normalizeUrlForCache(url: String): String {
    //     // Implement normalization logic (e.g., lowercase, remove trailing slash, etc.)
    //     return url.trim().lowercase().removeSuffix("/")
    // }
}