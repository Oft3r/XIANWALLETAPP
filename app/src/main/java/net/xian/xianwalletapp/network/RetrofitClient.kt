package net.xian.xianwalletapp.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // IMPORTANT: Replace this with your actual GraphQL endpoint URL
    // Example: private const val BASE_URL = "https://api.xian.net/"
    private const val BASE_URL = "https://node.xian.org/" // Changed to host only

    // Create a logging interceptor (optional, useful for debugging)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
    }

    // Create an OkHttpClient with the interceptor
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Provide an instance of the XianApiService
    val instance: XianApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL) // Base URL must end with '/'
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // For GSON deserialization
            .build()
        retrofit.create(XianApiService::class.java)
    }
}