package net.xian.xianwalletapp.data

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ejemplo de cómo usar el sistema de cache de imágenes mejorado
 * Este archivo muestra las mejores prácticas encontradas en la investigación
 */

/**
 * Ejemplo 1: Cómo usar AsyncImage con el ImageLoader personalizado
 */
@Composable
fun TokenLogoImage(
    logoUrl: String?,
    tokenName: String,
    imageLoader: coil.ImageLoader,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = logoUrl,
        imageLoader = imageLoader, // Usar nuestro ImageLoader personalizado
        contentDescription = "$tokenName logo",
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape),
        // Estas configuraciones aseguran que use cache
        placeholder = null, // Coil manejará el placeholder internamente
        error = null // Coil manejará errores internamente
    )
}

/**
 * Ejemplo 2: Precargar imágenes de manera eficiente
 */
class TokenImagePreloader(private val context: Context) {
    private val cacheManager = ImprovedTokenLogoCacheManager(context)
    
    /**
     * Precargar imágenes de tokens importantes en background
     */
    suspend fun preloadImportantTokens(tokenUrls: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            tokenUrls.forEach { url ->
                cacheManager.cacheTokenLogoInBackground(url, "Preload")
            }
        }
    }
    
    /**
     * Verificar si un token está en cache antes de mostrarlo
     */
    suspend fun isTokenCached(url: String): Boolean {
        return cacheManager.isLogoCached(url)
    }
}

/**
 * Ejemplo 3: Mejores prácticas para configuración de cache
 */
object ImageCacheBestPractices {
    
    /**
     * Configuración recomendada para diferentes tipos de apps
     */
    
    // Para apps con muchas imágenes pequeñas (como logos de tokens)
    fun createTokenImageLoader(context: Context) = coil.ImageLoader.Builder(context)
        .memoryCache {
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.25) // 25% de memoria RAM
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(context.filesDir.resolve("token_cache"))
                .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                .build()
        }
        .respectCacheHeaders(false) // Ignorar headers para cache persistente
        .build()
    
    // Para apps con imágenes grandes (como NFTs)
    fun createNftImageLoader(context: Context) = coil.ImageLoader.Builder(context)
        .memoryCache {
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.15) // Menos memoria para imágenes grandes
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(context.filesDir.resolve("nft_cache"))
                .maxSizeBytes(200L * 1024 * 1024) // 200 MB para NFTs
                .build()
        }
        .build()
}

/**
 * Ejemplo 4: Cómo verificar y limpiar cache cuando sea necesario
 */
class CacheMaintenanceManager(private val context: Context) {
    private val cacheManager = ImprovedTokenLogoCacheManager(context)
    
    /**
     * Verificar estadísticas de cache
     */
    suspend fun logCacheStats() {
        val stats = cacheManager.getCacheStats()
        android.util.Log.d("CacheStats", """
            Disk Cache: ${stats.diskCacheSize / 1024 / 1024} MB / ${stats.diskCacheMaxSize / 1024 / 1024} MB
            Memory Cache: ${stats.memoryCacheSize} items / ${stats.memoryCacheMaxSize} max
            Cache Directory: ${stats.diskCacheDirectory}
        """.trimIndent())
    }
    
    /**
     * Limpiar cache si está lleno
     */
    suspend fun cleanCacheIfNeeded() {
        val stats = cacheManager.getCacheStats()
        val usagePercent = (stats.diskCacheSize.toFloat() / stats.diskCacheMaxSize.toFloat()) * 100
        
        if (usagePercent > 90) {
            android.util.Log.w("CacheManager", "Cache usage at ${usagePercent}%, clearing cache")
            cacheManager.clearCache()
        }
    }
}

/**
 * Ejemplo 5: Uso en ViewModel (patrón recomendado)
 */
class ExampleTokenViewModel(private val context: Context) {
    private val cacheManager = ImprovedTokenLogoCacheManager(context)
    
    /**
     * Obtener el ImageLoader para usar en UI
     */
    fun getImageLoader() = cacheManager.imageLoader
    
    /**
     * Precargar logos de tokens al inicializar ViewModel
     */
    fun preloadTokenLogos(tokens: List<Pair<String, String?>>) {
        CoroutineScope(Dispatchers.IO).launch {
            cacheManager.cacheTokenLogos(tokens)
        }
    }
    
    /**
     * Verificar si un logo está en cache
     */
    suspend fun isLogoCached(url: String?): Boolean {
        return cacheManager.isLogoCached(url)
    }
}