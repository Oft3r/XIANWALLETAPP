package net.xian.xianwalletapp.ui.screens

import android.view.ViewGroup
import android.view.View
import android.widget.Toast

import android.webkit.WebView
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL
import java.net.MalformedURLException
import android.util.Log
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language // Placeholder icon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline // Import Remove icon
import androidx.compose.material.icons.filled.Star // Import Star icon
import androidx.compose.material.icons.outlined.Star

import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.wallet.XianWebViewBridge
import net.xian.xianwalletapp.wallet.AuthRequestListener
import net.xian.xianwalletapp.data.FaviconCacheManager // Import the cache manager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
// TODO: Add import for actual Xian logo resource if available
// import net.xian.xianwalletapp.R
// import androidx.compose.ui.res.painterResource

// Helper function to normalize URLs for comparison (top-level)
private fun normalizeUrlForComparison(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        var tempUrl = url.trim().lowercase()
        if (!tempUrl.startsWith("http://") && !tempUrl.startsWith("https://")) {
            tempUrl = "https://$tempUrl"
        }
        // Use Java URL class to handle potential parsing complexities and get host
        val parsedUrl = URL(tempUrl)
        // Reconstruct consistently: protocol + host + path (without query/fragment for comparison)
        // Remove trailing slash from path if present and path is not just "/"
        var path = parsedUrl.path?.takeIf { it.isNotEmpty() } ?: "/"
        if (path.length > 1 && path.endsWith('/')) {
            path = path.dropLast(1)
        }
        "${parsedUrl.protocol}://${parsedUrl.host}$path"
    } catch (e: MalformedURLException) {
        Log.w("WebBrowserScreen", "Could not normalize URL: $url", e)
        url // Return original on error
    }
}


// Helper data class to hold prompt request details
data class JsPromptRequest(
    val message: String,
    val defaultValue: String,
    val result: JsPromptResult
)

// Helper function to fetch and parse HTML for favicon URL (runs in background)
private suspend fun fetchFaviconUrl(baseUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(baseUrl).get()
            // Look for standard icon links
            var iconLink = doc.select("link[rel~=(?i)^(shortcut|icon)$]").first()?.attr("href")

            // If not found, sometimes it's apple-touch-icon
            if (iconLink.isNullOrBlank()) {
                iconLink = doc.select("link[rel=apple-touch-icon]").first()?.attr("href")
            }

            if (iconLink.isNullOrBlank()) {
                // Last resort: try default /favicon.ico
                try {
                    // Check if default exists without downloading full HTML again (less reliable)
                    val defaultIcoUrl = URL(URL(baseUrl), "/favicon.ico").toString()
                    // A more robust check would involve an HTTP HEAD request here
                    // For simplicity, we'll just assume it might exist if other links don't
                    return@withContext defaultIcoUrl // Return the guessed default URL
                } catch (e: MalformedURLException) {
                     Log.e("FaviconFetch", "Malformed base URL for default check: $baseUrl", e)
                    return@withContext null // Cannot construct default URL
                }
            }

            // Construct absolute URL if the found link is relative
            val absoluteIconUrl = try {
                 URL(URL(baseUrl), iconLink).toString()
            } catch (e: MalformedURLException) {
                 Log.e("FaviconFetch", "Malformed URL found: base=$baseUrl, icon=$iconLink", e)
                 null
            }
            absoluteIconUrl

        } catch (e: Exception) { // Catch network or parsing errors
            Log.e("FaviconFetch", "Error fetching favicon for $baseUrl: ${e.message}")
            null
        }
    }
}
// Data class for XApp shortcuts
data class XAppInfo(
    val name: String,
    val url: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector, // Placeholder icon
    val faviconUrl: String? = null // Optional MANUAL favicon URL
)

