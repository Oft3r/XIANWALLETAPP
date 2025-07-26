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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.net.URL
import java.net.MalformedURLException
import android.util.Log
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.rememberAsyncImagePainter
import kotlin.math.abs
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language // Placeholder icon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.RemoveCircleOutline // Import Remove icon
import androidx.compose.material.icons.filled.Star // Import Star icon
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.MoreVert // Import More Vert icon for three dots
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight

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
import androidx.compose.ui.text.style.TextOverflow
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
import net.xian.xianwalletapp.ui.components.PasswordTextField
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import net.xian.xianwalletapp.ui.components.XianBottomNavBar // Import the shared navigation bar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModel // Import NavigationViewModel 
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModelFactory // Import NavigationViewModelFactory
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState // Import collectAsState
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
    val faviconUrl: String? = null, // Optional MANUAL favicon URL
    val localDrawableRes: Int? = null // Optional local drawable resource
)

// --- Banner Carousel Composable ---
@Composable
fun BannerCarousel(
    modifier: Modifier = Modifier,
    onBannerClick: (String) -> Unit
) {
    // List of banner drawable resources with their corresponding URLs
    val bannerData: List<Pair<Int, String>> = listOf(
        net.xian.xianwalletapp.R.drawable.banner1 to "https://xns.domains",
        net.xian.xianwalletapp.R.drawable.banner2 to "https://pixelsnek.xian.org",
        net.xian.xianwalletapp.R.drawable.banner3 to "https://dex.xian.org"
    )
    
    // Create a large number of pages for infinite scroll effect
    val infinitePageCount = bannerData.size * 1000 // Large number for pseudo-infinite scrolling
    val startPage = infinitePageCount / 2 // Start in the middle to allow scrolling in both directions
    
    // Create pager state for managing the carousel with infinite pages
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { infinitePageCount }
    )
    
    // Track user interaction state for auto-scroll
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val isPressed by pagerState.interactionSource.collectIsPressedAsState()
    val isUserInteracting = isDragged || isPressed
    
    // Auto-scroll functionality - only when user is not interacting
    // Always moves forward in the same direction (spiral effect)
    LaunchedEffect(isUserInteracting) {
        if (!isUserInteracting) {
            while (true) {
                delay(3000L) // Wait 3 seconds between auto-scrolls
                // Always increment the page (spiral effect in one direction)
                val nextPage = pagerState.currentPage + 1
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(durationMillis = 1200)
                )
            }
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        ) { page ->
            // Map the infinite page index to the actual banner data using modulo
            val actualBannerIndex = page % bannerData.size
            val (imageRes, url) = bannerData[actualBannerIndex]
            
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Promotional Banner ${actualBannerIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onBannerClick(url) },
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
            )
        }
    }
}

