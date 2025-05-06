package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.xian.xianwalletapp.data.LocalTransactionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Composable function to display a single local transaction record.
 */
@Composable
fun TransactionRecordItem(record: LocalTransactionRecord) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
    val formattedTimestamp = remember(record.timestamp) { formatter.format(Instant.ofEpochMilli(record.timestamp)) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${if (record.type == "Sent") "-" else "+"}${record.amount} ${record.symbol}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (record.type == "Sent" && record.recipient != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "To: ${record.recipient.take(8)}...${record.recipient.takeLast(6)}", 
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(record.recipient))
                            coroutineScope.launch {
                                // Optionally show a snackbar
                                // snackbarHostState.showSnackbar("Address copied to clipboard")
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy, 
                            contentDescription = "Copy Address",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // TODO: Add 'From' if needed for received transactions (requires storing sender)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TX: ${record.txHash.take(8)}...${record.txHash.takeLast(6)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(record.txHash))
                            coroutineScope.launch {
                                // Optionally show a snackbar
                                // snackbarHostState.showSnackbar("Transaction hash copied")
                            }
                        }
                )
                // IconButton removed to match the current design
            }
        }
    }
}
