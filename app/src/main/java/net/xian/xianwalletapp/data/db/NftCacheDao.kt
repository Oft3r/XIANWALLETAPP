package net.xian.xianwalletapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NftCacheDao {

    /**
     * Inserts a list of NFTs. If an NFT with the same contractAddress already exists,
     * it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNfts(nfts: List<NftCacheEntity>)

    /**
     * Gets all cached NFTs associated with a specific owner's public key as a Flow.
     * The Flow will automatically emit updates when the data changes.
     */
    @Query("SELECT * FROM nft_cache WHERE ownerPublicKey = :ownerPublicKey ORDER BY name ASC")
    fun getNftsByOwner(ownerPublicKey: String): Flow<List<NftCacheEntity>>

    /**
     * Deletes all NFTs associated with a specific owner's public key.
     * Useful when switching wallets or deleting a wallet.
     */
    @Query("DELETE FROM nft_cache WHERE ownerPublicKey = :ownerPublicKey")
    suspend fun deleteNftsByOwner(ownerPublicKey: String)

    /**
     * Deletes NFTs for a specific owner that are NOT in the provided list of current contract addresses.
     * This is used to remove stale entries from the cache after fetching fresh data from the network.
     */
    @Query("DELETE FROM nft_cache WHERE ownerPublicKey = :ownerPublicKey AND contractAddress NOT IN (:currentContractAddresses)")
    suspend fun deleteOrphanedNfts(ownerPublicKey: String, currentContractAddresses: List<String>)

    /**
     * Gets a specific NFT by its contract address. Useful for checking existence or details.
     * (Optional, might not be needed immediately)
     */
    @Query("SELECT * FROM nft_cache WHERE contractAddress = :contractAddress LIMIT 1")
    suspend fun getNftByContractAddress(contractAddress: String): NftCacheEntity?
}
