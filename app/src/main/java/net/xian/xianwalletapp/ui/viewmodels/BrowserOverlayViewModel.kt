package net.xian.xianwalletapp.ui.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import net.xian.xianwalletapp.ui.models.MinimizedPage

class BrowserOverlayViewModel : ViewModel() {
    // Shared state for minimized pages and bubble UI
    val minimizedPages = mutableStateListOf<MinimizedPage>()
    val bubbleFaviconUrls = mutableStateMapOf<String, String?>()
    val bubbleFaviconBitmaps = mutableStateMapOf<String, ImageBitmap?>()
    val bubblePositions = mutableStateMapOf<String, IntOffset>()

    // Pending restore request that WebBrowserScreen can consume
    var pendingRestore = mutableStateOf<MinimizedPage?>(null)

    fun minimizePage(title: String, url: String, state: android.os.Bundle) {
        minimizedPages.add(MinimizedPage(title = title, url = url, state = state))
    }

    fun removePage(page: MinimizedPage) {
        minimizedPages.remove(page)
        bubblePositions.remove(page.url)
        bubbleFaviconUrls.remove(page.url)
        bubbleFaviconBitmaps.remove(page.url)
    }

    fun setPendingRestore(page: MinimizedPage?) {
        pendingRestore.value = page
    }
}

