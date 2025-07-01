package net.xian.xianwalletapp.data

import java.time.Instant // Use java.time for modern date/time handling

/**
 * Represents a single transaction record stored locally.
 */
data class LocalTransactionRecord(
    val timestamp: Long = Instant.now().toEpochMilli(), // Store as milliseconds since epoch
    val type: String, // e.g., "Sent", "Received"
    val amount: String,
    val symbol: String,
    val recipient: String? = null, // Null if received
    val sender: String? = null, // Null if sent (use own address)
    val txHash: String,
    val contract: String
)