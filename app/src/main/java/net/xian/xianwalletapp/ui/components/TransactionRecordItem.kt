package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xian.xianwalletapp.data.LocalTransactionRecord
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.util.Log
import androidx.navigation.NavController
import net.xian.xianwalletapp.navigation.XianDestinations

/**
 * Composable function to display a single local transaction record.
 */
@Composable
fun TransactionRecordItem(
    record: LocalTransactionRecord,
    navController: NavController,
    dense: Boolean = false,
    topRounded: Boolean = true,
    bottomRounded: Boolean = true
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
    val formattedTimestamp = remember(record.timestamp) { formatter.format(Instant.ofEpochMilli(record.timestamp)) }

    val outerPadding = if (dense) PaddingValues(vertical = 2.dp) else PaddingValues(vertical = 6.dp)
    val shape = RoundedCornerShape(
        topStart = if (topRounded) 8.dp else 2.dp,
        topEnd = if (topRounded) 8.dp else 2.dp,
        bottomStart = if (bottomRounded) 8.dp else 2.dp,
        bottomEnd = if (bottomRounded) 8.dp else 2.dp
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(outerPadding),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedTimestamp,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = record.type,
                    fontSize = 12.sp,
                    color = if (record.type == "Sent") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(if (dense) 2.dp else 4.dp))
            Text(
                text = "${if (record.type == "Sent") "-" else "+"}${
                    try {
                        String.format("%.2f", record.amount.toDouble())
                    } catch (e: NumberFormatException) {
                        record.amount // Fallback to original amount if not a valid double
                    }
                } ${record.symbol}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(if (dense) 2.dp else 4.dp))

            val otherPartyAddress = if (record.type == "Sent") record.recipient else record.sender
            val addressLabel = if (record.type == "Sent") "To: " else "From: "

            if (otherPartyAddress != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$addressLabel${otherPartyAddress.take(8)}...${otherPartyAddress.takeLast(6)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val url = "https://explorer.xian.org/addresses/$otherPartyAddress"
                                try {
                                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                    navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                                } catch (e: Exception) {
                                    Log.e("TransactionRecordItem", "Error encoding address URL: $url", e)
                                }
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(if (dense) 1.dp else 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TX: ${record.txHash.take(8)}...${record.txHash.takeLast(6)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val url = "https://explorer.xian.org/tx/${record.txHash}"
                            try {
                                val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                            } catch (e: Exception) {
                                Log.e("TransactionRecordItem", "Error encoding tx URL: $url", e)
                            }
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
