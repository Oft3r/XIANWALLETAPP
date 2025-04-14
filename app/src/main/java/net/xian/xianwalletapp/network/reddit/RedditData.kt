package net.xian.xianwalletapp.network.reddit

import com.google.gson.annotations.SerializedName

data class RedditListingResponse(
    val data: RedditListingData
)

data class RedditListingData(
    val children: List<RedditChild>
)

data class RedditChild(
    val data: RedditPostData
)

data class RedditPostData(
    val author: String,
    val title: String,
    val selftext: String,
    @SerializedName("created_utc") val createdUtc: Long,
    val permalink: String,
    @SerializedName("removed_by_category") val removedByCategory: String? // Can be null if not removed
)