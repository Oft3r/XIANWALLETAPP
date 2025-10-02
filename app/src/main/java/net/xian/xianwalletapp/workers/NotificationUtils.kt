package net.xian.xianwalletapp.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import net.xian.xianwalletapp.R
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

object NotificationUtils {

    // Accent colors
    private const val COLOR_RECEIVE = 0xFF34C759.toInt() // green
    private const val COLOR_SEND = 0xFFFF3B30.toInt()    // red
    private const val COLOR_SWAP = 0xFF007AFF.toInt()    // blue
    private const val COLOR_GENERIC = 0xFF8E8E93.toInt() // neutral slate

    // Background gradients
    private const val LIGHT_START = 0xFFFFFFFF.toInt()
    private const val LIGHT_END = 0xFFF7F9FC.toInt()
    private const val DARK_START = 0xFF1E1E2E.toInt()
    private const val DARK_END = 0xFF16161D.toInt()

    enum class ActionType { RECEIVE, SEND, SWAP, GENERIC }

    /**
     * Legacy simple notification (kept for compatibility).
     */
    fun showNotificationIfPermitted(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        pendingIntent: PendingIntent? = null
    ) {
        if (!hasPostNotificationsPermission(context)) {
            Log.w("NotificationUtils", "POST_NOTIFICATIONS permission not granted. Skipping notification.")
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, channelId, "Wallet Activity", "General wallet notifications", high = false)

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
            builder.setAutoCancel(true)
        }