// --- Dashboard Composable ---
@Composable
fun DashboardContent(
    mainApps: List<XAppInfo>,
    favoriteApps: List<XAppInfo>,
    onShortcutClick: (String) -> Unit,
    onRemoveFavoriteClick: (XAppInfo) -> Unit, // Add callback for removing favorites
    faviconCacheManager: FaviconCacheManager // Add cache manager parameter
) {
    // State to hold the dynamically fetched favicon URLs, keyed by the app's main URL
    val faviconUrls = remember { mutableStateMapOf<String, String?>() }

    // Main Column to hold both sections
    Column(
        modifier = Modifier.fillMaxSize() // Remove verticalScroll from the outer Column
    ) {
        // --- Main XApps Section ---
        Text(
            text = "Main XApps",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp), // Adjust minSize as needed
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth() // Grid takes available width, height wraps content
        ) {
            items(mainApps) { app -> // Use mainApps list here
                // Fetch favicon URL: Check cache first, then fetch if needed
                LaunchedEffect(app.url, faviconCacheManager) { // Add cache manager as key
                    if (app.faviconUrl == null && !faviconUrls.containsKey(app.url)) {
                        // 1. Check cache
                        val cachedUrl = faviconCacheManager.getFaviconUrl(app.url)
                        if (cachedUrl != null) {
                            faviconUrls[app.url] = cachedUrl // Use cached URL
                            Log.d("FaviconCache", "Using cached favicon for ${app.url}")
                        } else {
                            // 2. Fetch if not in cache
                            Log.d("FaviconCache", "Fetching favicon for ${app.url}")
                            faviconUrls[app.url] = null // Mark as fetching/default
                            val fetchedUrl = fetchFaviconUrl(app.url)
                            if (fetchedUrl != null) {
                                faviconUrls[app.url] = fetchedUrl // Update state
                                // 3. Save to cache after successful fetch
                                faviconCacheManager.saveFaviconUrl(app.url, fetchedUrl)
                                Log.d("FaviconCache", "Fetched and cached favicon for ${app.url}")
                            } else {
                                // Fetch failed, state remains null (triggers fallback)
                                Log.w("FaviconCache", "Failed to fetch favicon for ${app.url}")
                            }
                        }
                    } else if (app.faviconUrl != null) {
                        // If manual faviconUrl is provided, ensure it's in the state map
                        // (though Coil likely handles this fine, this ensures consistency if needed elsewhere)
                        faviconUrls[app.url] = app.faviconUrl
                    }
                }

                Column(
                    modifier = Modifier
                        .clickable { onShortcutClick(app.url) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val placeholderPainter = rememberVectorPainter(image = app.icon)
                    // Prioritize manual URL, then fetched URL, then placeholder
                    val imageUrl = app.faviconUrl ?: faviconUrls[app.url]

                    Image(
                        painter = rememberAsyncImagePainter(
                            model = imageUrl, // Use manual or fetched URL
                            placeholder = placeholderPainter,
                            error = placeholderPainter // Fallback to placeholder on error or if URL is null
                        ),
                        contentDescription = app.name,
                        modifier = Modifier.size(64.dp) // Icon size
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = app.name,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp, // Text size
                        maxLines = 1 // Ensure text doesn't wrap excessively
                    )
                }
            }
        } // End LazyVerticalGrid for Main XApps

        // --- Favorite XApps Section ---
        Text(
            text = "Favorite XApps", // New title
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp) // Added top padding
        )
        // TODO: Add LazyVerticalGrid or similar here for favorite apps later
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth() // Grid takes available width, height wraps content
                .heightIn(max = 300.dp) // Limit height to avoid pushing things too far, adjust as needed
        ) {
            items(favoriteApps) { app ->
                // LaunchedEffect for favicon fetching (Apply same cache logic as main apps)
                LaunchedEffect(app.url, faviconCacheManager) { // Add cache manager as key
                    if (app.faviconUrl == null && !faviconUrls.containsKey(app.url)) {
                        // 1. Check cache
                        val cachedUrl = faviconCacheManager.getFaviconUrl(app.url)
                        if (cachedUrl != null) {
                            faviconUrls[app.url] = cachedUrl // Use cached URL
                            Log.d("FaviconCache", "[Fav] Using cached favicon for ${app.url}")
                        } else {
                            // 2. Fetch if not in cache
                            Log.d("FaviconCache", "[Fav] Fetching favicon for ${app.url}")
                            faviconUrls[app.url] = null // Mark as fetching/default
                            val fetchedUrl = fetchFaviconUrl(app.url)
                            if (fetchedUrl != null) {
                                faviconUrls[app.url] = fetchedUrl // Update state
                                // 3. Save to cache after successful fetch
                                faviconCacheManager.saveFaviconUrl(app.url, fetchedUrl)
                                Log.d("FaviconCache", "[Fav] Fetched and cached favicon for ${app.url}")
                            } else {
                                // Fetch failed, state remains null (triggers fallback)
                                Log.w("FaviconCache", "[Fav] Failed to fetch favicon for ${app.url}")
                            }
                        }
                    } else if (app.faviconUrl != null) {
                        faviconUrls[app.url] = app.faviconUrl
                    }
                }

                // Box to overlay the remove button
                Box {
                    Column(
                        modifier = Modifier
                            .clickable { onShortcutClick(app.url) }
                            .padding(8.dp), // Add padding to Column so remove button doesn't overlap content too much
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val placeholderPainter = rememberVectorPainter(image = app.icon)
                        val imageUrl = app.faviconUrl ?: faviconUrls[app.url]
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = imageUrl,
                                placeholder = placeholderPainter,
                                error = placeholderPainter
                            ),
                            contentDescription = app.name,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.name,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    // Remove Button - aligned to top-end
                    IconButton(
                        onClick = { onRemoveFavoriteClick(app) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(0.dp) // Adjust padding if needed
                            .size(24.dp) // Make button small
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Remove Favorite",
                            tint = MaterialTheme.colorScheme.error // Use error color for remove
                        )
                    }
                }
            }
        }
    } // End Main Column
}

