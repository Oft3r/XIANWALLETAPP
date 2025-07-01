package net.xian.xianwalletapp.ui.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel that handles shared navigation state across screens
 */
class NavigationViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Selected bottom navigation item
    private val _selectedNavItem = MutableStateFlow(
        savedStateHandle.get<Int>(SELECTED_NAV_ITEM) ?: 0
    )
    val selectedNavItem: StateFlow<Int> = _selectedNavItem.asStateFlow()

    /**
     * Set selected navigation item
     */
    fun setSelectedNavItem(index: Int) {
        _selectedNavItem.value = index
        savedStateHandle[SELECTED_NAV_ITEM] = index
    }    /**
     * Sync selected item based on current route
     * This helps maintain consistency when navigating directly to a screen
     */
    fun syncSelectedItemWithRoute(route: String) {
        val index = when (route) {
            "wallet" -> 0
            "web_browser" -> 1
            "advanced" -> 2
            "news" -> 3
            else -> return // Don't change for other routes
        }
        setSelectedNavItem(index)
    }

    companion object {
        private const val SELECTED_NAV_ITEM = "selected_nav_item"
    }
}
