package com.example.xianwalletapp.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.xianwalletapp.MainActivity // Import MainActivity
import com.example.xianwalletapp.R
import com.example.xianwalletapp.network.XianNetworkService
import kotlinx.coroutines.*
import java.text.DecimalFormat // Import DecimalFormat
import java.util.Locale

class XianPriceWidgetProvider : AppWidgetProvider() {

    // Create a CoroutineScope for background tasks
    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each App Widget that belongs to this provider
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d("XianPriceWidget", "Updating widget ID: $appWidgetId")
        // Construct the RemoteViews object
        val views = RemoteViews(context.packageName, R.layout.xian_price_widget)

        // Set initial text while loading
        views.setTextViewText(R.id.widget_price_text, "Updating...")

        // Set up the intent that starts the MainActivity when the widget is clicked
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_price_text, pendingIntent) // Make text clickable
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent) // Make title clickable


        // Launch a coroutine to fetch the price in the background
        widgetScope.launch {
            try {
                val networkService = XianNetworkService.getInstance(context.applicationContext)
                val priceInfo = networkService.getXianPriceInfo()

                val priceText = if (priceInfo != null) {
                    val (reserve0, reserve1) = priceInfo
                    val price = if (reserve1 != 0f) reserve0 / reserve1 else 0f
                    // Format the price to 3 decimal places with $ symbol
                    val format = DecimalFormat("$#,##0.000", java.text.DecimalFormatSymbols(Locale.US))
                    format.format(price)
                } else {
                    "N/A"
                }
                Log.d("XianPriceWidget", "Fetched price for widget $appWidgetId: $priceText")
                // Update the widget view with the fetched price
                withContext(Dispatchers.Main) { // Switch to Main thread for UI updates
                    views.setTextViewText(R.id.widget_price_text, priceText)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("XianPriceWidget", "Error updating widget $appWidgetId", e)
                // Update the widget view with an error message
                withContext(Dispatchers.Main) { // Switch to Main thread for UI updates
                    views.setTextViewText(R.id.widget_price_text, "Error")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        // Instruct the widget manager to update the widget (initial update before network call finishes)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel the scope when the last widget instance is disabled
        widgetScope.cancel()
        Log.d("XianPriceWidget", "Last widget disabled, cancelling scope.")
    }
}