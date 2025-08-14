package net.xian.xianwalletapp.data

import android.util.Log
import java.math.BigDecimal
import net.xian.xianwalletapp.network.GraphQLQuery
import net.xian.xianwalletapp.network.XianApiService
import net.xian.xianwalletapp.network.GraphQLResponse // This should now refer to the one in NetworkTransactionModels.kt
import net.xian.xianwalletapp.network.NetworkTransactionDetails // Ensure this is imported if not nested directly under GraphQLResponse
import net.xian.xianwalletapp.network.StateChangeNodeData // Import this if needed for nodeValue type
import net.xian.xianwalletapp.network.AllTransactionsResponse // Import for token transactions
import net.xian.xianwalletapp.network.TransactionNodeData // Import for token transaction node data

class TransactionRepository(private val apiService: XianApiService) {

    // Fetches token-specific transactions from the network
    suspend fun getTokenTransactions(userAddress: String, tokenContract: String): List<LocalTransactionRecord> {
        // Construct the GraphQL query for token-specific transactions
        val query = """
            query TokenTransactions {
              allTransactions(
                condition: { success: true }
                filter: {
                  contract: { equalTo: "$tokenContract" }
                  and: { sender: { equalTo: "$userAddress" } }
                }
                orderBy: BLOCK_TIME_DESC
                first: 15
              ) {
                edges {
                  node {
                    sender
                    function
                    created
                    jsonContent
                  }
                }
              }
            }
        """

        return try {
            val response = apiService.getTokenTransactions(GraphQLQuery(query))
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.data?.allTransactions?.edges?.mapNotNull { edge ->
                    edge.node?.let { networkTx ->
                        mapTokenNetworkToLocalRecord(networkTx, userAddress, tokenContract)
                    }
                } ?: emptyList()
            } else {
                Log.e("TransactionRepository", "Error fetching token transactions: ${response.code()} - ${response.message()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Exception fetching token transactions", e)
            emptyList()
        }
    }

    // Fetches transactions from the network and maps them to LocalTransactionRecord
    suspend fun getNetworkTransactions(userAddress: String): List<LocalTransactionRecord> {
        // Construct the GraphQL query
        // IMPORTANT: This query is based on the example and might need adjustments
        // especially for filtering by userAddress and getting necessary transaction details (amount, recipient, etc.)
        // You will likely need to expand the 'transactionByTxHash' part to include
        // transaction arguments or payload to get amount, recipient, and sender.
        val query = """
            query Txs {
              allStateChanges(
                first: 30, # Limit to the first 30 results
                filter: {
                  # Assuming 'key' can be used for sender or receiver.
                  # This might need to be more specific if 'key' only refers to one side.
                  # Or you might need to query twice, once for sent and once for received,
                  # or have a more complex filter if your GraphQL API supports it.
                  key: { includes: "$userAddress" } 
                  txHash: { notEqualTo: "GENESIS" }
                }
                orderBy: CREATED_DESC # Ensure this is a valid field name for ordering, e.g., blockTime might be BLOCK_TIME
              ) {
                edges {
                  node {
                    value # The state value, might be useful for context later
                    transactionByTxHash {
                      jsonContent # Requesting the full jsonContent
                    }
                  }
                }
              }
            }
        """

        return try {
            // Ensure RetrofitClient.instance is correctly providing the apiService
            val response = apiService.getTransactions(GraphQLQuery(query))
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.data?.allStateChanges?.edges?.mapNotNull { edge ->
                    edge.node?.transactionByTxHash?.let { networkTx ->
                        mapNetworkToLocalRecord(networkTx, userAddress, edge.node.value)
                    }
                } ?: emptyList()
            } else {
                Log.e("TransactionRepository", "Error fetching transactions: ${response.code()} - ${response.message()} - ${response.errorBody()?.string()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Exception fetching transactions", e)
            emptyList()
        }
    }

    private fun mapContractToSymbol(contractName: String?): String {
        return when (contractName?.lowercase()) {
            "currency" -> "XIAN"
            "con_usdc" -> "USDC"
            "con_poop_coin" -> "POOP"
            "con_multisend" -> "MULTI" // Example, adjust as needed
            "con_dex_v2" -> "DEX" // Or more specific if events don't clarify token
            "con_dex_noob_wrapper" -> "DEXW"
            // Add more known contract to symbol mappings here
            else -> contractName?.split("_")?.lastOrNull()?.uppercase()?.replaceFirst("CON", "") ?: "TOKEN"
        }
    }

