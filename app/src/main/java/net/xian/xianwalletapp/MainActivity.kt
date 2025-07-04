package net.xian.xianwalletapp

import net.xian.xianwalletapp.workers.scheduleTransactionMonitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
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
import net.xian.xianwalletapp.crypto.XianCrypto
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.navigation.XianNavArgs
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.ui.screens.*
import net.xian.xianwalletapp.ui.theme.XIANWALLETAPPTheme
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.data.FaviconCacheManager // Import FaviconCacheManager
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModel // Import NavigationViewModel
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModelFactory // Import NavigationViewModelFactory
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel // Import WalletViewModel
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModelFactory // Import WalletViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() { // Changed inheritance
    private lateinit var walletManager: WalletManager
    private lateinit var networkService: XianNetworkService
    private lateinit var faviconCacheManager: FaviconCacheManager // Declare FaviconCacheManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize wallet manager and network service
        walletManager = WalletManager.getInstance(this)
        networkService = XianNetworkService.getInstance(this)
        faviconCacheManager = FaviconCacheManager(applicationContext) // Initialize FaviconCacheManager
        
        // Set RPC and explorer URLs from wallet manager
        networkService.setRpcUrl(walletManager.getRpcUrl())
        networkService.setExplorerUrl(walletManager.getExplorerUrl())

        // Iniciar el monitoreo de transacciones con WorkManager
        scheduleTransactionMonitor(this)

        // Configuración edge-to-edge para tener en cuenta la barra de navegación del sistema
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.parseColor("#1A1A1A"), // Dark color for navigation bar
                android.graphics.Color.parseColor("#F5F5F5")   // Light color for navigation bar
            )
        )

        setContent {
            XIANWALLETAPPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XianWalletApp(walletManager, networkService, faviconCacheManager)
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
fun XianWalletApp(
    walletManager: WalletManager,
    networkService: XianNetworkService,
    faviconCacheManager: FaviconCacheManager // Add FaviconCacheManager parameter
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() } // Hoist SnackbarHostState
    val coroutineScope = rememberCoroutineScope() // Hoist CoroutineScope
    var startDestination by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var requirePasswordVerification by remember { mutableStateOf(false) }
    var passwordVerified by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Create a shared WalletViewModel scoped to the NavHost
    val walletViewModel: WalletViewModel = viewModel(
        factory = WalletViewModelFactory(context, walletManager, networkService)
    )
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
            WalletScreen(navController, walletManager, networkService, walletViewModel)
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
        
        // Token detail screen
        composable(
            route = "${XianDestinations.TOKEN_DETAIL}?${XianNavArgs.TOKEN_CONTRACT}={${XianNavArgs.TOKEN_CONTRACT}}&${XianNavArgs.TOKEN_SYMBOL}={${XianNavArgs.TOKEN_SYMBOL}}",
            arguments = listOf(
                navArgument(XianNavArgs.TOKEN_CONTRACT) {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument(XianNavArgs.TOKEN_SYMBOL) {
                    type = NavType.StringType
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val tokenContract = backStackEntry.arguments?.getString(XianNavArgs.TOKEN_CONTRACT) ?: "currency"
            val tokenSymbol = backStackEntry.arguments?.getString(XianNavArgs.TOKEN_SYMBOL) ?: "XIAN"
            TokenDetailScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                tokenContract = tokenContract,
                tokenSymbol = tokenSymbol,
                viewModel = walletViewModel
            )
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
                faviconCacheManager = faviconCacheManager, // Pass FaviconCacheManager instance
                initialUrl = initialUrl // Pass the extracted URL
            )
        }
          composable(XianDestinations.ADVANCED) {
            AdvancedScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                // Share the NavigationViewModel
                navigationViewModel = viewModel(
                    factory = NavigationViewModelFactory(SavedStateHandle())
                )
            )
        }
        
        composable(XianDestinations.NEWS) {
            NewsScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                // Share the NavigationViewModel
                navigationViewModel = viewModel(
                    factory = NavigationViewModelFactory(SavedStateHandle())
                )
            )
        }
        
        composable(XianDestinations.SETTINGS) {
            // SettingsScreen now collects active wallet state directly from WalletManager
            // No need to pass walletAddress or preferredNftContract as parameters anymore
            // val walletAddress = walletManager.getActiveWalletPublicKey() // No longer needed here
            // val preferredNftContract = walletManager.getPreferredNftContract() // No longer needed here
            // android.util.Log.d("MainActivity", "Navigating to SettingsScreen")

            SettingsScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                snackbarHostState = snackbarHostState, // Pass hoisted state
                coroutineScope = coroutineScope // Pass hoisted scope
                // Removed preferredNftContract parameter
                // Removed walletAddress parameter
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