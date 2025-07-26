package net.xian.xianwalletapp.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import net.xian.xianwalletapp.workers.NotificationUtils

class TransactionMonitorWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        android.util.Log.d("TransactionMonitorWorker", "Starting transaction check")
        try {
            val latestTransaction = checkForNewTransaction()
            if (latestTransaction != null) {
                android.util.Log.i("TransactionMonitorWorker", "New transaction detected! Showing notification")
                showTransactionNotification(latestTransaction)
            } else {
                android.util.Log.d("TransactionMonitorWorker", "No new transactions found")
            }
            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TransactionMonitorWorker", "Error in worker execution", e)
            return Result.failure()
        }
    }
    
    /**
     * Verifica si hay una nueva transacción en la blockchain usando el mismo método que Activity.
     * Compara el hash de la transacción más reciente con el último guardado localmente.
     * Retorna los detalles de la transacción si es nueva, null si no hay cambios.
     */
    private suspend fun checkForNewTransaction(): net.xian.xianwalletapp.data.LocalTransactionRecord? {
        val prefs = applicationContext.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)
        val lastTxId = prefs.getString("last_tx_id", null)
        android.util.Log.d("TransactionMonitorWorker", "Last saved transaction ID: $lastTxId")
        
        val latestTransaction = getLatestNetworkTransaction()
        android.util.Log.d("TransactionMonitorWorker", "Latest transaction ID: ${latestTransaction?.txHash}")

        return if (latestTransaction != null && latestTransaction.txHash != lastTxId) {
            android.util.Log.d("TransactionMonitorWorker", "New transaction detected! Saving new ID")
            prefs.edit().putString("last_tx_id", latestTransaction.txHash).apply()
            latestTransaction
        } else {
            android.util.Log.d("TransactionMonitorWorker", "No changes or no transactions found")
            null
        }
    }

    /**
     * Obtiene la transacción más reciente usando TransactionRepository y XianNetworkService,
     * igual que la sección Activity de la wallet.
     */
    private suspend fun getLatestNetworkTransaction(): net.xian.xianwalletapp.data.LocalTransactionRecord? = withContext(Dispatchers.IO) {
        try {
            // Obtener la public key activa desde WalletManager (sin reflexión)
            val walletManager = net.xian.xianwalletapp.wallet.WalletManager.getInstance(applicationContext)
            val publicKey = walletManager.getActiveWalletPublicKey() as? String
            if (publicKey.isNullOrEmpty()) {
                android.util.Log.e("TransactionMonitorWorker", "No active wallet public key found")
                return@withContext null
            }

            // Obtener instancia de XianNetworkService (sin reflexión)
            val networkService = net.xian.xianwalletapp.network.XianNetworkService.getInstance(applicationContext)
            // Configurar URLs del nodo y explorer (igual que en MainActivity)
            val rpcUrl = walletManager.getRpcUrl()
            val explorerUrl = walletManager.getExplorerUrl()
            networkService.setRpcUrl(rpcUrl)
            networkService.setExplorerUrl(explorerUrl)
            android.util.Log.d("TransactionMonitorWorker", "Configured node with RPC URL: $rpcUrl")

            // Obtener instancia de TransactionRepository usando el apiService público
            val apiService = networkService.apiService
            android.util.Log.d("TransactionMonitorWorker", "apiService instance from XianNetworkService: ${apiService?.javaClass?.name}")

            // Usar el constructor de TransactionRepository directamente
            val transactionRepository = net.xian.xianwalletapp.data.TransactionRepository(apiService)
            android.util.Log.d("TransactionMonitorWorker", "TransactionRepository instantiated successfully.")

            // Llamar a getNetworkTransactions(publicKey)
            val txList = transactionRepository.getNetworkTransactions(publicKey)
            if (txList.isNullOrEmpty()) return@withContext null

            // El primer elemento es el más reciente (ordenado DESC)
            return@withContext txList.firstOrNull()
        } catch (e: Exception) {
            android.util.Log.e("TransactionMonitorWorker", "Error checking for transactions", e)
        }
        return@withContext null
    }

    private fun showTransactionNotification(transaction: net.xian.xianwalletapp.data.LocalTransactionRecord) {
        val channelId = "wallet_activity"
        
        // Create an intent to open MainActivity when the notification is tapped
        val intent = android.content.Intent(applicationContext, net.xian.xianwalletapp.MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
        
        // Format the transaction details
        val title = if (transaction.type == "Sent") "Transaction Sent" else "Transaction Received"
        val amountFormatted = try {
            String.format("%.2f", transaction.amount.toDouble())
        } catch (e: NumberFormatException) {
            transaction.amount
        }
        val sign = if (transaction.type == "Sent") "-" else "+"
        val amount = "$sign$amountFormatted ${transaction.symbol}"
        
        val otherPartyAddress = if (transaction.type == "Sent") transaction.recipient else transaction.sender
        val addressLabel = if (transaction.type == "Sent") "To" else "From"
        val addressDisplay = otherPartyAddress?.let { address ->
            "$addressLabel: ${address.take(8)}...${address.takeLast(6)}"
        } ?: ""
        
        val txDisplay = "TX: ${transaction.txHash.take(8)}...${transaction.txHash.takeLast(6)}"
        
        // Format timestamp
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
        val timeDisplay = formatter.format(java.time.Instant.ofEpochMilli(transaction.timestamp))
        
        NotificationUtils.showTransactionNotification(
            applicationContext,
            channelId,
            title,
            amount,
            addressDisplay,
            txDisplay,
            timeDisplay,
            pendingIntent
        )
    }
}
