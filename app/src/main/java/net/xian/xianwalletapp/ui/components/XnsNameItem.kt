package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Visibility // Import View icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import net.xian.xianwalletapp.navigation.XianDestinations
import android.util.Log

/**
 * Composable for displaying an XNS name item
 */
@Composable
fun XnsNameItem(
    username: String,
    remainingDays: Long?,
    navController: NavController
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp), // Keep height consistent
            // REMOVE .clickable modifier from here
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "XNS Domain",
                modifier = Modifier
                    .size(48.dp) // Reducido de 64dp a 48dp para mejor ajuste
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(10.dp), // Reducido de 12dp a 10dp
                tint = MaterialTheme.colorScheme.primary
            )            
            Spacer(modifier = Modifier.height(12.dp)) // Reducido de 16dp a 12dp
            
            Text(
                text = username,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp, // Reducido de 20sp a 18sp para mejor ajuste
                textAlign = TextAlign.Center
            )
            
            // Optional: Add a small label            
            Text(
                text = "XNS Domain",
                style = MaterialTheme.typography.bodySmall, // Cambiado de bodyMedium a bodySmall
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 12.sp // Tamaño de fuente explícito
            )
            
            // Display Remaining Days
            if (remainingDays != null) {
                Spacer(modifier = Modifier.height(6.dp)) // Reducido de 8dp a 6dp
                val textColor = when {
                    remainingDays < 30 -> Color.Red
                    remainingDays < 90 -> Color(0xFFFF9800) // Orange
                    else -> Color.White // Was green, now white
                }
                Text(
                    text = when {
                        remainingDays == 0L -> "Expired"
                        remainingDays == 1L -> "Expires tomorrow"
                        else -> "Expires in $remainingDays days"
                    },
                    style = MaterialTheme.typography.bodySmall, // Cambiado de bodyMedium a bodySmall
                    color = textColor,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp // Tamaño de fuente explícito para mejor ajuste
                )            } else {
                Spacer(modifier = Modifier.height(6.dp)) // Reducido de 8dp a 6dp                
                Text(
                    text = "Expiration unknown",
                    style = MaterialTheme.typography.bodySmall, // Cambiado de bodyMedium a bodySmall
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp // Tamaño de fuente explícito para mantener coherencia
                )
            }
            
            Spacer(modifier = Modifier.weight(1f)) // Pushes the button to the bottom

            // Re-add the Button
            Button(
                onClick = { 
                    val urlToLoad = "https://xns.domains/?name=$username"
                    try {
                        val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                        navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                    } catch (e: Exception) {
                        Log.e("XnsNameItem", "Error encoding or navigating to URL: $urlToLoad", e)
                        // Optionally, show a snackbar if snackbarHostState is available
                    }
                },
                modifier = Modifier.align(Alignment.End), // Align button to the end like in NftItem
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp) // Compact button
            ) {
                // Use a more descriptive icon or text if desired
                // Icon(Icons.Default.Language, contentDescription = "View", modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(
                    text = "View", 
                    fontSize = 12.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                ) // Texto negro y en negrita
            }
        }
    }
}
