package net.xian.xianwalletapp.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenPriceCacheDao {
    
    @Query("SELECT * FROM token_price_cache WHERE tokenContract = :contract")
    fun getPriceFlow(contract: String): Flow<TokenPriceCacheEntity?>
    
    @Query("SELECT * FROM token_price_cache WHERE tokenContract = :contract")
    suspend fun getPrice(contract: String): TokenPriceCacheEntity?
    
    @Query("SELECT * FROM token_price_cache")
    fun getAllPricesFlow(): Flow<List<TokenPriceCacheEntity>>
    
    @Query("SELECT * FROM token_price_cache WHERE lastUpdated < :staleThreshold")
    suspend fun getStalePrices(staleThreshold: Long): List<TokenPriceCacheEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: TokenPriceCacheEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<TokenPriceCacheEntity>)
    
    @Query("UPDATE token_price_cache SET isStale = :isStale WHERE tokenContract = :contract")
    suspend fun markAsStale(contract: String, isStale: Boolean = true)
    
    @Query("DELETE FROM token_price_cache WHERE tokenContract = :contract")
    suspend fun deletePrice(contract: String)
    
    @Query("DELETE FROM token_price_cache")
    suspend fun clearAll()
}