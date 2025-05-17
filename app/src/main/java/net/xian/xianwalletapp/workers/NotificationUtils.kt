package net.xian.xianwalletapp.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest

object NotificationUtils {
    fun showNotificationIfPermitted(context: Context, channelId: String, title: String, message: String, pendingIntent: android.app.PendingIntent? = null) {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                Log.w("NotificationUtils", "POST_NOTIFICATIONS permission not granted. Skipping notification.")
                return
            }
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Wallet Activity", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
            builder.setAutoCancel(true)
        }
        val notification = builder.build()
        manager.notify(1, notification)
    }
}
