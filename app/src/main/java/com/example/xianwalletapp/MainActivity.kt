package com.example.xianwalletapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.xianwalletapp.crypto.XianCrypto
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.navigation.XianNavArgs
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.ui.screens.*
import com.example.xianwalletapp.ui.theme.XIANWALLETAPPTheme
import com.example.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() { // Changed inheritance
    private lateinit var walletManager: WalletManager
    private lateinit var networkService: XianNetworkService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize wallet manager and network service
        walletManager = WalletManager.getInstance(this)
        networkService = XianNetworkService.getInstance(this)
        
        // Set RPC and explorer URLs from wallet manager
        networkService.setRpcUrl(walletManager.getRpcUrl())
        networkService.setExplorerUrl(walletManager.getExplorerUrl())
        
        enableEdgeToEdge()
        setContent {
            XIANWALLETAPPTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    XianWalletApp(walletManager, networkService)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Clear the cached private key when the app is stopped
        walletManager.clearPrivateKeyCache()
        android.util.Log.d("MainActivity", "onStop called, clearing private key cache.")
    }

}

@Composable
fun XianWalletApp(walletManager: WalletManager, networkService: XianNetworkService) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() } // Hoist SnackbarHostState
    val coroutineScope = rememberCoroutineScope() // Hoist CoroutineScope
    var startDestination by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var requirePasswordVerification by remember { mutableStateOf(false) }
    var passwordVerified by remember { mutableStateOf(false) }
    // Determine start destination based on whether a wallet exists and if password is required
    LaunchedEffect(Unit) {
        delay(1000) // Brief delay for splash screen effect
        if (walletManager.hasWallet()) {
            // Check if password verification is required
            requirePasswordVerification = walletManager.getRequirePassword()
            android.util.Log.d("MainActivity", "Require password setting value: $requirePasswordVerification") // Add logging
            // Even if biometrics is enabled, we still need password verification screen
            // as it's where biometric auth is handled
            startDestination = XianDestinations.WALLET
        } else {
            startDestination = XianDestinations.WELCOME
        }
        isLoading = false
    }
    
    // Redirect to password verification if needed
    LaunchedEffect(requirePasswordVerification, passwordVerified, isLoading, startDestination) {
        if (requirePasswordVerification && !passwordVerified && !isLoading && startDestination == XianDestinations.WALLET) {
            android.util.Log.d("MainActivity", "Navigating to PASSWORD_VERIFICATION") // Add log here too
            navController.navigate(XianDestinations.PASSWORD_VERIFICATION) {
                // Clear backstack so user can't go back by pressing back button
                popUpTo(0) { inclusive = true }
            }
        }
    }
             android.util.Log.d("MainActivity", "Skipping navigation to PASSWORD_VERIFICATION. Conditions: require=$requirePasswordVerification, verified=$passwordVerified, loading=$isLoading, destination=$startDestination") // Add log for else case
    
    // Register composable screens
    NavHost(
        navController = navController,
        startDestination = if (isLoading) XianDestinations.SPLASH else startDestination
    ) {
        // Add a password verification route
        composable(XianDestinations.PASSWORD_VERIFICATION) {
            PasswordVerificationScreen(
                navController = navController,
                walletManager = walletManager,
                onPasswordVerified = {
                    passwordVerified = true
                    navController.navigate(XianDestinations.WALLET) {
                        popUpTo(0) { saveState = true }
                    }
                }
            )
        }
        
        composable(XianDestinations.SPLASH) {
            SplashScreen()
        }
        
        composable(XianDestinations.WELCOME) {
            WelcomeScreen(navController)
        }
        
        composable(XianDestinations.CREATE_WALLET) {
            CreateWalletScreen(navController, walletManager)
        }
        
        composable(XianDestinations.IMPORT_WALLET) {
            ImportWalletScreen(navController, walletManager)
        }
        
        composable(XianDestinations.WALLET) {
            WalletScreen(navController, walletManager, networkService)
        }
        
        composable(
            "${XianDestinations.SEND_TOKEN}?${XianNavArgs.TOKEN_CONTRACT}={${XianNavArgs.TOKEN_CONTRACT}}&${XianNavArgs.TOKEN_SYMBOL}={${XianNavArgs.TOKEN_SYMBOL}}"
        ) { backStackEntry ->
            val contract = backStackEntry.arguments?.getString(XianNavArgs.TOKEN_CONTRACT) ?: "currency"
            val symbol = backStackEntry.arguments?.getString(XianNavArgs.TOKEN_SYMBOL) ?: "XIAN"
            // Pass only the required arguments; viewModel is injected within the screen
            SendTokenScreen(navController, walletManager, contract, symbol)
        }
        
        composable(XianDestinations.RECEIVE_TOKEN) {
            ReceiveTokenScreen(navController, walletManager)
        }
        
        composable(
            route = "${XianDestinations.WEB_BROWSER}?url={url}", // Define route with optional arg
            arguments = listOf(
                navArgument("url") { // Define the argument
                    type = NavType.StringType
                    nullable = true // Make it optional
                    defaultValue = null // Default to null if not provided
                }
            )
        ) { backStackEntry ->
            val initialUrl = backStackEntry.arguments?.getString("url") // Extract the argument
            WebBrowserScreen(
                navController = navController,
                walletManager = walletManager, // Pass existing instance
                networkService = networkService, // Pass existing instance
                initialUrl = initialUrl // Pass the extracted URL
            )
        }
        
        composable(XianDestinations.ADVANCED) {
            AdvancedScreen(navController, walletManager, networkService)
        }
        
        composable(XianDestinations.NEWS) {
            NewsScreen(navController, walletManager, networkService)
        }
        
        composable(XianDestinations.SETTINGS) {
            SettingsScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                snackbarHostState = snackbarHostState, // Pass hoisted state
                coroutineScope = coroutineScope // Pass hoisted scope
            )
        }

        composable(XianDestinations.SETTINGS_SECURITY) {
            SecuritySettingsScreen(
                navController = navController,
                walletManager = walletManager,
                snackbarHostState = snackbarHostState, // Pass hoisted state
                coroutineScope = coroutineScope // Pass hoisted scope
            )
        }

        composable(XianDestinations.SETTINGS_NETWORK) {
            NetworkSettingsScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                snackbarHostState = snackbarHostState, // Pass hoisted state
                coroutineScope = coroutineScope // Pass hoisted scope
            )
        }
    }
}