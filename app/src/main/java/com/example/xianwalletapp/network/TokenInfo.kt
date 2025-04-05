package com.example.xianwalletapp.network

/**
 * Data class to hold token information
 */
data class TokenInfo(
    val name: String,
    val symbol: String,
    val decimals: Int = 8,
    val contract: String,
    val logoUrl: String? = null // Added optional logo URL
)