package net.xian.xianwalletapp.network

import com.google.gson.JsonElement // Added import

// Root response structure
data class GraphQLResponse(
    val data: AllStateChangesData?
)

data class AllStateChangesData(
    val allStateChanges: AllStateChangesConnection?
)

data class AllStateChangesConnection(
    val edges: List<EdgeNode>?
)

data class EdgeNode(
    val node: StateChangeNodeData? // Changed from StateChangeNode
)

// Represents the 'node' within an edge, containing value and transaction details
data class StateChangeNodeData(
    val value: JsonElement?, // Changed from String? to JsonElement?
    val transactionByTxHash: NetworkTransactionDetails?
)

// Represents the 'transactionByTxHash' object, now primarily holding jsonContent
data class NetworkTransactionDetails(
    // Removed direct fields like blockTime, blockHeight, contract, stamps, success, function, hash
    // These will now be inside JsonContentData
    val jsonContent: JsonContentData?
)

// Represents the main 'jsonContent' object
data class JsonContentData(
    val b_meta: BMetaData?,
    val payload: PayloadData?,
    val metadata: SignatureMetaData?, // Assuming 'metadata' contains signature
    val tx_result: TxResultData?,
    val stamp_rewards_amount: String?,
    val stamp_rewards_contract: String?
)

data class BMetaData(
    val hash: String?,
    val nanos: String?, // Timestamp in nanoseconds
    val height: String?,
    val chain_id: String?
)

data class PayloadData(
    val nonce: String?,
    val kwargs: Map<String, Any?>?, // Using Map for dynamic kwargs
    val sender: String?,
    val chain_id: String?,
    val contract: String?,
    val function: String?,
    val stamps_supplied: String?
)

// Represents the 'metadata' object within jsonContent (likely for signature)
data class SignatureMetaData(
    val signature: String?
)

data class TxResultData(
    val hash: String?, // Transaction execution hash
    val state: List<StateEntryData>?,
    val events: List<EventData>?,
    val result: String?, // Can be complex, sometimes error messages
    val status: String?, // "0" for success, "1" for error (based on example)
    val rewards: RewardsData?,
    val stamps_used: String?
)

data class StateEntryData(
    val key: String?,
    val value: Any? // Value can be of various types
)

data class EventData(
    val data: Map<String, Any?>?, // Dynamic data within an event
    val event: String?, // Event name, e.g., "Transfer"
    val caller: String?,
    val signer: String?, // Often the same as payload.sender
    val contract: String?, // Contract emitting the event (token contract for Transfers)
    val data_indexed: Map<String, Any?>? // Indexed event data
)

data class RewardsData(
    val developer_reward: Map<String, String>?,
    val foundation_reward: Map<String, String>?,
    val masternode_reward: Map<String, String>?
)