        manager.notify(1, builder.build())
    }

    /**
     * Redesigned wallet push notification: premium single-line card with gradient, contextual icon,
     * capitalized action verb, 4-digit precision amount with ticker, and relative timestamp.
     * Never shows transaction hashes.
     */
    fun showRedesignedTransactionNotification(
        context: Context,
        channelId: String,
        rawType: String,                 // e.g., "Sent", "Received", "Swapped", "Interacted"
        amount: String,                  // raw amount string
        symbol: String,                  // asset ticker
        otherPartyAddress: String?,      // counterparty address (shortened inside)
        timestampMillis: Long,           // epoch millis
        pendingIntent: PendingIntent? = null
    ) {
        if (!hasPostNotificationsPermission(context)) {
            Log.w("NotificationUtils", "POST_NOTIFICATIONS permission not granted. Skipping notification.")
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, channelId, "Wallet Activity", "Transaction notifications", high = true)

        // If the existing channel importance is too low for heads-up, auto-switch to a new high-importance channel id
        val finalChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(channelId)
            if (existing != null) {
                Log.d("NotificationUtils", "Channel '$channelId' importance=${existing.importance}")
            }
            if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                val altId = "${channelId}_v2"
                ensureChannel(manager, altId, "Wallet Activity", "Transaction notifications", high = true)
                Log.w("NotificationUtils", "Switching to alt channel '$altId' with IMPORTANCE_HIGH to avoid suppression")
                altId
            } else {
                channelId
            }
        } else {
            channelId
        }

        val actionType = parseActionType(rawType)
        val actionVerb = actionVerb(actionType) // Capitalized as per spec
        val formattedAmount = formatAmountWithTicker(amount, symbol, actionType)
        val timeRelative = toRelativeTime(timestampMillis)
        val isDark = isDarkMode(context)

        // Build custom RemoteViews
        val views = RemoteViews(context.packageName, R.layout.notification_component)

    // Se elimina el fondo degradado para un estilo más limpio (sin color de relleno)
    // Ya no se manipula noti_bg; el layout se simplificará removiendo ese ImageView.

        // Text content
        views.setTextViewText(R.id.tvAction, actionVerb)
        views.setTextViewText(R.id.tvAmount, buildString {
            append(formattedAmount)
            otherPartyAddress?.takeIf { it.isNotEmpty() }?.let {
                append(" • ")
                append(shortAddress(it))
            }
        })
        views.setTextViewText(R.id.tvTimestamp, timeRelative)

        // Text colors tuned per theme
        val primaryText = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val secondaryText = if (isDark) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()
        val tertiaryText = if (isDark) 0x99FFFFFF.toInt() else 0x99000000.toInt()
        views.setInt(R.id.tvAction, "setTextColor", primaryText)
        views.setInt(R.id.tvAmount, "setTextColor", secondaryText)
        views.setInt(R.id.tvTimestamp, "setTextColor", tertiaryText)

        // Right-aligned contextual icon with tint
        val (iconRes, tint) = when (actionType) {
            ActionType.RECEIVE -> R.drawable.ic_tx_receive_24 to COLOR_RECEIVE
            ActionType.SEND -> R.drawable.ic_tx_send_24 to COLOR_SEND
            ActionType.SWAP -> R.drawable.ic_tx_swap_24 to COLOR_SWAP
            ActionType.GENERIC -> R.drawable.ic_tx_generic_24 to COLOR_GENERIC
        }
        views.setImageViewResource(R.id.ivType, iconRes)
        views.setInt(R.id.ivType, "setColorFilter", tint)

        // Build notification
        val builder = NotificationCompat.Builder(context, finalChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            // Set baseline content as fallback for OEMs that require standard fields even with custom views
            .setContentTitle(actionVerb)
            .setContentText("$formattedAmount • $timeRelative")
            .setWhen(timestampMillis)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setAutoCancel(true)
            // Persist until user taps or swipes away (no auto-timeout)

        val contentIntent = pendingIntent ?: PendingIntent.getActivity(
            context,
            0,
            Intent(context, net.xian.xianwalletapp.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        builder.setContentIntent(contentIntent)

        // Post notification with unique ID (with robust fallback if custom view fails)
        try {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (t: Throwable) {
            Log.e("NotificationUtils", "Custom notification failed, falling back to basic style", t)
            val fallback = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(actionVerb)
                .setContentText("$formattedAmount • ${otherPartyAddress?.let { shortAddress(it) } ?: ""} • $timeRelative".trim().trim('•',' '))
                .setWhen(timestampMillis)
                .setShowWhen(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), fallback)
        }
    }

    // --- Helpers ---

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun ensureChannel(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String,
        high: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(id)
            if (existing == null) {
                val channel = NotificationChannel(
                    id,
                    name,
                    if (high) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    this.description = description
                    enableLights(true)
                    enableVibration(false)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            } else {
                // Importance cannot be changed after creation; warn if it's too low
                if (high && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    Log.w("NotificationUtils", "Channel '$id' importance=${existing.importance}. Notifications may be suppressed by system/user settings.")
                }
            }
        }
    }

    private fun parseActionType(raw: String): ActionType {
        val t = raw.trim().lowercase()
        return when {
            t.startsWith("recv") || t.startsWith("receive") -> ActionType.RECEIVE
            t.startsWith("sent") || t.startsWith("send") -> ActionType.SEND
            t.startsWith("swap") -> ActionType.SWAP
            else -> ActionType.GENERIC
        }
    }

    private fun actionVerb(type: ActionType): String = when (type) {
        ActionType.RECEIVE -> "Received"
        ActionType.SEND -> "Sent"
        ActionType.SWAP -> "Swapped"
        ActionType.GENERIC -> "Interacted"
    }

    private fun shortAddress(address: String): String {
        return if (address.length <= 12) address else "${address.take(6)}...${address.takeLast(4)}"
    }

    private fun formatAmountWithTicker(amountStr: String, symbol: String, type: ActionType): String {
        return try {
            val absVal = BigDecimal(amountStr.trim())
            val mc = MathContext(4, RoundingMode.HALF_UP) // 4 significant digits
            val rounded = absVal.abs().round(mc).stripTrailingZeros()
            val plain = rounded.toPlainString()
            val sign = when (type) {
                ActionType.SEND -> "-" // sending out
                ActionType.RECEIVE -> "+" // receiving in
                else -> "" // neutral for swap/generic
            }
            "$sign$plain $symbol"
        } catch (e: Exception) {
            // Fallback to raw string
            val sign = when (type) {
                ActionType.SEND -> "-"
                ActionType.RECEIVE -> "+"
                else -> ""
            }
            "$sign$amountStr $symbol"
        }
    }

    private fun toRelativeTime(timestampMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = abs(now - timestampMillis)
        val sec = diff / 1000
        return when {
            sec < 5 -> "Just now"
            sec < 60 -> "${sec}s ago"
            sec < 3600 -> "${sec / 60} min ago"
            sec < 86400 -> "${sec / 3600} hr ago"
            else -> "${sec / 86400} d ago"
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPxF(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    // Eliminada función de creación de bitmap con gradiente para simplificar el estilo.
}
