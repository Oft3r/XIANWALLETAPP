package net.xian.xianwalletapp.network

/**
 * Data class representing information about an NFT.
 */
data class NftInfo(
    val contractAddress: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val viewUrl: String
)