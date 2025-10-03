package net.xian.xianwalletapp.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavController
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.ui.viewmodels.BrowserOverlayViewModel
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.compose.AsyncImagePainter
import net.xian.xianwalletapp.data.FaviconCacheManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.jsoup.Jsoup
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

// Lightweight helpers (mirroring WebBrowserScreen) to locate a good favicon URL
private fun looksLikeImage(contentType: String?): Boolean {
    val ct = contentType?.lowercase() ?: return false
    return ct.startsWith("image/") || ct.contains("svg") || ct.contains("ico")
}

private suspend fun isImageUrlReachable(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "HEAD"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            connectTimeout = 5000
            readTimeout = 5000
        }
        val code = conn.responseCode
        if (code in 200..399 && looksLikeImage(conn.contentType)) return@withContext true

        // Some servers don't support HEAD properly; try tiny GET
        val getConn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            setRequestProperty("Range", "bytes=0-0")
            connectTimeout = 5000
            readTimeout = 5000
        }
        val getCode = getConn.responseCode
        getConn.inputStream?.close()
        (getCode in 200..399 && looksLikeImage(getConn.contentType))
    } catch (_: Exception) {
        false
    }
}

private suspend fun fetchFaviconUrl(baseUrl: String): String? = withContext(Dispatchers.IO) {
    try {
        // 1) Try to parse HTML for link rel icons
        val doc = Jsoup.connect(baseUrl).userAgent("Mozilla/5.0 (Android)").timeout(5000).get()
        // Be permissive: accept any rel that contains 'icon' in any form
        val links = doc.select("link[rel~=(?i)icon]")
        val candidates = links.mapNotNull { el ->
            val href = el.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            try { URL(URL(baseUrl), href).toString() } catch (_: Exception) { null }
        }
        for (c in candidates) {
            if (isImageUrlReachable(c)) return@withContext c
        }

        // 1b) Try common logo images in DOM when no rel=icon is present
        // Heuristic: any <img> whose src contains "logo"
        run {
            val logoImgs = doc.select("img[src*=_logo i], img[src*=logo i], img.logo, img[class*=logo i]")
            for (img in logoImgs) {
                val href = img.attr("src").takeIf { it.isNotBlank() } ?: continue
                val u = try { URL(URL(baseUrl), href).toString() } catch (_: Exception) { null }
                if (u != null && isImageUrlReachable(u)) return@withContext u
            }
        }

        // 2) Common paths
        val common = listOf("/favicon.png", "/favicon.svg", "/apple-touch-icon.png", "/favicon.ico")
        for (p in common) {
            val u = try { URL(URL(baseUrl), p).toString() } catch (_: Exception) { null }
            if (u != null && isImageUrlReachable(u)) return@withContext u
        }
        // 3) Fallback to Google s2 API (PNG) for domains without declared icons
        val host = try { URL(baseUrl).host } catch (_: Exception) { null }
        if (!host.isNullOrBlank()) {
            val s2 = "https://www.google.com/s2/favicons?domain=$host&sz=64"
            if (isImageUrlReachable(s2)) return@withContext s2
        }
        null
    } catch (e: Exception) {
        Log.w("GlobalBubbles", "Favicon fetch failed for $baseUrl: ${e.message}")
        null
    }
}

@Composable
fun GlobalBrowserBubblesOverlay(
    navController: NavController,
    viewModel: BrowserOverlayViewModel,
    faviconCacheManager: FaviconCacheManager
) {
    val pages = viewModel.minimizedPages
    if (pages.isEmpty()) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx().roundToInt() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().roundToInt() }
    val bubbleSize = 56.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx().roundToInt() }

    pages.forEachIndexed { index, page ->
        val key = page.url
        var currentOffset by remember(key) {
            mutableStateOf(
                viewModel.bubblePositions[key] ?: IntOffset(
                    (screenWidthPx - bubbleSizePx) - (16 * (index + 1)),
                    200 + (index * (bubbleSizePx + 16))
                )
            )
        }

        LaunchedEffect(key1 = key) {
            if (!viewModel.bubblePositions.containsKey(key)) {
                viewModel.bubblePositions[key] = currentOffset
            }
            // Seed favicon URL for this bubble: cache -> fetch
            if (!viewModel.bubbleFaviconUrls.containsKey(key)) {
                val cached = faviconCacheManager.getFaviconUrl(key)
                if (cached != null) {
                    viewModel.bubbleFaviconUrls[key] = cached
                } else {
                    val fetched = fetchFaviconUrl(key)
                    if (!fetched.isNullOrBlank()) {
                        viewModel.bubbleFaviconUrls[key] = fetched
                        faviconCacheManager.saveFaviconUrl(key, fetched)
                    } else {
                        viewModel.bubbleFaviconUrls[key] = null
                    }
                }
            }
        }

        Popup(
            alignment = Alignment.TopStart,
            offset = currentOffset,
            properties = PopupProperties(focusable = false)
        ) {
            Box(
                modifier = Modifier
                    .size(bubbleSize)
                    .pointerInput(key, screenWidthPx, screenHeightPx, bubbleSizePx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            var newX = currentOffset.x + dragAmount.x.toInt()
                            var newY = currentOffset.y + dragAmount.y.toInt()
                            newX = newX.coerceIn(0, screenWidthPx - bubbleSizePx)
                            newY = newY.coerceIn(0, screenHeightPx - bubbleSizePx)
                            currentOffset = IntOffset(newX, newY)
                            viewModel.bubblePositions[key] = currentOffset
                        }
                    }
                    .then(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .clickable {
                                viewModel.setPendingRestore(page)
                                val encoded = Uri.encode(page.url)
                                navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encoded")
                                viewModel.removePage(page)
                            }
                    )
            ) {
                // Prefer bitmap captured from WebView if available
                val bitmap = viewModel.bubbleFaviconBitmaps[page.url]
                // Otherwise resolve favicon URL from VM or persistent cache
                val cachedUrlFromVm = viewModel.bubbleFaviconUrls[page.url]
                val effectiveUrl = remember(cachedUrlFromVm) {
                    cachedUrlFromVm ?: faviconCacheManager.getFaviconUrl(page.url)
                }

                // Build an ImageLoader that supports SVGs
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val loader = remember {
                    ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build()
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = page.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else if (!effectiveUrl.isNullOrBlank()) {
                    val painter = rememberAsyncImagePainter(model = effectiveUrl, imageLoader = loader)
                    when (painter.state) {
                        is AsyncImagePainter.State.Success -> {
                            Image(
                                painter = painter,
                                contentDescription = page.title,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        }
                        is AsyncImagePainter.State.Loading -> {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = page.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }
                        is AsyncImagePainter.State.Error -> {
                            // If cached URL is not usable (often .ico), try to refetch a better one once
                            val refetchKey = "refetch-" + key
                            LaunchedEffect(refetchKey) {
                                val newUrl = fetchFaviconUrl(key)
                                if (!newUrl.isNullOrBlank() && newUrl != effectiveUrl) {
                                    viewModel.bubbleFaviconUrls[key] = newUrl
                                    faviconCacheManager.saveFaviconUrl(key, newUrl)
                                    Log.d("GlobalBubbles", "Refetched favicon for $key -> $newUrl")
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = page.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = page.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = page.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
            }
        }
    }
}
