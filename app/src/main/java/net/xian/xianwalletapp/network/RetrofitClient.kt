package net.xian.xianwalletapp.network

import net.xian.xianwalletapp.network.reddit.RedditApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object RetrofitClient {

    private const val REDDIT_BASE_URL = "https://www.reddit.com/"

    // Create a logging interceptor (optional, useful for debugging)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
    }

    // Create an OkHttpClient with the interceptor
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // Create a Retrofit instance for Reddit API
    private val redditRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(REDDIT_BASE_URL)
            .client(okHttpClient) // Use the custom OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Provide an instance of the RedditApiService
    val redditApiService: RedditApiService by lazy {
        redditRetrofit.create(RedditApiService::class.java)
    }

    // TODO: Add instance for XianNetworkService if needed, or integrate existing setup
    // It seems there might be another network service (XianNetworkService).
    // This setup should be reviewed to see if a single Retrofit instance or
    // dependency injection framework (like Hilt or Koin) would be better.
}