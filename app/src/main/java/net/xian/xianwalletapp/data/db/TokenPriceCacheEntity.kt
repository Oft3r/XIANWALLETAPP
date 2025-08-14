package net.xian.xianwalletapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_price_cache")
data class TokenPriceCacheEntity(
    @PrimaryKey
    val tokenContract: String,
    val price: Float,
    val priceChangePercent: Float? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isStale: Boolean = false // Marca si el precio necesita actualización
)