package net.xian.xianwalletapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager

class StakingViewModelFactory(
    private val networkService: XianNetworkService,
    private val walletManager: WalletManager
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StakingViewModel::class.java)) {
            return StakingViewModel(networkService, walletManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}