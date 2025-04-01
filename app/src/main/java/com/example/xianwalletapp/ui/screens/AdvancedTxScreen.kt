package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.wallet.WalletManager

/**
 * Screen for advanced transaction operations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTxScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService
) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var gasLimit by remember { mutableStateOf("") }
    var gasPrice by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Transaction") },
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
        ) {
            // Recipient address field
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; errorMessage = null },
                label = { Text("Recipient Address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )
            
            // Amount field
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; errorMessage = null },
                label = { Text("Amount") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )
            
            // Gas limit field
            OutlinedTextField(
                value = gasLimit,
                onValueChange = { gasLimit = it; errorMessage = null },
                label = { Text("Gas Limit") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )
            
            // Gas price field
            OutlinedTextField(
                value = gasPrice,
                onValueChange = { gasPrice = it; errorMessage = null },
                label = { Text("Gas Price (Gwei)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )
            
            // Transaction data field
            OutlinedTextField(
                value = data,
                onValueChange = { data = it; errorMessage = null },
                label = { Text("Transaction Data (Hex)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(bottom = 16.dp)
            )
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Send transaction button
            Button(
                onClick = {
                    // TODO: Implement transaction sending logic
                    errorMessage = "Feature coming soon"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Send Transaction", fontSize = 16.sp)
            }
        }
    }
}