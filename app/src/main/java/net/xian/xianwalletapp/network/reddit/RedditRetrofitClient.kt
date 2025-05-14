package net.xian.xianwalletapp.network.reddit

import net.xian.xianwalletapp.network.reddit.RedditApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RedditRetrofitClient {

    private const val REDDIT_BASE_URL = "https://www.reddit.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Or HttpLoggingInterceptor.Level.NONE for production
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: RedditApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(REDDIT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(RedditApiService::class.java)
    }
}
