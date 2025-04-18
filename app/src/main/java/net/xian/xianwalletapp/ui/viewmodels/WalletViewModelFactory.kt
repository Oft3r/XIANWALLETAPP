package net.xian.xianwalletapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager

// Simple ViewModel Factory (replace with your DI solution if available)
class WalletViewModelFactory(
    private val walletManager: WalletManager,
    private val networkService: XianNetworkService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalletViewModel(walletManager, networkService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}