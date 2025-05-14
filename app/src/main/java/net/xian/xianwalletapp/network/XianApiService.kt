package net.xian.xianwalletapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET // Added import for @GET

// Define el cuerpo de la solicitud para GraphQL
data class GraphQLQuery(val query: String, val variables: Map<String, Any>? = null)

// Define el cuerpo de la respuesta para el health check
data class HealthResponse(val status: String?) // Basic definition

interface XianApiService {
    @POST("graphql") // Changed from "graphiql" to "graphql"
    suspend fun getTransactions(@Body query: GraphQLQuery): Response<GraphQLResponse>

    @GET("health")
    suspend fun checkHealth(): Response<HealthResponse>
}
