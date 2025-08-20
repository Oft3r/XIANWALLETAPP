package net.xian.xianwalletapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET // Added import for @GET
import retrofit2.http.Path
import retrofit2.http.Query

// Define el cuerpo de la solicitud para GraphQL
data class GraphQLQuery(val query: String, val variables: Map<String, Any>? = null)

// Define el cuerpo de la respuesta para el health check
data class HealthResponse(val status: String?) // Basic definition

// Data classes for holders API response
data class HoldersPagination(
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class HoldersResponse(
    val pagination: HoldersPagination,
    val holders: List<Any> // We only need the pagination.total, so holders can be Any
)

// Data classes for token total supply API response
data class TokenSupplyResponse(
    val total_supply: String? // The total supply value from the API
)

// Data class for 24h volume response
data class Volume24hResponse(
    val pairId: String,
    val token: String,
    val volume24h: Double
)

// Data class for 24h price change response
data class PriceChange24hResponse(
    val pairId: String,
    val token: String,
    val priceNow: Double,
    val price24hAgo: Double,
    val changePct: Double
)

// Data classes for recent trades (last N)
data class TradeItem(
    val created: String,
    val side: String,     // "buy" or "sell"
    val amount: Double,   // base amount
    val amount1: Double,  // quote amount (USDC)
    val price: Double,
    val token: String
)

data class TradesResponse(
    val pairId: String,
    val token: String,
    val trades: List<TradeItem>
    // pagination exists in API but is not required here; extra fields will be ignored by Gson
)
interface XianApiService {
    @POST("graphql") // Changed from "graphiql" to "graphql"
    suspend fun getTransactions(@Body query: GraphQLQuery): Response<GraphQLResponse>
    
    @POST("graphql")
    suspend fun getTokenTransactions(@Body query: GraphQLQuery): Response<AllTransactionsResponse>

    @GET("health")
    suspend fun checkHealth(): Response<HealthResponse>

    @GET("tokens/{contractName}/holders")
    suspend fun getTokenHolders(
        @Path("contractName") contractName: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 10
    ): Response<HoldersResponse>

    @GET("tokens/{contractName}")
    suspend fun getTokenInfo(
        @Path("contractName") contractName: String
    ): Response<TokenSupplyResponse>

    @GET("https://xian-api.poc.workers.dev/token/{contractName}/balance/{address}")
    suspend fun getTokenBalance(
        @Path("contractName") contractName: String,
        @Path("address") address: String
    ): Response<TokenBalanceResponse>

    @GET("https://xian-api.poc.workers.dev/pairs/{pairId}/volume24h")
    suspend fun getPairVolume24h(
        @Path("pairId") pairId: String,
        @Query("token") token: String,
        @Query("ts") ts: Long
    ): Response<Volume24hResponse>

    @GET("https://xian-api.poc.workers.dev/pairs/{pairId}/pricechange24h")
    suspend fun getPairPriceChange24h(
        @Path("pairId") pairId: String,
        @Query("token") token: String,
        @Query("ts") ts: Long
    ): Response<PriceChange24hResponse>
@GET("https://xian-api.poc.workers.dev/pairs/{pairId}/trades")
    suspend fun getPairTrades(
        @Path("pairId") pairId: String,
        @Query("token") token: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("ts") ts: Long
    ): Response<TradesResponse>
}
