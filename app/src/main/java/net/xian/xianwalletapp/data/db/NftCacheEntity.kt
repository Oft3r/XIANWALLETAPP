package net.xian.xianwalletapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nft_cache")
data class NftCacheEntity(
    @PrimaryKey val contractAddress: String,
    val ownerPublicKey: String, // To associate NFT with a specific wallet
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val viewUrl: String?
)
