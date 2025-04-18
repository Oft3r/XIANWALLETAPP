package net.xian.xianwalletapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import net.xian.xianwalletapp.network.XianNetworkService // Keep for now, review later
import net.xian.xianwalletapp.wallet.WalletManager // Keep for now, review later
import net.xian.xianwalletapp.ui.viewmodels.NewsViewModel
import net.xian.xianwalletapp.ui.viewmodels.NewsUiState
import net.xian.xianwalletapp.ui.viewmodels.NewsItem // Import NewsItem from viewmodels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
// Removed unused SimpleDateFormat and java.util.* imports

/**
 * Screen for displaying ecosystem news and updates fetched from Reddit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    navController: NavController,
    walletManager: WalletManager, // TODO: Review if this is needed here
    networkService: XianNetworkService, // TODO: Review if this is needed here
    newsViewModel: NewsViewModel = viewModel() // Inject ViewModel
) {
    val uiState by newsViewModel.uiState.collectAsStateWithLifecycle()
    // Link refresh state to loading state, but only show SwipeRefresh indicator
    val isRefreshing = uiState is NewsUiState.Loading
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ecosystem News") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { newsViewModel.fetchNews() }, // Trigger fetch on refresh
            modifier = Modifier.padding(paddingValues) // Apply padding from Scaffold
        ) {
            // Use Box to allow content to be scrollable even when SwipeRefresh is active
            // and to center loading/error messages
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp), // Apply horizontal padding here for content
                contentAlignment = Alignment.TopCenter // Align content to the top
            ) {
                when (val state = uiState) {
                    is NewsUiState.Loading -> {
                        // Show loading indicator centered only if not triggered by swipe refresh
                        if (!swipeRefreshState.isSwipeInProgress) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        // Keep the Box structure so SwipeRefresh works correctly even while loading
                    }
                    is NewsUiState.Success -> {
                        if (state.newsItems.isEmpty()) {
                            Text(
                                text = "No news available at the moment.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            // Column for title + list structure
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Latest Updates from r/xiannetwork",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 16.dp) // Add padding around title
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 16.dp) // Padding at the bottom of the list
                                ) {
                                    items(state.newsItems) { newsItem ->
                                        NewsCard(newsItem)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    is NewsUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize(), // Fill the Box
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Error: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { newsViewModel.fetchNews() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Removed redundant NewsItem data class definition

/**
 * Composable for displaying a news card
 */
@Composable
fun NewsCard(newsItem: NewsItem) {
    val context = LocalContext.current // Get context for Intent

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = newsItem.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = newsItem.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Published: ${newsItem.date}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newsItem.link))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Handle case where no browser is available or link is invalid
                        // You could show a Toast message here
                        // Log.e("NewsCard", "Could not open link: ${newsItem.link}", e)
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Read More on Reddit") // Updated button text
            }
        }
    }
}