    private fun mapNetworkToLocalRecord(
        networkTxDetails: NetworkTransactionDetails,
        currentUserAddress: String,
        nodeValue: com.google.gson.JsonElement? // Type is already JsonElement
    ): LocalTransactionRecord? { // Return nullable if tx can't be meaningfully parsed
        val jsonContent = networkTxDetails.jsonContent ?: return null

        // Convert nodeValue (JsonElement) to String if it's a primitive, otherwise handle as needed
        // For now, we'll just log if it's not a string, as the mapping logic doesn't use it yet.
        val nodeValueString: String? = if (nodeValue?.isJsonPrimitive == true && nodeValue.asJsonPrimitive.isString) {
            nodeValue.asString
        } else {
            if (nodeValue != null) {
                Log.w("TransactionRepository", "Node value is not a simple string: ${nodeValue}")
            }
            null
        }

        val timestampMillis = try {
            jsonContent.b_meta?.nanos?.let { BigDecimal(it).divide(BigDecimal("1000000")).toLong() }
                ?: System.currentTimeMillis()
        } catch (e: NumberFormatException) {
            Log.e("TransactionRepository", "Error parsing blockTime (nanos): ${jsonContent.b_meta?.nanos}", e)
            System.currentTimeMillis()
        }

        val txHashDisplay = jsonContent.tx_result?.hash ?: jsonContent.b_meta?.hash ?: "N/A"
        val payloadContract = jsonContent.payload?.contract ?: "Unknown Contract"
        val payloadFunction = jsonContent.payload?.function ?: "Unknown Function"
        val payloadSender = jsonContent.payload?.sender // The address that signed/sent the transaction

        var inferredType = payloadFunction // Default type to function name
        var inferredAmount = "0.00"
        var inferredSymbol = mapContractToSymbol(payloadContract) // Default symbol from payload contract
        var inferredRecipient: String? = payloadContract // Default recipient to payload contract
        var inferredSender: String? = payloadSender

        val events = jsonContent.tx_result?.events ?: emptyList()

        // Prioritize "Transfer" events for amount, symbol, sender, recipient, type
        val userTransferEvents = events.filter { event ->
            event.event == "Transfer" &&
            (event.data_indexed?.get("from") == currentUserAddress || event.data_indexed?.get("to") == currentUserAddress)
        }

        if (userTransferEvents.isNotEmpty()) {
            // If it's a simple transfer involving the user directly
            if (payloadFunction.equals("transfer", ignoreCase = true) && userTransferEvents.size == 1) {
                val event = userTransferEvents.first()
                val eventFrom = event.data_indexed?.get("from") as? String
                val eventTo = event.data_indexed?.get("to") as? String
                val eventAmount = event.data?.get("amount") as? String ?: "0.00"
                val eventContract = event.contract

                inferredAmount = eventAmount
                inferredSymbol = mapContractToSymbol(eventContract)
                inferredSender = eventFrom
                inferredRecipient = eventTo

                if (eventFrom == currentUserAddress) {
                    inferredType = "Sent"
                } else if (eventTo == currentUserAddress) {
                    inferredType = "Received"
                }
            } else if (payloadFunction.startsWith("swap", ignoreCase = true) || payloadContract.contains("dex", ignoreCase = true) ) {
                // Handle swaps: try to find what the user received or sent
                inferredType = "Swap" // General type for swap

                // Token received by user
                val receivedEvent = userTransferEvents.find { it.data_indexed?.get("to") == currentUserAddress }
                // Token sent by user
                val sentEvent = userTransferEvents.find { it.data_indexed?.get("from") == currentUserAddress }

                if (receivedEvent != null) {
                    inferredAmount = receivedEvent.data?.get("amount") as? String ?: (jsonContent.tx_result?.result ?: "0.00")
                    inferredSymbol = mapContractToSymbol(receivedEvent.contract)
                    inferredSender = receivedEvent.data_indexed?.get("from") as? String // e.g., DEX pool
                    inferredRecipient = currentUserAddress
                    // Could add details of token sent if UI supports it
                    // Log.d("TransactionRepository", "Swap: User received ${inferredAmount} ${inferredSymbol}")
                } else if (sentEvent != null) {
                    // If we didn't find a received token, show what was sent
                    inferredAmount = sentEvent.data?.get("amount") as? String ?: "0.00"
                    inferredSymbol = mapContractToSymbol(sentEvent.contract)
                    inferredSender = currentUserAddress
                    inferredRecipient = sentEvent.data_indexed?.get("to") as? String // e.g., DEX pool
                     // Log.d("TransactionRepository", "Swap: User sent ${inferredAmount} ${inferredSymbol}")
                } else {
                    // Fallback for swaps if events are not clear, use payload kwargs if available
                    inferredAmount = jsonContent.payload?.kwargs?.get("amountIn") as? String
                                     ?: jsonContent.payload?.kwargs?.get("amount") as? String
                                     ?: jsonContent.tx_result?.result // Last resort for amount
                                     ?: "0.00"
                    // Symbol might remain the payload contract's symbol or a generic "SWAP_TOKEN"
                    // Sender and Recipient would be payloadSender and payloadContract
                }
            } else {
                // Other functions with direct user transfers (e.g., multisend where user is one of recipients/senders)
                // For simplicity, take the first relevant event. This might need more specific handling.
                val event = userTransferEvents.first()
                val eventFrom = event.data_indexed?.get("from") as? String
                val eventTo = event.data_indexed?.get("to") as? String
                inferredAmount = event.data?.get("amount") as? String ?: "0.00"
                inferredSymbol = mapContractToSymbol(event.contract)
                inferredSender = eventFrom
                inferredRecipient = eventTo
                if (eventFrom == currentUserAddress) {
                    inferredType = "Sent (${payloadFunction})"
                } else if (eventTo == currentUserAddress) {
                    inferredType = "Received (${payloadFunction})"
                }
            }
        } else if (payloadSender == currentUserAddress) {
            // If no direct "Transfer" events involving user, but user is the sender of the payload
            // This could be a contract interaction like staking, voting, or a failed transfer with no events.
            inferredType = payloadFunction // e.g., "stake", "vote"
            inferredSender = currentUserAddress
            inferredRecipient = payloadContract
            // Amount and symbol might be harder to determine here without specific events
            // Check kwargs for clues
            inferredAmount = jsonContent.payload?.kwargs?.get("amount") as? String ?: "0.00"
            // Symbol might remain the payload contract's symbol
        } else {
            // User is involved (due to GraphQL filter 'key:includes') but not as primary sender in payload
            // nor in any direct transfer events. This could be a contract paying out to the user,
            // or a more complex interaction.
            // Log this case for further analysis.
            Log.i("TransactionRepository", "Complex transaction for $currentUserAddress: tx $txHashDisplay, function $payloadFunction. No direct user transfer events found, payload sender is $payloadSender.")
            // Keep default inferred values or mark as "Interaction"
            inferredType = "Interaction: $payloadFunction"
        }
        
        // Check transaction success
        val txSuccess = jsonContent.tx_result?.status == "0"
        if (!txSuccess) {
            inferredType = "Failed: $inferredType"
            // Optionally, you could clear amount for failed transactions or show it differently
            // inferredAmount = "0.00" 
        }

        return LocalTransactionRecord(
            timestamp = timestampMillis,
            type = inferredType.take(30), // Truncate type if too long
            amount = inferredAmount,
            symbol = inferredSymbol,
            recipient = inferredRecipient,
            sender = inferredSender,
            txHash = txHashDisplay,
            contract = payloadContract // Store the main contract from payload
        )
    }

