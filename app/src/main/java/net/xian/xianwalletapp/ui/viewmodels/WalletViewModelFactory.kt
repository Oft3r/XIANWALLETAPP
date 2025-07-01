package net.xian.xianwalletapp.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.xian.xianwalletapp.data.TransactionRepository // Import TransactionRepository
import net.xian.xianwalletapp.data.db.AppDatabase
import net.xian.xianwalletapp.network.RetrofitClient // Import RetrofitClient
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager

// Simple ViewModel Factory (replace with your DI solution if available)
class WalletViewModelFactory(
    private val context: Context,
    private val walletManager: WalletManager,
    private val networkService: XianNetworkService
    // TransactionRepository will be created within the factory
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Get DAO instances from the database
            val database = AppDatabase.getDatabase(context)
            val nftDao = database.nftCacheDao()
            val tokenCacheDao = database.tokenCacheDao()
            // Create TransactionRepository instance here
            val apiService = RetrofitClient.instance // Get XianApiService
            val transactionRepository = TransactionRepository(apiService) // Create Repository
            return WalletViewModel(context, walletManager, networkService, nftDao, tokenCacheDao, transactionRepository) as T // Pass context and repository with tokenCacheDao
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}