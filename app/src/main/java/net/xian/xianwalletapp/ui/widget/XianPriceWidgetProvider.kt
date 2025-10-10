package net.xian.xianwalletapp.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.widget.RemoteViews
import java.text.DecimalFormat // Import DecimalFormat
import java.util.Locale
import kotlinx.coroutines.*
import net.xian.xianwalletapp.MainActivity // Import MainActivity
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.network.XianNetworkService

class XianPriceWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_MANUAL_REFRESH =
                "net.xian.xianwalletapp.widget.ACTION_MANUAL_REFRESH"
        private const val EXTRA_WIDGET_ID = "net.xian.xianwalletapp.widget.EXTRA_WIDGET_ID"
    }

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
        val pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_price_text, pendingIntent) // Make text clickable
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent) // Make title clickable

        // Set up the intent for the refresh button
        val refreshIntent =
                Intent(context, XianPriceWidgetProvider::class.java).apply {
                    action = ACTION_MANUAL_REFRESH
                    putExtra(EXTRA_WIDGET_ID, appWidgetId) // Pass the specific widget ID
                }
        // Use unique request code per widget ID to ensure PendingIntents are distinct
        val refreshPendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        appWidgetId, // Use widget ID as request code
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        // Launch a coroutine to fetch the price, 24h change and sparkline in background
        widgetScope.launch {
            val networkService = XianNetworkService.getInstance(context.applicationContext)
            try {
                // Fetch all data concurrently
                val priceDeferred = async { networkService.getXianPriceInfo() }
                val pairsDeferred = async { networkService.getAllPairs() }

                val priceInfo = priceDeferred.await()
                val allPairs = pairsDeferred.await()

                // Identify XIAN/USDC pair to compute 24h change and sparkline
                val xianUsdcPair =
                        allPairs.find {
                            (it.token0 == "currency" && it.token1 == "con_usdc") ||
                                    (it.token1 == "currency" && it.token0 == "con_usdc")
                        }

                val change24hDeferred = async {
                    if (xianUsdcPair != null) {
                        // Determine denomination: we want USDC per XIAN => if token0 is currency we
                        // want token1-per-token0? Actually getPriceChange24h(token) expects
                        // 0=token0-per-token1.
                        // price current calculation uses reserves.first (USDC) / reserves.second
                        // (XIAN). If pair.token0 == currency then price = reserve1? We'll just
                        // derive denomination dynamically.
                        // We want USDC per XIAN: price = USDC/XIAN. If pair.token0 == "currency"
                        // then token1 == "con_usdc" so token1-per-token0 => token=1, else token=0.
                        val denomination = if (xianUsdcPair.token0 == "currency") 1 else 0
                        networkService.getPriceChange24h(xianUsdcPair.id, denomination)
                    } else null
                }

                val sparklineDeferred = async {
                    if (xianUsdcPair != null) {
                        try {
                            val events = networkService.getSwapEventsForPair(xianUsdcPair.id, "1D")
                            val prices = extractPricesFromEvents(events, xianUsdcPair)
                            if (prices.size >= 2) buildSparklineBitmap(prices.takeLast(100))
                            else null
                        } catch (e: Exception) {
                            Log.e("XianPriceWidget", "Error generating sparkline: ${e.message}")
                            null
                        }
                    } else null
                }

                val priceText =
                        if (priceInfo != null) {
                            val reserves = priceInfo.second
                            val price =
                                    if (reserves != null && reserves.second != 0f)
                                            reserves.first / reserves.second
                                    else 0f
                            val format =
                                    DecimalFormat(
                                            "$#,##0.000",
                                            java.text.DecimalFormatSymbols(Locale.US)
                                    )
                            format.format(price)
                        } else "N/A"

                val change24h = change24hDeferred.await()
                val sparkline = sparklineDeferred.await()

                withContext(Dispatchers.Main) {
                    views.setTextViewText(R.id.widget_price_text, priceText)

                    // 24h change formatting
                    if (change24h != null && change24h.isFinite()) {
                        val isPositive = change24h >= 0f
                        val df =
                                DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale.US))
                        val txt = (if (isPositive) "+" else "") + df.format(change24h) + "%"
                        views.setTextViewText(R.id.widget_change_text, txt)
                        val color =
                                if (isPositive) Color.parseColor("#4CAF50")
                                else Color.parseColor("#F44336")
                        views.setTextColor(R.id.widget_change_text, color)
                    } else {
                        views.setTextViewText(R.id.widget_change_text, "--")
                        views.setTextColor(R.id.widget_change_text, Color.GRAY)
                    }

                    // Sparkline
                    if (sparkline != null) {
                        views.setImageViewBitmap(R.id.widget_chart_image, sparkline)
                        views.setViewVisibility(R.id.widget_chart_image, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_chart_image, android.view.View.GONE)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("XianPriceWidget", "Error updating widget $appWidgetId", e)
                withContext(Dispatchers.Main) {
                    views.setTextViewText(R.id.widget_price_text, "Error")
                    views.setTextViewText(R.id.widget_change_text, "--")
                    views.setViewVisibility(R.id.widget_chart_image, android.view.View.GONE)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        // Instruct the widget manager to update the widget (initial update before network call
        // finishes)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel the scope when the last widget instance is disabled
        widgetScope.cancel()
        Log.d("XianPriceWidget", "Last widget disabled, cancelling scope.")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Important: call super first

        if (intent.action == ACTION_MANUAL_REFRESH) {
            val appWidgetId =
                    intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.d("XianPriceWidget", "Manual refresh requested for widget ID: $appWidgetId")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } else {
                Log.w("XianPriceWidget", "Received manual refresh intent without valid widget ID.")
                // Optionally, update all widgets if ID is missing?
                // val appWidgetManager = AppWidgetManager.getInstance(context)
                // val thisAppWidget = ComponentName(context.packageName, javaClass.name)
                // val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
                // appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id) }
            }
        }
        // Handle other standard widget intents if necessary, though super.onReceive usually covers
        // them.
    }
}

