package net.xian.xianwalletapp.network

/**
 * Data class to represent the result of a transaction on the Xian network
 * 
 * @property txHash The transaction hash if the transaction was successful
 * @property success Whether the transaction was successful
 * @property errors Any error messages if the transaction failed
 * @property status The status of the transaction (pending, confirmed, failed)
 */
data class TransactionResult(
    val txHash: String = "",
    val success: Boolean = false,
    val errors: String = "",
    val status: String = "pending"
)