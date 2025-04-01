package com.example.xianwalletapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xianwalletapp.network.RetrofitClient
import com.example.xianwalletapp.network.reddit.RedditPostData
// Removed import from ui.screens - NewsItem is now defined below
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// Define UiState for better state management
sealed class NewsUiState {
    object Loading : NewsUiState()
    data class Success(val newsItems: List<NewsItem>) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
}

/**
 * Data class representing a news item for the UI, now defined here.
 */
data class NewsItem(
    val title: String,
    val description: String,
    val date: String, // Formatted date string
    val link: String // Full URL
)

class NewsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState

    private val redditApiService = RetrofitClient.redditApiService

    init {
        fetchNews()
    }

    fun fetchNews() {
        _uiState.value = NewsUiState.Loading
        viewModelScope.launch {
            try {
                val response = redditApiService.getNews()
                val filteredPosts = response.data.children
                    .map { it.data }
                    .filter { it.author == "lorythril" && it.removedByCategory == null }
                    .map { mapRedditPostToNewsItem(it) }

                _uiState.value = NewsUiState.Success(filteredPosts)
            } catch (e: Exception) {
                // Log the exception for debugging
                // Log.e("NewsViewModel", "Error fetching news", e)
                _uiState.value = NewsUiState.Error("Failed to load news: ${e.localizedMessage}")
            }
        }
    }

    private fun mapRedditPostToNewsItem(post: RedditPostData): NewsItem {
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(post.createdUtc * 1000)) // Convert UTC seconds to milliseconds

        // Basic text processing (truncation) - more advanced processing can be added
        val processedText = processText(post.selftext.take(250)) // Truncate description

        return NewsItem(
            title = post.title,
            description = if (post.selftext.length > 250) "$processedText..." else processedText,
            date = formattedDate,
            link = "https://www.reddit.com${post.permalink}" // Construct full link
        )
    }

    // Basic text processing - similar to the JS version but simplified for now
    // TODO: Implement more robust Markdown/URL to clickable text conversion if needed
    private fun processText(text: String): String {
        // Basic placeholder for processing, just returns text for now
        // Could add regex for links later
        return text.replace("\n", " ") // Replace newlines with spaces for preview
    }
}