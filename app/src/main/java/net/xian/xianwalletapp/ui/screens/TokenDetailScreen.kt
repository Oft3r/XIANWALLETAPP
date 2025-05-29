package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.navigation.XianNavArgs
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModelFactory
import net.xian.xianwalletapp.wallet.WalletManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Token detail screen showing price card, balance, and action buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    tokenContract: String,
    tokenSymbol: String,
    viewModel: WalletViewModel = viewModel(
        factory = WalletViewModelFactory(LocalContext.current, walletManager, networkService)
    )
) {
    val context = LocalContext.current
    
    // Collect states from ViewModel
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    val poopPrice by viewModel.poopPrice.collectAsStateWithLifecycle()
    val xtfuPrice by viewModel.xtfuPrice.collectAsStateWithLifecycle()
    
    // Get token information
    val tokenInfo = tokenInfoMap[tokenContract]
    val balance = balanceMap[tokenContract] ?: 0f
    val tokenName = tokenInfo?.name ?: tokenContract
    val logoUrl = tokenInfo?.logoUrl
    
    // Calculate USD/XIAN value based on token type
    val tokenPrice = when (tokenContract) {
        "currency" -> xianPrice
        "con_poop_coin" -> poopPrice
        "con_xtfu" -> xtfuPrice
        else -> null
    }    // Create formatters for different values
    val usdFormatter = DecimalFormat("#,##0.0000") // For USD values (4 decimals)
    val priceFormatter = DecimalFormat("#,##0.000000") // For token prices in XIAN (6 decimals)
    val balanceFormatter = DecimalFormat("#,##0.####")
    
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Text(
                        text = tokenName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {            // Price Card (similar to total balance card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(colors = listOf(XianPrimary, XianPrimaryVariant))
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {                    Text(
                        text = "Token Price",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (tokenPrice != null) {
                        Text(
                            text = if (tokenContract == "currency") {
                                "$${usdFormatter.format(tokenPrice)} USD"
                            } else {
                                "${priceFormatter.format(tokenPrice)} XIAN"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Price not available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Token Balance and Logo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Balance information
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Balance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "${balanceFormatter.format(balance)} $tokenSymbol",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                          // Show USD/XIAN equivalent if price is available
                        if (tokenPrice != null && balance > 0) {
                            val totalValue = balance * tokenPrice
                            Text(
                                text = if (tokenContract == "currency") {
                                    "≈ $${usdFormatter.format(totalValue)} USD"
                                } else {
                                    "≈ ${priceFormatter.format(totalValue)} XIAN"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Token logo
                    // Use Image composable for local resource, AsyncImage for URL
                    if (tokenContract == "currency") {
                        Image(
                            painter = painterResource(id = R.drawable.xian_logo), // Use local resource directly
                            contentDescription = "$tokenName Logo",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Inside
                        )
                    } else {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = "$tokenName Logo",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Inside,
                            error = painterResource(id = R.drawable.ic_question_mark),
                            placeholder = painterResource(id = R.drawable.ic_question_mark)
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Send Button
                Button(
                    onClick = {
                        navController.navigate(
                            "${XianDestinations.SEND_TOKEN}?${XianNavArgs.TOKEN_CONTRACT}=$tokenContract&${XianNavArgs.TOKEN_SYMBOL}=$tokenSymbol"
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    colors = xianButtonColors(XianButtonType.PRIMARY),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Receive Button
                Button(
                    onClick = {
                        navController.navigate(XianDestinations.RECEIVE_TOKEN)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    colors = xianButtonColors(XianButtonType.SECONDARY),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Receive",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Receive",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Swap Button (was Exchange)
                Button(
                    onClick = {
                        // Abrir Swap igual que en WalletScreen
                        val urlToLoad = "https://snakexchange.org/"
                        val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                        navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Swap",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Swap", // Cambiado de "Exchange" a "Swap"
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Additional token information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Token Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Contract address
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Contract:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (tokenContract == "currency") "Native XIAN" else tokenContract,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Symbol
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Symbol:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tokenSymbol,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