// --- Dashboard Composable ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardContent(
    mainApps: List<XAppInfo>,
    defiApps: List<XAppInfo>,
    collectiblesApps: List<XAppInfo>,
    favoriteApps: List<XAppInfo>,
    onShortcutClick: (String) -> Unit,
    onRemoveFavoriteClick: (XAppInfo) -> Unit, // Add callback for removing favorites
    faviconCacheManager: FaviconCacheManager, // Add cache manager parameter
    showBottomBar: Boolean,
    onShowBottomBarChange: (Boolean) -> Unit,
    lastScrollY: Int,
    onLastScrollYChange: (Int) -> Unit
) {
    // State to hold the dynamically fetched favicon URLs, keyed by the app's main URL
    val faviconUrls = remember { mutableStateMapOf<String, String?>() }
    
    // State to track which favorite item is being long-pressed (for showing delete button)
    var longPressedFavoriteUrl by remember { mutableStateOf<String?>(null) }

    // Define the gradient brush for the border using the new color palette
    val borderBrush = Brush.horizontalGradient(
        colors = listOf(XianPrimary, XianPrimaryVariant) // Teal color scheme
    )

    // Main Column to hold both sections
    val scrollState = rememberScrollState()
    
    // Detectar dirección del scroll para ocultar/mostrar la barra de navegación
    LaunchedEffect(scrollState.value) {
        val currentScrollY = scrollState.value
        val scrollDifference = currentScrollY - lastScrollY
        
        // Solo actuar si hay un cambio significativo en el scroll (más de 10px)
        if (abs(scrollDifference) > 10) {
            if (scrollDifference > 0 && currentScrollY > 50) {
                // Scrolling down - hide bottom bar (threshold reducido a 50px)
                onShowBottomBarChange(false)
            } else if (scrollDifference < 0) {
                // Scrolling up - show bottom bar
                onShowBottomBarChange(true)
            }
            onLastScrollYChange(currentScrollY)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // Use the scroll state for detection
            .padding(horizontal = 16.dp) // Move horizontal padding after scroll
    ) {
        // Add top spacer to allow scrolling above the first element
        Spacer(modifier = Modifier.height(8.dp))
        
        // --- Banner Carousel Section ---
        BannerCarousel(
            modifier = Modifier.padding(bottom = 16.dp),
            onBannerClick = onShortcutClick
        )
        
        // --- Favorites Bar Section (Small Icons) ---
        // Create default favorites if none exist
        val displayFavorites = if (favoriteApps.isNotEmpty()) {
            favoriteApps
        } else {
            // Default favorites for demonstration
            listOf(
                XAppInfo(name = "Xian.org", url = "https://xian.org", icon = Icons.Default.Language, faviconUrl = "https://xian.org/assets/img/favicon.ico"),
                XAppInfo(name = "XNS Domains", url = "https://xns.domains/", icon = Icons.Default.Language),
                XAppInfo(name = "PixelSnek", url = "https://pixelsnek.xian.org/", icon = Icons.Default.Language),
                XAppInfo(name = "SnakeXchange", url = "https://snakexchange.org/", icon = Icons.Default.Language)
            )
        }

        Text(
            text = "Favorites",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        
        // Horizontal scrolling favorites bar with small icons
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // Fixed height for favorites bar
        ) {
            items(displayFavorites) { app -> // Show all favorites with horizontal scrolling
                // Fetch favicon URL for favorites bar
                LaunchedEffect(app.url, faviconCacheManager) {
                    if (app.faviconUrl == null && !faviconUrls.containsKey(app.url)) {
                        val cachedUrl = faviconCacheManager.getFaviconUrl(app.url)
                        if (cachedUrl != null) {
                            faviconUrls[app.url] = cachedUrl
                        } else {
                            faviconUrls[app.url] = null
                            val fetchedUrl = fetchFaviconUrl(app.url)
                            if (fetchedUrl != null) {
                                faviconUrls[app.url] = fetchedUrl
                                faviconCacheManager.saveFaviconUrl(app.url, fetchedUrl)
                            }
                        }
                    } else if (app.faviconUrl != null) {
                        faviconUrls[app.url] = app.faviconUrl
                    }
                }

                // Box to overlay the remove button when long-pressed
                Box(
                    modifier = Modifier.width(60.dp) // Fixed width for consistent scrolling
                ) {
                    Column(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onShortcutClick(app.url) },
                                onLongClick = {
                                    longPressedFavoriteUrl = if (longPressedFavoriteUrl == app.url) {
                                        null // Toggle off if same item is long-pressed again
                                    } else {
                                        app.url // Set as long-pressed item
                                    }
                                }
                            )
                            .padding(4.dp), // Smaller padding
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val placeholderPainter = rememberVectorPainter(image = app.icon)
                        
                        // Prioritize local drawable, then favicon URL, then placeholder
                        val painter = if (app.localDrawableRes != null) {
                            painterResource(id = app.localDrawableRes)
                        } else {
                            val imageUrl = app.faviconUrl ?: faviconUrls[app.url]
                            rememberAsyncImagePainter(
                                model = imageUrl,
                                placeholder = placeholderPainter,
                                error = placeholderPainter
                            )
                        }

                        Image(
                            painter = painter,
                            contentDescription = app.name,
                            modifier = Modifier.size(32.dp) // Much smaller icon size
                        )
                        Spacer(modifier = Modifier.height(4.dp)) // Smaller spacer
                        Text(
                            text = app.name,
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp, // Smaller text
                            maxLines = 2, // Allow 2 lines for longer names
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp) // Small horizontal padding for text
                        )
                    }
                    
                    // Remove Button - only show when this item is long-pressed
                    if (longPressedFavoriteUrl == app.url) {
                        IconButton(
                            onClick = {
                                onRemoveFavoriteClick(app)
                                longPressedFavoriteUrl = null // Reset state after removal
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(0.dp)
                                .size(20.dp) // Smaller button for compact favorites bar
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveCircleOutline,
                                contentDescription = "Remove Favorite",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp) // Smaller icon
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp)) // Space before categories
        
        // Helper function to create category sections
        @Composable
        fun CategorySection(title: String, apps: List<XAppInfo>) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        brush = borderBrush,
                        shape = MaterialTheme.shapes.medium
                    ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    gridItems(apps) { app ->
                        // Fetch favicon URL: Check cache first, then fetch if needed
                        LaunchedEffect(app.url, faviconCacheManager) {
                            if (app.faviconUrl == null && !faviconUrls.containsKey(app.url)) {
                                val cachedUrl = faviconCacheManager.getFaviconUrl(app.url)
                                if (cachedUrl != null) {
                                    faviconUrls[app.url] = cachedUrl
                                    Log.d("FaviconCache", "Using cached favicon for ${app.url}")
                                } else {
                                    Log.d("FaviconCache", "Fetching favicon for ${app.url}")
                                    faviconUrls[app.url] = null
                                    val fetchedUrl = fetchFaviconUrl(app.url)
                                    if (fetchedUrl != null) {
                                        faviconUrls[app.url] = fetchedUrl
                                        faviconCacheManager.saveFaviconUrl(app.url, fetchedUrl)
                                        Log.d("FaviconCache", "Fetched and cached favicon for ${app.url}")
                                    } else {
                                        Log.w("FaviconCache", "Failed to fetch favicon for ${app.url}")
                                    }
                                }
                            } else if (app.faviconUrl != null) {
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
                            
                            val painter = if (app.localDrawableRes != null) {
                                painterResource(id = app.localDrawableRes)
                            } else {
                                val imageUrl = app.faviconUrl ?: faviconUrls[app.url]
                                rememberAsyncImagePainter(
                                    model = imageUrl,
                                    placeholder = placeholderPainter,
                                    error = placeholderPainter
                                )
                            }

                            Image(
                                painter = painter,
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
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // --- Essentials Section ---
        CategorySection("Essentials", mainApps)
        
        // --- DeFi Section ---
        CategorySection("DeFi", defiApps)
        
        // --- Collectibles Section ---
        CategorySection("Collectibles", collectiblesApps)

        // --- Favorite XApps Section ---
        
        // Add extra bottom spacer to allow scrolling past the last element
        Spacer(modifier = Modifier.height(100.dp)) // Increased bottom spacing for navigation bars
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
    initialUrl: String? = null, // Argument for initial URL
    navigationViewModel: NavigationViewModel = viewModel(
        factory = NavigationViewModelFactory(SavedStateHandle())
    )
) {    // Ensure navigation state is synchronized with current screen
    LaunchedEffect(Unit) {
        // Use 1 for WebBrowser screen based on bottom nav order
        navigationViewModel.syncSelectedItemWithRoute("web_browser")
    }
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
    // Collect favorites from DataStore Flow as State
    val favoriteXAppsState = walletManager.loadFavoritesFlow().collectAsState(initial = emptyList())
    val favoriteXApps = favoriteXAppsState.value // Get the actual list from the state

    // REMOVE the old mutableStateListOf and LaunchedEffect
    // val favoriteXApps = remember { mutableStateListOf<XAppInfo>() }
    // LaunchedEffect(walletManager) { ... }

    // Define the categorized XApps
    val mainApps = listOf(
        XAppInfo(name = "Xian.org", url = "https://xian.org", icon = Icons.Default.Language, faviconUrl = "https://xian.org/assets/img/favicon.ico"),
        XAppInfo(name = "Xian Block Explorer", url = "https://explorer.xian.org", icon = Icons.Default.Language, faviconUrl = "https://explorer.xian.org/img/logo.bf1eed5b.png")
    )
    
    val defiApps = listOf(
        XAppInfo(name = "XIAN DEX", url = "https://dex.xian.org", icon = Icons.Default.Language, localDrawableRes = net.xian.xianwalletapp.R.drawable.xdex),
        XAppInfo(name = "SnakeXchange", url = "https://snakexchange.org/", icon = Icons.Default.Language),
        XAppInfo(name = "OTC", url = "https://xian-otc.site/open-offers", icon = Icons.Default.Language, localDrawableRes = net.xian.xianwalletapp.R.drawable.otc)
    )
    
    val collectiblesApps = listOf(
        XAppInfo(name = "XNS Domains", url = "https://xns.domains/", icon = Icons.Default.Language),
        XAppInfo(name = "PixelSnek", url = "https://pixelsnek.xian.org/", icon = Icons.Default.Language)
    )

    // Estado para mostrar/ocultar la barra inferior según el scroll
    var showBottomBar by remember { mutableStateOf(true) }
    
    // Estado para detectar la dirección del scroll
    var lastScrollY by remember { mutableStateOf(0) }
    var isScrollingUp by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            // Barra de URL y navegación, ahora respeta el insets del sistema
            Surface(
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                // Usar el padding de statusBars para evitar que la barra quede detrás del reloj
                val insets = WindowInsets.statusBars.asPaddingValues()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(insets)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Botón de retroceso
                    IconButton(
                        onClick = {
                            val webView = webViewRef.value
                            if (!showDashboard) {
                                if (webView?.canGoBack() == true) {
                                    webView.goBack()
                                } else {
                                    showDashboard = true
                                    showBottomBar = true // Ensure bottom bar is visible when returning to dashboard
                                    isLoading = false
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    // Campo de URL
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Enter URL...", fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val formattedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                                    "https://$urlInput"
                                } else {
                                    urlInput
                                }
                                urlInput = formattedUrl
                                currentWebViewUrl = formattedUrl
                                showDashboard = false
                                showBottomBar = true // Reset bottom bar state when navigating to web
                                isLoading = true
                                focusManager.clearFocus()
                            }
                        ),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .height(48.dp)
                            .weight(1f, fill = true),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    )
                    
                    // Botón Go compacto - solo mostrar en dashboard
                    if (showDashboard) {
                        IconButton(
                            onClick = {
                                val formattedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                                    "https://$urlInput"
                                } else {
                                    urlInput
                                }
                                urlInput = formattedUrl
                                currentWebViewUrl = formattedUrl
                                showDashboard = false
                                showBottomBar = true // Reset bottom bar state when navigating to web
                                isLoading = true
                                focusManager.clearFocus()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Go",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Solo mostrar menú de opciones cuando no está en dashboard (cuando hay un sitio web cargado)
                    if (!showDashboard) {
                        // Estado para controlar si el menú está abierto
                        var showOptionsMenu by remember { mutableStateOf(false) }
                        
                        // Calcular si está en favoritos
                        val isFavorited = remember(urlInput, favoriteXApps) {
                            val normalizedInput = normalizeUrlForComparison(urlInput)
                            val result = normalizedInput != null && favoriteXApps.any { normalizeUrlForComparison(it.url) == normalizedInput }
                            Log.d("WebBrowserScreen", "Recalculating isFavorited: urlInput='$urlInput', normalizedInput='$normalizedInput', result=$result")
                            result
                        }
                        
                        // Botón de tres puntos para mostrar menú
                        Box {
                            IconButton(
                                onClick = { showOptionsMenu = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Menú desplegable
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                // Opción de recargar
                                DropdownMenuItem(
                                    text = { Text("Reload Page") },
                                    onClick = {
                                        webViewRef.value?.reload()
                                        showOptionsMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Reload",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                                
                                // Opción de añadir/quitar favoritos (siempre mostrar)
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isFavorited) "Remove from Favorites" else "Add to Favorites")
                                    },
                                    onClick = {
                                        val originalUrl = urlInput
                                        val normalizedUrl = normalizeUrlForComparison(originalUrl)
                                        if (normalizedUrl != null) {
                                            if (isFavorited) {
                                                // Remover de favoritos
                                                val updatedList = favoriteXApps.filter {
                                                    normalizeUrlForComparison(it.url) != normalizedUrl
                                                }
                                                coroutineScope.launch {
                                                    walletManager.saveFavorites(updatedList)
                                                }
                                                Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                                            } else {
                                                // Añadir a favoritos
                                                try {
                                                    val urlObject = URL(originalUrl)
                                                    val host = urlObject.host ?: originalUrl
                                                    val name = host.uppercase()
                                                    val newFavorite = XAppInfo(
                                                        name = name,
                                                        url = originalUrl,
                                                        icon = Icons.Default.Language
                                                    )
                                                    val updatedList = favoriteXApps + newFavorite
                                                    Log.d("WebBrowserScreen", "Adding favorite: $originalUrl (Normalized: $normalizedUrl)")
                                                    coroutineScope.launch {
                                                        walletManager.saveFavorites(updatedList)
                                                    }
                                                    Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                                                } catch (e: MalformedURLException) {
                                                    Log.e("WebBrowserScreen", "Invalid URL for favorite add: $originalUrl", e)
                                                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Log.e("WebBrowserScreen", "Error adding favorite: $originalUrl", e)
                                                    Toast.makeText(context, "Error Adding Favorite", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            Log.w("WebBrowserScreen", "Attempted to add favorite with blank or invalid URL: $originalUrl")
                                            Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                        }
                                        showOptionsMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isFavorited) Icons.Filled.Star else Icons.Outlined.Star,
                                            contentDescription = if (isFavorited) "Remove from Favorites" else "Add to Favorites",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar && showDashboard, // Solo mostrar en dashboard, no cuando hay web cargada
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                XianBottomNavBar(
                    navController = navController,
                    navigationViewModel = viewModel(
                        factory = NavigationViewModelFactory(SavedStateHandle())
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .navigationBarsPadding()
        ) {

            // --- Conditional Content (Dashboard or WebView) ---
            if (showDashboard) {
                DashboardContent(
                    mainApps = mainApps,
                    defiApps = defiApps,
                    collectiblesApps = collectiblesApps,
                    favoriteApps = favoriteXApps, // Pass the collected list
                    onShortcutClick = { targetUrl ->
                        currentWebViewUrl = targetUrl
                        isLoading = true
                        showDashboard = false
                        showBottomBar = true // Reset bottom bar state when navigating to web
                    },
                    faviconCacheManager = faviconCacheManager, // Pass the cache manager here
                    onRemoveFavoriteClick = { appToRemove ->
                        // Remove from the list and save
                        val updatedList = favoriteXApps.filter { it.url != appToRemove.url }
                        coroutineScope.launch {
                            walletManager.saveFavorites(updatedList)
                        }
                        Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show() // Add feedback
                    },
                    showBottomBar = showBottomBar,
                    onShowBottomBarChange = { showBottomBar = it },
                    lastScrollY = lastScrollY,
                    onLastScrollYChange = { lastScrollY = it }
                )
            } else {
               // WebView                // Apply a clipped container for the WebView with rounded corners
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)) // Add rounded corners to the WebView container
                ) {
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

                            // --- Scroll listener para mostrar/ocultar la barra inferior ---
                            var lastScrollY = 0
                            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                if (scrollY > oldScrollY + 10) {
                                    // Scroll hacia arriba (usuario baja)
                                    if (showBottomBar) showBottomBar = false
                                } else if (scrollY < oldScrollY - 10) {
                                    // Scroll hacia abajo (usuario sube)
                                    if (!showBottomBar) showBottomBar = true
                                }
                                lastScrollY = scrollY
                            }

                            // Remove WebView border
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                        }                    },
                    modifier = Modifier.fillMaxSize()
                )
                } // Close the Box with the clip modifier
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
                            Text("OK", color = Color.Black)
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
                            Text("Cancel", color = Color.Black)
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
                            PasswordTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(),
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
                        ) { Text("Unlock", color = Color.Black) }
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
                        ) { Text("Cancel", color = Color.Black) }
                    }
                )
            }            // Loading indicator mejorado con mensaje de estado
            // Show loading indicator when navigating to WebView but before content is shown/loaded
            if (!showDashboard) { // Siempre mostrar algún indicador de estado cuando se muestra el WebView
                if (isLoading) {
                    // Mostrar barra de progreso mientras carga
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp)), // Bordes redondeados para el indicador
                        color = MaterialTheme.colorScheme.primary, // Color principal
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) // Color de fondo más sutil
                    )
                } else {
                    // Mostrar indicador de página cargada
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle, 
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Página cargada", 
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

        } // Closes the main Column
    } // Closes the Scaffold lambda
}