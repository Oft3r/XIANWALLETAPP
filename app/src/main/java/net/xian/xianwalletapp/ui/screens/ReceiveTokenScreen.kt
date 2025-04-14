package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.xian.xianwalletapp.wallet.WalletManager
import android.content.Intent
import net.xian.xianwalletapp.utils.QrCodeUtils
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors

/**
 * Screen for receiving tokens by displaying the user's wallet address
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveTokenScreen(
    navController: NavController,
    walletManager: WalletManager
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val publicKey = walletManager.getPublicKey() ?: ""
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Tokens") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Your Wallet Address",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Address card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp) // Reduced bottom padding
                    .clickable { // Added clickable modifier for sharing
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "My Xian Network wallet address: $publicKey")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share wallet address")
                        context.startActivity(shareIntent)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = publicKey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // QR Code Display
            val qrBitmap = remember(publicKey) {
                QrCodeUtils.generateQrCodeBitmap(publicKey, width = 256, height = 256)
            }
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Wallet Address QR Code",
                    modifier = Modifier
                        .size(256.dp) // Adjust size as needed
                        .padding(bottom = 32.dp)
                )
            } else {
                // Optional: Display a placeholder or error message if QR generation fails
                Text(
                    text = "Could not generate QR code.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
            
            
            // Instructions
            Text(
                text = "Share this address with anyone who wants to send you tokens on the Xian Network.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Action buttons
            // Copy button (now takes full width)
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(publicKey))
                    kotlinx.coroutines.MainScope().launch {
                        snackbarHostState.showSnackbar("Address copied to clipboard")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp), // Keep vertical padding
                colors = xianButtonColors(XianButtonType.SECONDARY)
            ) {
                Icon(
                    Icons.Default.ContentCopy, // Using ContentCopy icon for clarity
                    contentDescription = "Copy Address",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Address")
            }
            // Removed Share button as Card is now clickable for sharing
        }
    }
}