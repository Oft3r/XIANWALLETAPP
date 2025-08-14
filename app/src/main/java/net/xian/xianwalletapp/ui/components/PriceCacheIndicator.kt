package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay

@Composable
fun PriceCacheIndicator(
    price: Float?,
    isFromCache: Boolean = true,
    isUpdating: Boolean = false,
    lastUpdated: Long? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Price display
        if (price != null) {
            Text(
                text = "$${String.format("%.6f", price)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Loading...",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Cache status indicator
        when {
            isUpdating -> {
                // Rotating refresh icon when updating
                var rotation by remember { mutableStateOf(0f) }
                
                LaunchedEffect(isUpdating) {
                    while (isUpdating) {
                        rotation += 360f
                        delay(1000)
                    }
                }
                
                val animatedRotation by animateFloatAsState(
                    targetValue = rotation,
                    animationSpec = tween(1000, easing = LinearEasing),
                    label = "RefreshRotation"
                )
                
                Icon(
                    imageVector = Icons.Default.Cached,
                    contentDescription = "Updating price",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(animatedRotation),
                    tint = Color.Blue
                )
            }
            isFromCache -> {
                // Clock icon for cached data
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Cached price",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Green
                )
            }
            else -> {
                // Cloud off icon for no cache
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "No cached price",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
            }
        }
        
        // Age indicator
        lastUpdated?.let { timestamp ->
            val ageMinutes = (System.currentTimeMillis() - timestamp) / (1000 * 60)
            if (ageMinutes > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${ageMinutes}m",
                    fontSize = 12.sp,
                    color = when {
                        ageMinutes < 5 -> Color.Green
                        ageMinutes < 15 -> Color(0xFFFFA500)
                        else -> Color.Red
                    }
                )
            }
        }
    }
}

@Composable
fun CacheStatusDot(
    isFromCache: Boolean,
    isStale: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(
                color = when {
                    !isFromCache -> Color.Gray
                    isStale -> Color(0xFFFFA500)
                    else -> Color.Green
                },
                shape = CircleShape
            )
    )
}