/**
 * Web Browser screen with URL address bar and WebView
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebBrowserScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    faviconCacheManager: FaviconCacheManager, // Add FaviconCacheManager parameter
// Helper function to normalize URLs for comparison
// Removed misplaced normalizeUrlForComparison function from here. It will be inserted at the top level.


    initialUrl: String? = null // Argument for initial URL
) {
    val defaultUrl = "https://xian.org"
    // Decode the initial URL if provided
    val decodedInitialUrl = remember(initialUrl) {
        initialUrl?.let {
            try {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                android.util.Log.e("WebBrowserScreen", "Failed to decode URL: $it", e)
                null // Indicate error or invalid URL passed
            }
        }
    }

    // Determine if we should show the dashboard or WebView initially
    val startUrl = decodedInitialUrl ?: defaultUrl
    val showDashboardInitially = decodedInitialUrl == null // Show dashboard only if no specific URL was passed

    // State for the URL text field (used only when WebView is visible)
    var urlInput by remember { mutableStateOf(startUrl) }
    // State for the URL currently loaded or intended for the WebView
    var currentWebViewUrl by remember { mutableStateOf(startUrl) }
    // State to control dashboard/WebView visibility
    // State to control dashboard/WebView visibility
    var showDashboard by remember { mutableStateOf(showDashboardInitially) }

    var isLoading by remember { mutableStateOf(!showDashboardInitially) } // Start loading only if showing WebView initially
    val focusManager = LocalFocusManager.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // State for managing the custom JS prompt dialog
    var showJsPromptDialog by remember { mutableStateOf(false) }
    var jsPromptRequest by remember { mutableStateOf<JsPromptRequest?>(null) } // Explicit type

    // State for the pre-authentication password dialog
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var authCallbacks by remember { mutableStateOf<Pair<((String) -> Unit), (() -> Unit)>?>(null) }
    var txDetailsForAuth by remember { mutableStateOf<String?>(null) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current // Get context for WalletManager
    // State list for favorite apps - initialized later
    val favoriteXApps = remember { mutableStateListOf<XAppInfo>() }

    // Load favorites from SharedPreferences on initial composition
    // Load favorites from SharedPreferences on initial composition or when walletManager changes
    LaunchedEffect(walletManager) { // Rerun if walletManager instance changes (though unlikely here)
        Log.d("WebBrowserScreen", "LaunchedEffect running: Loading favorites.")
        val loadedFavorites = walletManager.loadFavorites() // Call suspend function
        favoriteXApps.clear() // Clear the list first
        favoriteXApps.addAll(loadedFavorites)
        Log.d("WebBrowserScreen", "Loaded ${loadedFavorites.size} favorites into state.")
    }

    // Define the list of official XApps
    val officialXApps = listOf(
        XAppInfo(name = "Xian.org", url = "https://xian.org", icon = Icons.Default.Language, faviconUrl = "https://xian.org/assets/img/favicon.ico"), // Manual URL
        XAppInfo(name = "XNS Domains", url = "https://xns.domains/", icon = Icons.Default.Language),
        XAppInfo(name = "PixelSnek", url = "https://pixelsnek.xian.org/", icon = Icons.Default.Language),
        XAppInfo(name = "SnakeXchange", url = "https://snakexchange.org/", icon = Icons.Default.Language)
        , // Add comma for the next item
        XAppInfo(name = "Xian Block Explorer", url = "https://explorer.xian.org", icon = Icons.Default.Language, faviconUrl = "https://explorer.xian.org/img/logo.bf1eed5b.png") // Manual URL
        // Add more apps here later (just name, url, placeholder icon)
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web Browser") },
                navigationIcon = {
                    IconButton(onClick = {
                        val webView = webViewRef.value
                        if (!showDashboard) { // If WebView is currently showing
                            if (webView?.canGoBack() == true) {
                                // If WebView can go back, navigate within WebView
                                webView.goBack()
                            } else {
                                // If WebView cannot go back, show the dashboard
                                showDashboard = true
                                // Reset loading state when returning to dashboard
                                isLoading = false
                            }
                        } else {
                            // If Dashboard is showing, pop the screen
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
       ) {
            // --- Always Visible URL Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val formattedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                                "https://$urlInput"
                            } else {
                                urlInput
                            }
                            urlInput = formattedUrl // Update input field immediately
                            currentWebViewUrl = formattedUrl // Set the target URL
                            urlInput = formattedUrl // Update input field immediately
                            currentWebViewUrl = formattedUrl // Set the target URL
                            showDashboard = false // Ensure WebView is shown
                            isLoading = true // Show loading indicator
                            // loadUrl will be handled by AndroidView factory/update
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                // Removed redundant saveFavorites call here

                // Go button
                Button(
                    onClick = {
                        val formattedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                            "https://$urlInput"
                        } else {
                            urlInput
                        }
                        urlInput = formattedUrl // Update input field immediately
                        urlInput = formattedUrl // Update input field immediately
                        currentWebViewUrl = formattedUrl // Set the target URL
                        showDashboard = false // Ensure WebView is shown
                        isLoading = true // Show loading indicator
                        // loadUrl will be handled by AndroidView factory/update
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Go")
                }

                // Refresh button
                IconButton(
                    onClick = { webViewRef.value?.reload() },
                    enabled = !showDashboard // Enable only when WebView is visible
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                // Determine if the current URL (normalized) is already a favorite
                val isFavorited = remember(urlInput, favoriteXApps.size) { // Depend on size to recompose when list changes
                    val normalizedInput = normalizeUrlForComparison(urlInput)
                    val result = normalizedInput != null && favoriteXApps.any { normalizeUrlForComparison(it.url) == normalizedInput }
                    Log.d("WebBrowserScreen", "Recalculating isFavorited: urlInput='$urlInput', normalizedInput='$normalizedInput', result=$result")
                    result
                }

                // Add/Remove Favorites Toggle Button
                IconButton(
                    onClick = {
                        val originalUrl = urlInput
                        val normalizedUrl = normalizeUrlForComparison(originalUrl)

                        // Only proceed if the URL is valid and *not* already a favorite
                        if (normalizedUrl != null && !isFavorited) {
                            try {
                                // Add to favorites (use original URL for storage)
                                val urlObject = URL(originalUrl) // Validate original URL format before adding
                                val host = urlObject.host ?: originalUrl
                                val name = host.uppercase() // Simple name generation

                                favoriteXApps.add(
                                    XAppInfo(
                                        name = name,
                                        url = originalUrl, // Store the original URL
                                        icon = Icons.Default.Language // Default icon
                                    )
                                )
                                Log.d("WebBrowserScreen", "Added favorite: $originalUrl (Normalized: $normalizedUrl)")
                                // Save updated list
                                coroutineScope.launch {
                                    walletManager.saveFavorites(favoriteXApps.toList())
                                }
                                // Show feedback Toast
                                Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()

                            } catch (e: MalformedURLException) {
                                Log.e("WebBrowserScreen", "Invalid URL for favorite add: $originalUrl", e)
                                // TODO: Add user feedback (Invalid URL)
                            } catch (e: Exception) {
                                Log.e("WebBrowserScreen", "Error adding favorite: $originalUrl", e)
                                // TODO: Add user feedback (Error)
                            }
                        } else if (isFavorited) {
                             Log.d("WebBrowserScreen", "URL already favorited, doing nothing: $originalUrl")
                             // Show feedback Toast
                             Toast.makeText(context, "Already in Favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            // URL is blank or invalid for normalization
                            Log.w("WebBrowserScreen", "Attempted to add favorite with blank or invalid URL: $originalUrl")
                            Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    // Disable button slightly if already favorited? Optional.
                    // enabled = !isFavorited
                ) {
                    // Log the state right before rendering the icon
                    Log.d("WebBrowserScreen", "Rendering Icon: isFavorited = $isFavorited")
                    Icon(
                        imageVector = if (isFavorited) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (isFavorited) "Already Favorite" else "Add to Favorites", // Updated description
                        tint = if (isFavorited) MaterialTheme.colorScheme.primary else LocalContentColor.current // Highlight if favorited
                    )
                }
            }

            // --- Conditional Content (Dashboard or WebView) ---
            if (showDashboard) {
                DashboardContent(
                    mainApps = officialXApps,
                    favoriteApps = favoriteXApps,
                    onShortcutClick = { targetUrl ->
                        currentWebViewUrl = targetUrl
                        isLoading = true
                        showDashboard = false
                    },
                    faviconCacheManager = faviconCacheManager, // Pass the cache manager here
                    onRemoveFavoriteClick = { appToRemove ->
                        favoriteXApps.remove(appToRemove)
                        // Save updated list asynchronously
                        coroutineScope.launch {
                            walletManager.saveFavorites(favoriteXApps.toList())
                        }
                    }
                )
            } else {
               // WebView
                AndroidView(
                    factory = { context ->
                        // --- Create AuthRequestListener Implementation ---
                        val authListener = object : AuthRequestListener {
                            override fun requestAuth(
                                txDetailsJson: String,
                                onSuccess: (txDetailsJson: String) -> Unit,
                                onFailure: () -> Unit
                            ) {
                                // Store callbacks and details, then show dialog
                                txDetailsForAuth = txDetailsJson
                                authCallbacks = Pair(onSuccess, onFailure)
                                passwordInput = "" // Clear previous input
                                authErrorMessage = null // Clear previous error
                                showPasswordDialog = true
                            }
                        }
                        // --- End AuthRequestListener Implementation ---

                        WebView(context).apply {
                            visibility = View.INVISIBLE // Start hidden to prevent white flash
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    // isLoading is already true from the click handler/initial state
                                    view?.visibility = View.VISIBLE // Show WebView now
                                }
                                
                                // Rename lambda parameter to avoid collision with state variable 'url'
                                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                    super.onPageFinished(view, loadedUrl) // Use renamed parameter
                                    isLoading = false // Hide progress bar
                                    // Use the renamed parameter 'loadedUrl' here as well
                                    loadedUrl?.let { newUrl ->
                                        // Update the URL input field to reflect the actual loaded URL
                                        urlInput = newUrl
                                        currentWebViewUrl = newUrl
                                    }

                                    // Inject JavaScript to intercept and handle events from dapp.js
                                    val jsCode = """
                                    (function() {
                                        // Flag to track if we've already initialized
                                        if (window.xianWalletInjected) return;
                                        window.xianWalletInjected = true;

                                        // Create a debug logger
                                        window.xianDebug = function(message, data) {
                                            console.log('XIAN-DEBUG: ' + message, data);
                                            // Log to Android for debugging
                                            if (typeof XianWalletBridge !== 'undefined') {
                                                XianWalletBridge.logDebug(message + (data ? ': ' + JSON.stringify(data) : ''));
                                            }
                                        };
                                        
                                        console.log('Xian Wallet Android bridge initialized');
                                        window.xianDebug('Bridge initialization started');
                                        
                                        // Improved error handling for all callbacks
                                        window.xianHandleError = function(error, callback) {
                                            console.error('XIAN-ERROR:', error);
                                            window.xianDebug('ERROR', error);
                                            
                                            if (callback) {
                                                callback({
                                                    success: false,
                                                    error: typeof error === 'string' ? error : (error.message || 'Unknown error')
                                                });
                                            }
                                        };
                                        
                                        // Listen for wallet info requests
                                        document.addEventListener('xianWalletGetInfo', function() {
                                            window.xianDebug('xianWalletGetInfo event received');
                                            try {
                                                const walletInfo = JSON.parse(XianWalletBridge.getWalletInfo());
                                                document.dispatchEvent(new CustomEvent('xianWalletInfo', {
                                                    detail: walletInfo
                                                }));
                                            } catch (error) {
                                                window.xianHandleError(error);
                                            }
                                        });
                                        
                                        // Listen for sign message requests
                                        document.addEventListener('xianWalletSignMsg', function(event) {
                                            window.xianDebug('xianWalletSignMsg event received', event.detail);
                                            let passwordToUse = null; // Initialize password as null

                                            // Check if password was required on startup via the bridge
                                            const requirePasswordOnStartup = XianWalletBridge.isPasswordRequiredOnStartup();
                                            window.xianDebug('Password required on startup? ' + requirePasswordOnStartup);

                                            if (!requirePasswordOnStartup) {
                                                // If password NOT required on startup, prompt for it now
                                                passwordToUse = prompt('Enter your wallet password to sign the message', '');
                                                if (!passwordToUse) { // Check if user cancelled prompt
                                                    document.dispatchEvent(new CustomEvent('xianWalletSignMsgResponse', {
                                                        detail: { success: false, error: 'User cancelled the operation' }
                                                    }));
                                                    return; // Stop if user cancelled prompt
                                                }
                                                // If user entered password, passwordToUse holds it
                                            }
                                            // If password WAS required on startup, passwordToUse remains null,
                                            // signaling the bridge to try the cached key.

                                            try {
                                                // Call signMessage with the message and passwordToUse (which might be null)
                                                window.xianDebug('Calling XianWalletBridge.signMessage. Password provided: ' + (passwordToUse !== null));
                                                const result = JSON.parse(XianWalletBridge.signMessage(event.detail.message, passwordToUse));
                                                document.dispatchEvent(new CustomEvent('xianWalletSignMsgResponse', {
                                                    detail: result
                                                }));
                                            } catch (error) {
                                                window.xianHandleError(error, function(errorResult) {
                                                    document.dispatchEvent(new CustomEvent('xianWalletSignMsgResponse', {
                                                        detail: errorResult
                                                    }));
                                                });
                                            }
                                        });
                                        
                                        // Listen for transaction requests
                                        document.addEventListener('xianWalletSendTx', function(event) {
                                            window.xianDebug('xianWalletSendTx event received', event.detail);
                                            
                                            try {
                                                // Store transaction details for native dialog

                                                const txDetails = {
                                                    contract: event.detail.contract,
                                                    method: event.detail.method,
                                                    kwargs: JSON.stringify(event.detail.kwargs),
                                                    stampLimit: event.detail.stampLimit || 0
                                                };
                                                
                                                // Call native method to show transaction approval dialog
                                                XianWalletBridge.showTransactionApprovalDialog(
                                                    JSON.stringify(txDetails)
                                                );
                                                
                                                // The response will be sent back via a callback from native code
                                                // See the implementation of showTransactionApprovalDialog in XianWebViewBridge
                                            } catch (error) {
                                                window.xianHandleError(error, function(errorResult) {
                                                    document.dispatchEvent(new CustomEvent('xianWalletTxStatus', {
                                                        detail: errorResult
                                                    }));
                                                });
                                            }
                                        });
                                        
                                        // Create a global handler for tx status that websites can use
                                        window.addEventListener('xianWalletTxStatus', function(event) {
                                            window.xianDebug('Transaction status received', event.detail);
                                            if (!event.detail.success) {
                                                console.error('Transaction failed:', event.detail.errors);
                                            }
                                        });
                                        
                                        // Dispatch ready event to notify dapp.js that the wallet is ready
                                        setTimeout(function() {
                                            document.dispatchEvent(new CustomEvent('xianReady'));
                                            window.xianDebug('xianReady event dispatched');
                                        }, 500);
                                    })();
                                    """
                                    evaluateJavascript(jsCode, null)
                                }
                            }
                            // Set WebChromeClient to handle JS alerts, confirms, prompts
                            webChromeClient = object : WebChromeClient() {
                                override fun onJsPrompt(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    defaultValue: String?,
                                    result: JsPromptResult?
                                ): Boolean {
                                    if (result != null) {
                                        jsPromptRequest = JsPromptRequest(message ?: "", defaultValue ?: "", result)
                                        showJsPromptDialog = true
                                        return true // Indicate we're handling the prompt
                                    }
                                    return super.onJsPrompt(view, url, message, defaultValue, result)
                                }
                                // You can override onJsAlert and onJsConfirm here too if needed
                            }

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            
                            // Add JavaScript interface
                            // Pass the listener implementation to the bridge constructor
                            val bridge = XianWebViewBridge(walletManager, networkService, authListener)
                            bridge.setWebView(this) // Pasar la referencia del WebView al bridge
                            addJavascriptInterface(bridge, "XianWalletBridge")
                            
                            // Initial load
                            Log.d("WebBrowserScreen", "AndroidView.factory: Loading initial URL: $currentWebViewUrl")
                            loadUrl(currentWebViewUrl)
                        }.also {
                            webViewRef.value = it
                        }
                    },
                    update = { webView ->
                        // Check if the URL state has changed and update the WebView
                        // Only load if the current WebView URL doesn't match the state,
                        // preventing reload loops on internal navigation.
                        val currentActualUrl = webView.url
                        if (currentActualUrl != currentWebViewUrl) {
                             Log.d("WebBrowserScreen", "AndroidView.update: Loading URL: $currentWebViewUrl (current: $currentActualUrl)")
                             webView.loadUrl(currentWebViewUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } // End of if/else for Dashboard/WebView

            // --- Dialogs and Loading Indicator (Now outside the if/else) ---

            // Custom JS Prompt Dialog
            if (showJsPromptDialog && jsPromptRequest != null) {
                var promptInput by remember { mutableStateOf(jsPromptRequest!!.defaultValue) }

                AlertDialog(
                    onDismissRequest = {
                        // Handle dismiss as cancel
                        jsPromptRequest?.result?.cancel()
                        showJsPromptDialog = false
                        jsPromptRequest = null
                    },
                    title = { Text(jsPromptRequest!!.message) },
                    text = {
                        TextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            label = { Text("Value") }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                jsPromptRequest?.result?.confirm(promptInput)
                                showJsPromptDialog = false
                                jsPromptRequest = null
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                jsPromptRequest?.result?.cancel()
                                showJsPromptDialog = false
                                jsPromptRequest = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- Password Pre-authentication Dialog ---
            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = {
                        // Treat dismiss as cancellation
                        authCallbacks?.second?.invoke() // Call onFailure
                        showPasswordDialog = false
                        authCallbacks = null
                        txDetailsForAuth = null
                        authErrorMessage = null
                    },
                    title = { Text("Authentication Required") },
                    text = {
                        Column {
                            Text("Enter your wallet password to proceed with the transaction.")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { /* Handle validation/auth on button click */ })
                            )
                            authErrorMessage?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch { // Use coroutine for walletManager call
                                    val unlocked = walletManager.unlockWallet(passwordInput)
                                    if (unlocked != null) {
                                        // Success
                                        authCallbacks?.first?.invoke(txDetailsForAuth ?: "") // Call onSuccess
                                        showPasswordDialog = false
                                        authCallbacks = null
                                        txDetailsForAuth = null
                                        authErrorMessage = null
                                    } else {
                                        // Failure
                                        authErrorMessage = "Invalid password"
                                    }
                                }
                            }
                        ) { Text("Unlock") }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                // Treat dismiss as cancellation
                                authCallbacks?.second?.invoke() // Call onFailure
                                showPasswordDialog = false
                                authCallbacks = null
                                txDetailsForAuth = null
                                authErrorMessage = null
                            }
                        ) { Text("Cancel") }
                    }
                )
            }

            // Loading indicator
            // Show loading indicator when navigating to WebView but before content is shown/loaded
            if (isLoading && !showDashboard) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth() // Corrected modifier
                )
            }

        } // Closes the main Column
    } // Closes the Scaffold lambda
}