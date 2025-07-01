
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
) : CoroutineWorker(context, workerParams) {    override suspend fun doWork(): Result {
        android.util.Log.d("TransactionMonitorWorker", "Starting transaction check")
        try {
            val hasNewTransaction = checkForNewTransaction()
            if (hasNewTransaction) {
                android.util.Log.i("TransactionMonitorWorker", "New transaction detected! Showing notification")
                showNotification("New transaction detected!", "Check your wallet for details.")
            } else {
                android.util.Log.d("TransactionMonitorWorker", "No new transactions found")
            }
            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TransactionMonitorWorker", "Error in worker execution", e)
            return Result.failure()
        }
    }    /**
     * Verifica si hay una nueva transacción en la blockchain usando el mismo método que Activity.
     * Compara el hash de la transacción más reciente con el último guardado localmente.
     */
    private suspend fun checkForNewTransaction(): Boolean {
        val prefs = applicationContext.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)
        val lastTxId = prefs.getString("last_tx_id", null)
        android.util.Log.d("TransactionMonitorWorker", "Last saved transaction ID: $lastTxId")
        
        val latestTxId = getLatestNetworkTransactionHash()
        android.util.Log.d("TransactionMonitorWorker", "Latest transaction ID: $latestTxId")

        return if (latestTxId != null && latestTxId != lastTxId) {
            android.util.Log.d("TransactionMonitorWorker", "New transaction detected! Saving new ID")
            prefs.edit().putString("last_tx_id", latestTxId).apply()
            true
        } else {
            android.util.Log.d("TransactionMonitorWorker", "No changes or no transactions found")
            false
        }
    }

    /**
     * Obtiene el hash de la transacción más reciente usando TransactionRepository y XianNetworkService,
     * igual que la sección Activity de la wallet.
     */    private suspend fun getLatestNetworkTransactionHash(): String? = withContext(Dispatchers.IO) {
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
            val firstTx = txList.firstOrNull()
            if (firstTx == null) return@withContext null

            // Obtener el hash de la transacción (campo txHash)
            return@withContext firstTx.txHash
        } catch (e: Exception) {
            android.util.Log.e("TransactionMonitorWorker", "Error checking for transactions", e)
        }
        return@withContext null
    }

    private fun showNotification(title: String, message: String) {
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
        NotificationUtils.showNotificationIfPermitted(applicationContext, channelId, title, message, pendingIntent)
    }
}