    private fun mapTokenNetworkToLocalRecord(
        networkTxData: TransactionNodeData,
        currentUserAddress: String,
        tokenContract: String
    ): LocalTransactionRecord? {
        val jsonContent = networkTxData.jsonContent ?: return null

        // Parse timestamp from multiple sources with fallbacks
        val timestampMillis = try {
            // First try to get timestamp from b_meta.nanos (most accurate)
            jsonContent.b_meta?.nanos?.let { nanosStr ->
                try {
                    val nanos = nanosStr.toLong()
                    nanos / 1_000_000 // Convert nanoseconds to milliseconds
                } catch (e: NumberFormatException) {
                    Log.w("TransactionRepository", "Failed to parse nanos: $nanosStr", e)
                    null
                }
            } ?: 
            // Fallback to created field
            networkTxData.created?.let { createdStr ->
                try {
                    // Try different date formats
                    when {
                        createdStr.contains("T") -> {
                            // ISO format: 2024-01-15T10:30:45Z or 2024-01-15T10:30:45.123Z
                            java.time.Instant.parse(createdStr).toEpochMilli()
                        }
                        createdStr.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) -> {
                            // SQL format: 2024-01-15 10:30:45
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            java.time.LocalDateTime.parse(createdStr, formatter)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        createdStr.matches(Regex("\\d+")) -> {
                            // Unix timestamp (seconds or milliseconds)
                            val timestamp = createdStr.toLong()
                            if (timestamp > 1_000_000_000_000L) {
                                // Already in milliseconds
                                timestamp
                            } else {
                                // In seconds, convert to milliseconds
                                timestamp * 1000
                            }
                        }
                        else -> {
                            Log.w("TransactionRepository", "Unknown date format: $createdStr")
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TransactionRepository", "Failed to parse created timestamp: $createdStr", e)
                    null
                }
            } ?: System.currentTimeMillis() // Final fallback
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error parsing timestamp", e)
            System.currentTimeMillis()
        }

        // Debug logging for timestamp parsing
        Log.d("TransactionRepository", "Token transaction - created: ${networkTxData.created}, b_meta.nanos: ${jsonContent.b_meta?.nanos}, final timestamp: $timestampMillis")

        val txHashDisplay = jsonContent.tx_result?.hash ?: jsonContent.b_meta?.hash ?: "N/A"
        val payloadContract = jsonContent.payload?.contract ?: tokenContract
        val payloadFunction = jsonContent.payload?.function ?: "Unknown Function"
        val payloadSender = jsonContent.payload?.sender

        var inferredType = payloadFunction
        var inferredAmount = "0.00"
        var inferredSymbol = mapContractToSymbol(tokenContract)
        var inferredRecipient: String? = null
        var inferredSender: String? = payloadSender

        // Extract amount and recipient from payload kwargs
        val kwargs = jsonContent.payload?.kwargs
        if (kwargs != null) {
            // Try to get amount from various possible fields
            inferredAmount = (kwargs["amount"] as? String) 
                ?: (kwargs["amountIn"] as? String)
                ?: (kwargs["amountOut"] as? String)
                ?: "0.00"

            // Try to get recipient from various possible fields
            inferredRecipient = (kwargs["to"] as? String)
                ?: (kwargs["recipient"] as? String)
                ?: payloadContract
        }

        // Analyze events for more accurate information
        val events = jsonContent.tx_result?.events ?: emptyList()
        val transferEvents = events.filter { event ->
            event.event == "Transfer" && event.contract == tokenContract
        }

        if (transferEvents.isNotEmpty()) {
            val transferEvent = transferEvents.first()
            val eventFrom = transferEvent.data_indexed?.get("from") as? String
            val eventTo = transferEvent.data_indexed?.get("to") as? String
            val eventAmount = transferEvent.data?.get("amount") as? String

            if (eventAmount != null) {
                inferredAmount = eventAmount
            }

            inferredSender = eventFrom
            inferredRecipient = eventTo

            // Determine transaction type based on user involvement
            inferredType = when {
                eventFrom == currentUserAddress && eventTo != currentUserAddress -> "Sent"
                eventTo == currentUserAddress && eventFrom != currentUserAddress -> "Received"
                eventFrom == currentUserAddress && eventTo == currentUserAddress -> "Self Transfer"
                else -> payloadFunction
            }
        } else {
            // No transfer events, determine type based on function and sender
            inferredType = when {
                payloadSender == currentUserAddress -> when (payloadFunction.lowercase()) {
                    "transfer" -> "Sent"
                    "approve" -> "Approved"
                    "stake" -> "Staked"
                    "unstake" -> "Unstaked"
                    else -> payloadFunction
                }
                else -> payloadFunction
            }
        }

        // Filter out "approve" transactions
        if (inferredType.lowercase().contains("approve") || payloadFunction.lowercase() == "approve") {
            return null // Skip approve transactions
        }

        // Check transaction success
        val txSuccess = jsonContent.tx_result?.status == "0"
        if (!txSuccess) {
            inferredType = "Failed: $inferredType"
        }

        return LocalTransactionRecord(
            timestamp = timestampMillis,
            type = inferredType.take(30),
            amount = inferredAmount,
            symbol = inferredSymbol,
            recipient = inferredRecipient,
            sender = inferredSender,
            txHash = txHashDisplay,
            contract = tokenContract
        )
    }
}
