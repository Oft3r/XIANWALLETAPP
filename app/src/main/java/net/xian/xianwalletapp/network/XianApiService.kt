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

interface XianApiService {
    @POST("graphql") // Changed from "graphiql" to "graphql"
    suspend fun getTransactions(@Body query: GraphQLQuery): Response<GraphQLResponse>

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
}
