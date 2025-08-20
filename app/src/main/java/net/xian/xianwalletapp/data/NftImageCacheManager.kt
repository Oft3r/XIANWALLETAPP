package net.xian.xianwalletapp.data

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.StatFs
import java.util.regex.Pattern

/**
 * Dedicated ImageLoader and HTTP client for NFT images with:
 * - Persistent disk cache under filesDir (not cacheDir) to avoid OS cache purges
 * - OkHttp HTTP cache plus detailed cache/header logging (hits/misses, 304s, TTLs)
 * - Centralized file logger for post-mortem analysis
 *
 * This manager is intentionally separate from TokenLogoCacheManager to isolate NFT behavior.
 */
class NftImageCacheManager(private val context: Context) {

    private val logger = FileCacheLogger(context)

    // 25 MB HTTP cache for conditional GETs, 304s, etc.
    private val httpCacheDir = File(context.filesDir, "http_nft_cache").apply { mkdirs() }
    private val httpCacheSizeBytes = 25L * 1024 * 1024

    // 150 MB disk cache for decoded images (Coil's DiskCache)
    private val imageDiskCacheDir = File(context.filesDir, "nft_images_cache").apply { mkdirs() }
    private val imageDiskCacheSizeBytes = 150L * 1024 * 1024

    // OkHttp client with cache + logging
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(httpCacheDir, httpCacheSizeBytes))
            // Network interceptor sees responses after caching logic
            .addNetworkInterceptor(CacheLoggingInterceptor(logger))
            // Application interceptor to see requests before cache as well
            .addInterceptor(RequestLoggingInterceptor(logger))
            .build()
    }

    // Dedicated Coil ImageLoader for NFTs using filesDir-backed DiskCache
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // 25% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageDiskCacheDir)
                    .maxSizeBytes(imageDiskCacheSizeBytes)
                    .build()
            }
            .okHttpClient(okHttpClient)
            .build()
    }

    /**
     * Quick preload to force caching of an NFT image if needed.
     */
    suspend fun preload(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val req = ImageRequest.Builder(context)
                .data(url)
                .build()
            when (val res = imageLoader.execute(req)) {
                is SuccessResult -> {
                    logger.log(
                        "coil_preload_success",
                        mapOf(
                            "url" to url,
                            "sizePx" to (res.drawable?.intrinsicWidth?.toString() + "x" + res.drawable?.intrinsicHeight?.toString())
                        )
                    )
                    true
                }
                else -> {
                    logger.log("coil_preload_fail", mapOf("url" to url, "result" to res::class.java.simpleName))
                    false
                }
            }
        } catch (e: Exception) {
            logger.log("coil_preload_error", mapOf("url" to url, "error" to (e.message ?: "unknown")))
            false
        }
    }

    /**
     * Check if an NFT image is currently in Coil's disk cache.
     */
    fun isImageCached(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val key = generateCoilKey(url)
        val snapshot = imageLoader.diskCache?.openSnapshot(key)
        val exists = snapshot != null
        snapshot?.close()
        return exists
    }

    /**
     * Report cache statistics for diagnostics.
     */
    fun getCacheStats(): CacheStats {
        val dc = imageLoader.diskCache
        val mc = imageLoader.memoryCache
        val hc = okHttpClient.cache
        return CacheStats(
            diskCacheSize = dc?.size ?: 0L,
            diskCacheMaxSize = dc?.maxSize ?: 0L,
            memoryCacheSize = mc?.size ?: 0,
            memoryCacheMaxSize = mc?.maxSize ?: 0,
            httpCacheSize = hc?.size() ?: 0L,
            httpCacheMaxSize = hc?.maxSize()?.toLong() ?: 0L
        )
    }

    /**
     * Dump a snapshot of cache state to the audit log and return a human-readable summary.
     */
    fun dumpState(): String {
        val stats = getCacheStats()
        val diskDirInfo = dirInfo(imageDiskCacheDir)
        val httpDirInfo = dirInfo(httpCacheDir)
        val summary = buildString {
            appendLine("NFT Cache State:")
            appendLine("- Coil DiskCache dir: ${imageDiskCacheDir.absolutePath}")
            appendLine("  size=${stats.diskCacheSize} max=${stats.diskCacheMaxSize} files=${diskDirInfo.count} bytes=${diskDirInfo.bytes}")
            appendLine("- MemoryCache size=${stats.memoryCacheSize} max=${stats.memoryCacheMaxSize}")
            appendLine("- OkHttp HTTP cache dir: ${httpCacheDir.absolutePath}")
            appendLine("  size=${stats.httpCacheSize} max=${stats.httpCacheMaxSize} files=${httpDirInfo.count} bytes=${httpDirInfo.bytes}")
            appendLine("- Audit log: ${getAuditLogFile().absolutePath}")
        }
        logger.log(
            "cache_dump",
            mapOf(
                "diskCacheSize" to stats.diskCacheSize,
                "diskCacheMax" to stats.diskCacheMaxSize,
                "memoryCacheSize" to stats.memoryCacheSize,
                "memoryCacheMax" to stats.memoryCacheMaxSize,
                "httpCacheSize" to stats.httpCacheSize,
                "httpCacheMax" to stats.httpCacheMaxSize,
                "diskDir" to imageDiskCacheDir.absolutePath,
                "httpDir" to httpCacheDir.absolutePath
            )
        )
        return summary
    }

    fun getAuditLogFile(): File = logger.logFile

    data class DeviceStorageStats(
        val filesDirFreeBytes: Long,
        val filesDirTotalBytes: Long
    )

    /**
     * Measure storage stats for filesDir where caches and logs live.
     */
    fun getDeviceStorageStats(): DeviceStorageStats {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val avail = stat.availableBlocksLong * blockSize
            DeviceStorageStats(filesDirFreeBytes = avail, filesDirTotalBytes = total)
        } catch (e: Exception) {
            Log.e("CacheAudit", "Error reading storage stats", e)
            DeviceStorageStats(filesDirFreeBytes = -1L, filesDirTotalBytes = -1L)
        }
    }

    /**
     * Parse the audit log and summarize observed TTL-related headers and cache sources.
     * Returns a human-readable summary string and also writes a structured ttl_summary event.
     */
    fun summarizeTtlPolicies(maxLines: Int = 5000): String {
        val log = getAuditLogFile()
        if (!log.exists()) return "No audit log found at: ${log.absolutePath}"

        val lines = try {
            log.readLines()
        } catch (e: Exception) {
            logger.log("ttl_summary_error", mapOf("error" to (e.message ?: "readLines failed")))
            return "Failed to read audit log: ${e.message}"
        }
        val slice = if (lines.size > maxLines) lines.takeLast(maxLines) else lines

        val httpLines = slice.filter { it.contains("\"event\":\"http_response\"") }
        val fromBuckets = mutableMapOf<String, Int>()
        val maxAgeBuckets = mutableMapOf<String, Int>()
        var withEtag = 0
        var withMaxAge = 0
        var total = 0
        val ageValues = mutableListOf<Int>()

        val fromRegex = "\"from\":\"([^\"]+)\"".toRegex()
        val ccRegex = "\"cacheControl\":\"([^\"]*)\"".toRegex()
        val etagRegex = "\"etag\":\"([^\"]*)\"".toRegex()
        val ageRegex = "\"age\":\"([^\"]*)\"".toRegex()
        val maxAgeValueRegex = "max-age=([0-9]+)".toRegex()

        httpLines.forEach { line ->
            total++

            val from = fromRegex.find(line)?.groupValues?.getOrNull(1) ?: "unknown"
            fromBuckets[from] = (fromBuckets[from] ?: 0) + 1

            val cc = ccRegex.find(line)?.groupValues?.getOrNull(1) ?: ""
            val etag = etagRegex.find(line)?.groupValues?.getOrNull(1) ?: ""
            if (etag.isNotEmpty()) withEtag++

            val maxAge = maxAgeValueRegex.find(cc)?.groupValues?.getOrNull(1)
            if (maxAge != null) {
                withMaxAge++
                val secs = maxAge.toLongOrNull() ?: 0L
                val hours = if (secs > 0) secs / 3600L else 0L
                val bucket = if (hours > 0) "${hours}h(${secs}s)" else "${secs}s"
                maxAgeBuckets[bucket] = (maxAgeBuckets[bucket] ?: 0) + 1
            } else {
                maxAgeBuckets["no-max-age"] = (maxAgeBuckets["no-max-age"] ?: 0) + 1
            }

            val ageHeader = ageRegex.find(line)?.groupValues?.getOrNull(1) ?: ""
            val ageNum = ageHeader.filter { it.isDigit() }.toIntOrNull()
            if (ageNum != null) ageValues.add(ageNum)
        }

        fun pct(count: Int) = if (total == 0) 0 else (count * 100 / total)
        val fromSummary = fromBuckets.entries.sortedByDescending { it.value }
            .joinToString { "${it.key}=${it.value} (${pct(it.value)}%)" }
        val maxAgeTop = maxAgeBuckets.entries.sortedByDescending { it.value }.take(5)
            .joinToString { "${it.key}=${it.value} (${pct(it.value)}%)" }
        val ageAvg = if (ageValues.isNotEmpty()) ageValues.average().toInt() else 0

        val summary = buildString {
            appendLine("TTL Summary (last ${httpLines.size} http_response events):")
            appendLine("- Sources: $fromSummary")
            appendLine("- ETag present in: $withEtag/$total (${pct(withEtag)}%)")
            appendLine("- Cache-Control max-age present in: $withMaxAge/$total (${pct(withMaxAge)}%)")
            appendLine("- Top max-age buckets: $maxAgeTop")
            appendLine("- Avg Age header (s): $ageAvg")
            appendLine("- Log path: ${log.absolutePath}")
        }

        logger.log(
            "ttl_summary",
            mapOf(
                "responses" to total,
                "withEtag" to withEtag,
                "withMaxAge" to withMaxAge,
                "from_cache" to (fromBuckets["cache"] ?: 0),
                "from_conditional" to (fromBuckets["conditional_cache"] ?: 0),
                "from_network" to (fromBuckets["network"] ?: 0),
                "topMaxAge" to maxAgeTop
            )
        )

        return summary
    }
     
    data class CacheStats(
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long,
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int,
        val httpCacheSize: Long,
        val httpCacheMaxSize: Long
    )

    private data class DirInfo(val count: Int, val bytes: Long)
    private fun dirInfo(dir: File): DirInfo {
        if (!dir.exists()) return DirInfo(0, 0)
        var count = 0
        var bytes = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                count++
                bytes += f.length()
            }
        }
        return DirInfo(count, bytes)
    }

    // Coil's default key is MD5 of the data's URI
    private fun generateCoilKey(url: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Logs request lines and request headers prior to cache evaluation.
     */
    private class RequestLoggingInterceptor(
        private val logger: FileCacheLogger
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            logger.log(
                "http_request",
                mapOf(
                    "method" to req.method,
                    "url" to req.url.toString(),
                    "headers" to req.headers.toString(),
                    "cacheControl" to req.cacheControl.toString()
                )
            )
            return chain.proceed(req)
        }
    }

    /**
     * Logs cache behavior after a response is received (network vs cache),
     * along with relevant cache headers and TTL hints.
     */
    private class CacheLoggingInterceptor(
        private val logger: FileCacheLogger
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val startNs = System.nanoTime()
            val resp = chain.proceed(chain.request())
            val tookMs = (System.nanoTime() - startNs) / 1_000_000

            val cacheResp = resp.cacheResponse
            val networkResp = resp.networkResponse

            val from = when {
                cacheResp != null && networkResp == null -> "cache" // served fully from cache
                cacheResp != null && networkResp != null -> "conditional_cache" // likely 304 validated
                else -> "network"
            }

            val cc = resp.header("Cache-Control") ?: ""
            val etag = resp.header("ETag") ?: ""
            val age = resp.header("Age") ?: ""
            val expires = resp.header("Expires") ?: ""
            val lastMod = resp.header("Last-Modified") ?: ""
            val contentLen = resp.header("Content-Length") ?: ""

            logger.log(
                "http_response",
                mapOf(
                    "url" to resp.request.url.toString(),
                    "code" to resp.code,
                    "from" to from,
                    "tookMs" to tookMs,
                    "cacheControl" to cc,
                    "etag" to etag,
                    "age" to age,
                    "expires" to expires,
                    "lastModified" to lastMod,
                    "contentLength" to contentLen
                )
            )
            return resp
        }
    }

    /**
     * Simple file-backed logger. Writes JSON-like key/value lines to filesDir/cache_audit.log
     * and also to Logcat with tag "CacheAudit".
     */
    private class FileCacheLogger(context: Context) {
        private val logTag = "CacheAudit"
        val logFile: File = File(context.filesDir, "cache_audit.log").apply { if (!exists()) createNewFile() }
        private val timeFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        @Synchronized
        fun log(event: String, details: Map<String, Any?>) {
            val timestamp = timeFmt.format(Date())
            val line = buildString {
                append("{")
                append("\"ts\":\"").append(timestamp).append("\",")
                append("\"event\":\"").append(event).append("\"")
                details.forEach { (k, v) ->
                    append(",\"").append(k).append("\":")
                    when (v) {
                        null -> append("null")
                        is Number, is Boolean -> append(v.toString())
                        else -> append("\"").append(escape(v.toString())).append("\"")
                    }
                }
                append("}")
            }
            try {
                FileWriter(logFile, true).use { fw ->
                    PrintWriter(fw).use { pw ->
                        pw.println(line)
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed writing cache log", e)
            }
            Log.d(logTag, line)
        }

        private fun escape(s: String): String {
            return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }
    }
}