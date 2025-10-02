package net.xian.xianwalletapp.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import net.xian.xianwalletapp.data.db.TokenPriceCacheDao
import net.xian.xianwalletapp.data.db.TokenPriceCacheEntity
import net.xian.xianwalletapp.network.XianNetworkService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenPriceRepository @Inject constructor(
    private val priceCacheDao: TokenPriceCacheDao,
    private val networkService: XianNetworkService
) {
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val CACHE_EXPIRY_MINUTES = 5 // Cache válido por 5 minutos
        private const val BACKGROUND_REFRESH_INTERVAL_MS = 30_000L // Refresh cada 30 segundos
        private const val TAG = "TokenPriceRepository"
    }
    
    /**
     * Obtiene el precio de un token usando el patrón "cache-first"
     * - Retorna inmediatamente el valor cacheado si existe
     * - Actualiza en segundo plano si el cache está obsoleto
     */
    fun getTokenPrice(contract: String): Flow<Float?> {
        // Iniciar actualización en segundo plano si es necesario
        repositoryScope.launch {
            refreshPriceIfStale(contract)
        }
        
        // Retornar Flow del cache que se actualiza automáticamente
        return priceCacheDao.getPriceFlow(contract)
            .map { it?.price }
            .distinctUntilChanged()
    }
    
    /**
     * Obtiene información completa del precio (precio + cambio porcentual)
     */
    fun getTokenPriceInfo(contract: String): Flow<Pair<Float, Float>?> {
        repositoryScope.launch {
            refreshPriceIfStale(contract)
        }
        
        return priceCacheDao.getPriceFlow(contract)
            .map { entity ->
                entity?.let { 
                    Pair(it.price, it.priceChangePercent ?: 0f)
                }
            }
            .distinctUntilChanged()
    }
    
    /**
     * Fuerza la actualización de un precio específico
     */
    suspend fun forceRefreshPrice(contract: String): Result<Float> {
        return try {
            Log.d(TAG, "Force refreshing price for $contract")
            val priceInfo = fetchPriceFromNetwork(contract)
            
            if (priceInfo != null) {
                val cacheEntity = TokenPriceCacheEntity(
                    tokenContract = contract,
                    price = priceInfo.first,
                    priceChangePercent = priceInfo.second,
                    lastUpdated = System.currentTimeMillis(),
                    isStale = false
                )
                priceCacheDao.insertPrice(cacheEntity)
                Log.d(TAG, "Successfully updated price for $contract: ${priceInfo.first}")
                Result.success(priceInfo.first)
            } else {
                Log.w(TAG, "Failed to fetch price for $contract from network")
                Result.failure(Exception("Failed to fetch price from network"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error force refreshing price for $contract", e)
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza múltiples precios de una vez
     */
    suspend fun refreshMultiplePrices(contracts: List<String>) {
        contracts.forEach { contract ->
            repositoryScope.launch {
                refreshPriceIfStale(contract, forceRefresh = true)
            }
        }
    }
    
    /**
     * Inicia el proceso de actualización periódica en segundo plano
     */
    fun startPeriodicRefresh(contracts: List<String>) {
        repositoryScope.launch {
            while (true) {
                try {
                    contracts.forEach { contract ->
                        launch {
                            refreshPriceIfStale(contract)
                        }
                    }
                    delay(BACKGROUND_REFRESH_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic refresh", e)
                    delay(BACKGROUND_REFRESH_INTERVAL_MS) // Continue even if there's an error
                }
            }
        }
    }
    
    /**
     * Verifica si el cache está obsoleto y actualiza si es necesario
     */
    private suspend fun refreshPriceIfStale(contract: String, forceRefresh: Boolean = false) {
        try {
            val cachedPrice = priceCacheDao.getPrice(contract)
            val now = System.currentTimeMillis()
            val cacheExpiryTime = CACHE_EXPIRY_MINUTES * 60 * 1000L
            
            val shouldRefresh = forceRefresh || 
                cachedPrice == null || 
                cachedPrice.isStale ||
                (now - cachedPrice.lastUpdated) > cacheExpiryTime
            
            if (shouldRefresh) {
                Log.d(TAG, "Refreshing stale price for $contract")
                val priceInfo = fetchPriceFromNetwork(contract)
                
                if (priceInfo != null) {
                    val newCacheEntity = TokenPriceCacheEntity(
                        tokenContract = contract,
                        price = priceInfo.first,
                        priceChangePercent = priceInfo.second,
                        lastUpdated = now,
                        isStale = false
                    )
                    priceCacheDao.insertPrice(newCacheEntity)
                    Log.d(TAG, "Updated price for $contract: ${priceInfo.first}")
                } else {
                    // Marcar como obsoleto si no se pudo actualizar
                    cachedPrice?.let {
                        priceCacheDao.markAsStale(contract, true)
                    }
                    Log.w(TAG, "Failed to refresh price for $contract, marked as stale")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing price for $contract", e)
        }
    }
    
    /**
     * Obtiene el precio desde la red según el tipo de token
     */
    private suspend fun fetchPriceFromNetwork(contract: String): Pair<Float, Float>? {
        return try {
            when (contract) {
                "currency" -> {
                    // Para XIAN, usamos getXianPriceInfo que retorna Pair<PairInfo?, Pair<Float, Float>?>
                    val xianPriceResult = networkService.getXianPriceInfo()
                    val reserves = xianPriceResult.second
                    reserves?.let { (reserve0, reserve1) ->
                        val price = if (reserve1 != 0f) reserve0 / reserve1 else 0f
                        Pair(price, 0f) // XIAN no tiene cambio porcentual por ahora
                    }
                }
                "con_poop_coin" -> {
                    val priceInfo = networkService.getPoopPriceInfo()
                    priceInfo?.let { (reserve0_poop, reserve1_xian) ->
                        val price = if (reserve0_poop != 0f) reserve1_xian / reserve0_poop else 0f
                        Pair(price, 0f) // Por ahora sin cambio porcentual
                    }
                }
                "con_xtfu" -> {
                    val priceInfo = networkService.getXtfuPriceInfo()
                    priceInfo?.let { (reserve0_xtfu, reserve1_xian) ->
                        val price = if (reserve0_xtfu != 0f) reserve1_xian / reserve0_xtfu else 0f
                        Pair(price, 0f) // Por ahora sin cambio porcentual
                    }
                }
                "con_xarb" -> {
                    val priceInfo = networkService.getXarbPriceInfo()
                    priceInfo?.let { (reserve0_xarb, reserve1_xian) ->
                        val price = if (reserve0_xarb != 0f) reserve1_xian / reserve0_xarb else 0f
                        Pair(price, 0f) // Por ahora sin cambio porcentual
                    }
                }
                "con_xwt" -> {
                    val priceInfo = networkService.getXwtPriceInfo()
                    priceInfo?.let { (reserve0_xwt, reserve1_xian) ->
                        val price = if (reserve0_xwt != 0f) reserve1_xian / reserve0_xwt else 0f
                        Pair(price, 0f) // Por ahora sin cambio porcentual
                    }
                }
                "con_slither" -> {
                    val priceInfo = networkService.getSlitherPriceInfo()
                    priceInfo?.let { (reserve0_slither, reserve1_xian) ->
                        val price = if (reserve0_slither != 0f) reserve1_xian / reserve0_slither else 0f
                        Pair(price, 0f) // Por ahora sin cambio porcentual
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown contract for price fetch: $contract")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching price for $contract", e)
            null
        }
    }
    
    /**
     * Limpia el cache de precios
     */
    suspend fun clearCache() {
        priceCacheDao.clearAll()
        Log.d(TAG, "Price cache cleared")
    }
    
    /**
     * Obtiene estadísticas del cache
     */
    suspend fun getCacheStats(): Map<String, Any> {
        val allPrices = priceCacheDao.getAllPricesFlow()
        // Esta es una implementación simple, podrías expandirla
        return mapOf(
            "totalCachedPrices" to "Available via Flow",
            "lastUpdated" to System.currentTimeMillis()
        )
    }
}