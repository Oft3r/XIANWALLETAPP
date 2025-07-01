package net.xian.xianwalletapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle

/**
 * Factory for creating NavigationViewModel with SavedStateHandle
 */
class NavigationViewModelFactory(
    private val savedStateHandle: SavedStateHandle
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NavigationViewModel(savedStateHandle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
