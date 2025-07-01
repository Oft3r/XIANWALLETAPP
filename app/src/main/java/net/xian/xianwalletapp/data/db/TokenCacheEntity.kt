package net.xian.xianwalletapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for caching token information including logo URLs
 */
@Entity(tableName = "token_cache")
data class TokenCacheEntity(
    @PrimaryKey
    val contract: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val logoUrl: String?,
    val isLogoCached: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isActive: Boolean = true // Whether the token is actively shown in the wallet
)