package net.xian.xianwalletapp.data

import android.util.Log
import java.math.BigDecimal
import net.xian.xianwalletapp.network.GraphQLQuery
import net.xian.xianwalletapp.network.XianApiService
import net.xian.xianwalletapp.network.GraphQLResponse // This should now refer to the one in NetworkTransactionModels.kt
import net.xian.xianwalletapp.network.NetworkTransactionDetails // Ensure this is imported if not nested directly under GraphQLResponse
import net.xian.xianwalletapp.network.StateChangeNodeData // Import this if needed for nodeValue type

class TransactionRepository(private val apiService: XianApiService) {

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
                first: 20, # Limit to the first 10 results
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
}
