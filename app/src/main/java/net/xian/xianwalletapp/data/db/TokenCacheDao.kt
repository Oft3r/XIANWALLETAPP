package net.xian.xianwalletapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for token cache operations
 */
@Dao
interface TokenCacheDao {
    
    @Query("SELECT * FROM token_cache WHERE isActive = 1 ORDER BY symbol ASC")
    fun getAllActiveTokens(): Flow<List<TokenCacheEntity>>
    
    @Query("SELECT * FROM token_cache WHERE contract = :contract")
    suspend fun getTokenByContract(contract: String): TokenCacheEntity?
    
    @Query("SELECT * FROM token_cache WHERE isLogoCached = 1")
    suspend fun getCachedLogoTokens(): List<TokenCacheEntity>
    
    @Query("SELECT * FROM token_cache WHERE isLogoCached = 0 AND logoUrl IS NOT NULL AND logoUrl != ''")
    suspend fun getTokensNeedingLogoCache(): List<TokenCacheEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: TokenCacheEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<TokenCacheEntity>)
    
    @Update
    suspend fun updateToken(token: TokenCacheEntity)
    
    @Query("UPDATE token_cache SET isLogoCached = 1 WHERE contract = :contract")
    suspend fun markLogoAsCached(contract: String)
    
    @Query("UPDATE token_cache SET isActive = :isActive WHERE contract = :contract")
    suspend fun setTokenActive(contract: String, isActive: Boolean)
    
    @Query("DELETE FROM token_cache WHERE contract = :contract")
    suspend fun deleteToken(contract: String)
    
    @Query("DELETE FROM token_cache")
    suspend fun deleteAllTokens()
    
    @Query("UPDATE token_cache SET isLogoCached = 0")
    suspend fun resetAllLogoCacheStatus()
    
    // Balance caching operations
    @Query("SELECT * FROM token_cache WHERE ownerPublicKey = :publicKey AND isActive = 1 ORDER BY symbol ASC")
    fun getActiveTokensWithBalances(publicKey: String): Flow<List<TokenCacheEntity>>
    
    @Query("SELECT * FROM token_cache WHERE contract = :contract AND ownerPublicKey = :publicKey")
    suspend fun getTokenWithBalance(contract: String, publicKey: String): TokenCacheEntity?
    
    @Query("UPDATE token_cache SET cachedBalance = :balance, balanceLastUpdated = :timestamp WHERE contract = :contract AND ownerPublicKey = :publicKey")
    suspend fun updateTokenBalance(contract: String, publicKey: String, balance: Float, timestamp: Long)
    
    @Query("SELECT cachedBalance FROM token_cache WHERE contract = :contract AND ownerPublicKey = :publicKey")
    suspend fun getCachedBalance(contract: String, publicKey: String): Float?
    
    @Query("SELECT * FROM token_cache WHERE ownerPublicKey = :publicKey AND balanceLastUpdated > 0 AND isActive = 1")
    suspend fun getTokensWithCachedBalances(publicKey: String): List<TokenCacheEntity>
    
    @Query("UPDATE token_cache SET ownerPublicKey = :publicKey WHERE contract = :contract")
    suspend fun setTokenOwner(contract: String, publicKey: String)
}