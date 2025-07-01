package net.xian.xianwalletapp.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun scheduleTransactionMonitor(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<TransactionMonitorWorker>(15, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "TransactionMonitor",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

fun restartTransactionMonitor(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<TransactionMonitorWorker>(15, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "TransactionMonitor",
        ExistingPeriodicWorkPolicy.REPLACE, // Replace existing work to restart
        workRequest
    )
}
