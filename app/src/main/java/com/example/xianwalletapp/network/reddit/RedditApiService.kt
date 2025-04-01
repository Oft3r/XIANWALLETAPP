package com.example.xianwalletapp.network.reddit

import retrofit2.http.GET

interface RedditApiService {
    @GET("r/xiannetwork.json") // Relative URL to the base URL
    suspend fun getNews(): RedditListingResponse
}