// --- Helper functions ---
private fun extractPricesFromEvents(
        events: List<net.xian.xianwalletapp.network.SwapEvent>,
        pair: net.xian.xianwalletapp.network.XianNetworkService.PairInfo
): List<Float> {
    // SwapEvent.price = token1/token0
    // Queremos USDC por XIAN (USDC/XIAN)
    // Si token0 == "currency" (XIAN) y token1 == "con_usdc" => price YA es USDC/XIAN (correcto)
    // Si token0 == "con_usdc" y token1 == "currency" => price = XIAN/USDC, necesitamos invertir
    val invert = pair.token0 == "con_usdc" && pair.token1 == "currency"
    return events
            .mapNotNull { ev ->
                val raw = ev.price.toFloat()
                val adjusted = if (invert && raw > 0f) 1f / raw else raw
                adjusted.takeIf { it.isFinite() && it > 0f }
            }
            .reversed() // poner en orden cronológico ascendente
}

private fun buildSparklineBitmap(
        prices: List<Float>,
        width: Int = 400,
        height: Int = 150,
        stroke: Float = 4f
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.parseColor("#222222")) // Dark background

    if (prices.isEmpty()) return bmp

    val padX = 50f
    val padY = 30f
    val innerW = (width - padX * 2).coerceAtLeast(1f)
    val innerH = (height - padY * 2).coerceAtLeast(1f)

    val min = prices.minOrNull() ?: 0f
    val max = prices.maxOrNull() ?: min + 1f
    val range = (max - min).takeIf { it > 0f } ?: 1f

    val lineColor =
            if (prices.last() >= prices.first()) Color.parseColor("#4CAF50")
            else Color.parseColor("#F44336")

    // Grid lines
    val gridPaint =
            Paint().apply {
                color = Color.parseColor("#444444")
                strokeWidth = 1f
            }
    for (i in 0..4) {
        val y = padY + i * innerH / 4
        canvas.drawLine(padX, y, padX + innerW, y, gridPaint)
    }

    // Line chart
    val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                strokeWidth = stroke
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

    var prevX = padX
    var prevY = padY + innerH - ((prices.first() - min) / range * innerH)
    val stepX = innerW / (prices.size - 1).coerceAtLeast(1)

    for (i in 1 until prices.size) {
        val x = padX + i * stepX
        val y = padY + innerH - ((prices[i] - min) / range * innerH)
        canvas.drawLine(prevX, prevY, x, y, strokePaint)
        prevX = x
        prevY = y
    }

    // Labels
    val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f
            }
    val df = DecimalFormat("#,##0.000")
    canvas.drawText(df.format(max), 5f, padY - 5, labelPaint)
    canvas.drawText(df.format(min), 5f, padY + innerH + 20, labelPaint)

    return bmp
}
