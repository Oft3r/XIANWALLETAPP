package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.data.db.NftCacheEntity
import coil.compose.AsyncImage

/**
 * Composable for displaying an NFT item card
 */
@Composable
fun NftItem(
    nftInfo: NftCacheEntity,
    onViewClick: (String?) -> Unit
) {    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp), // Reducido de 300dp a 220dp para mejor ajuste en una cuadrícula de 3 columnas
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp) // Reducido de 16dp a 12dp para mejor aprovechamiento del espacio
        ) {            
            // NFT Image            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(nftInfo.imageUrl)
                    .crossfade(true)
                    .size(coil.size.Size.ORIGINAL)
                    .memoryCacheKey(nftInfo.contractAddress)
                    .diskCacheKey(nftInfo.contractAddress)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .fallback(R.drawable.ic_launcher_background)
                    .build(),
                contentDescription = nftInfo.name,
                modifier = Modifier
                    .height(120.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                error = painterResource(id = R.drawable.ic_launcher_background),
                onLoading = { 
                    // Se podría mostrar un indicador de progreso aquí si se desea
                },
                onError = { 
                    // Log el error pero continúa mostrando la imagen de error
                    android.util.Log.e("NftItem", "Error loading image: ${nftInfo.imageUrl}", it.result.throwable)
                }
            )
            
            Spacer(modifier = Modifier.height(6.dp)) // Reducido de 8dp a 6dp

            // NFT Name
            Text(
                text = nftInfo.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp, // Reducido de 16sp a 14sp
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )            
            // Eliminamos la descripción y añadimos un espaciador para mantener el layout balanceado
            Spacer(modifier = Modifier.height(10.dp)) // Espaciador más grande para compensar

            // View Button
            Button(
                onClick = { onViewClick(nftInfo.imageUrl) },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp) // Botón más compacto
            ) {
                Text("View", fontSize = 12.sp) // Texto más pequeño
            }
        }
    }
}
