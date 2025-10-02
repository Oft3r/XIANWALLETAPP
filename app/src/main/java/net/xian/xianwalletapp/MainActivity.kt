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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : AppCompatActivity() { // Changed inheritance
    private lateinit var walletManager: WalletManager
    private lateinit var networkService: XianNetworkService
    private lateinit var faviconCacheManager: FaviconCacheManager // Declare FaviconCacheManager
    private var needsAuthentication = false // Track if we need authentication when resuming
    
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
                    XianWalletApp(walletManager, networkService, faviconCacheManager, this@MainActivity)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Mark that we need authentication when resuming if password is required
        if (walletManager.hasWallet() && walletManager.getRequirePassword()) {
            needsAuthentication = true
            android.util.Log.d("MainActivity", "onStop called, marking authentication needed")
        }
        // Clear the cached private key when the app is stopped
        walletManager.clearPrivateKeyCache()
        android.util.Log.d("MainActivity", "onStop called, clearing private key cache.")
    }

    override fun onResume() {
        super.onResume()
        // When app resumes and needs authentication, trigger biometric if available
        if (needsAuthentication && walletManager.hasWallet() && walletManager.getRequirePassword()) {
            val isBiometricEnabled = walletManager.isBiometricEnabled()
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
            val isBiometricAvailable = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
            
            android.util.Log.d("MainActivity", "onResume: needsAuthentication=true, biometric enabled=$isBiometricEnabled, available=$isBiometricAvailable")
            
            if (isBiometricEnabled && isBiometricAvailable) {
                // Trigger biometric authentication directly when resuming
                triggerBiometricAuthentication()
            }
            // Note: if biometric is not available, the Composable logic will handle showing password screen
        }
    }

    private fun triggerBiometricAuthentication() {
        val cipher = walletManager.getBiometricCipherForDecryption()
        if (cipher != null) {
            val executor = ContextCompat.getMainExecutor(this)
            
            val biometricPrompt = BiometricPrompt(this as FragmentActivity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        android.util.Log.e("MainActivity", "Biometric authentication error: $errString")
                        // Keep needsAuthentication true so password screen will show
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        result.cryptoObject?.cipher?.let { authenticatedCipher ->
                            if (walletManager.unlockWalletWithBiometricCipher(authenticatedCipher)) {
                                android.util.Log.d("MainActivity", "Biometric authentication successful on resume")
                                needsAuthentication = false // Clear the flag
                            } else {
                                android.util.Log.e("MainActivity", "Failed to unlock wallet with biometric cipher on resume")
                            }
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        android.util.Log.w("MainActivity", "Biometric authentication failed on resume, retrying")
                        // Don't change needsAuthentication, let user retry biometric
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Wallet")
                .setSubtitle("Use your fingerprint to unlock your wallet")
                .setNegativeButtonText("Use Password")
                .build()

            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } else {
            android.util.Log.e("MainActivity", "Failed to get biometric cipher on resume")
        }
    }

}

@Composable
fun XianWalletApp(
    walletManager: WalletManager,
    networkService: XianNetworkService,
    faviconCacheManager: FaviconCacheManager, // Add FaviconCacheManager parameter
    activity: MainActivity // Add MainActivity parameter for lifecycle management
) {
    val navController = rememberNavController()
    // Removed global SnackbarHostState and coroutineScope since custom toast system is used per-screen
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
        delay(3000) // Extended delay for splash screen effect to allow complete app initialization
        if (walletManager.hasWallet()) {
            // Check if password verification is required
            requirePasswordVerification = walletManager.getRequirePassword()
            android.util.Log.d("MainActivity", "Require password setting value: $requirePasswordVerification") // Add logging
            startDestination = XianDestinations.WALLET
        } else {
            startDestination = XianDestinations.WELCOME
        }
        isLoading = false
    }
    
    // Check if biometric authentication should be used instead of password screen
    val shouldUseBiometric = remember { mutableStateOf(false) }
    val biometricManager = BiometricManager.from(context)
    
    LaunchedEffect(requirePasswordVerification, passwordVerified, isLoading, startDestination) {
        if (requirePasswordVerification && !passwordVerified && !isLoading && startDestination == XianDestinations.WALLET) {
            // Always navigate to PASSWORD_VERIFICATION screen
            // Let PasswordVerificationScreen handle biometric vs password internally
            android.util.Log.d("MainActivity", "Navigating to PASSWORD_VERIFICATION")
            navController.navigate(XianDestinations.PASSWORD_VERIFICATION) {
                // Clear backstack so user can't go back by pressing back button
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
    // Handle biometric authentication when needed
    LaunchedEffect(shouldUseBiometric.value) {
        if (shouldUseBiometric.value) {
            // Trigger biometric authentication directly
            val cipher = walletManager.getBiometricCipherForDecryption()
            if (cipher != null) {
                // Setup biometric prompt
                val executor = ContextCompat.getMainExecutor(context)
                val activity = context as FragmentActivity
                
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            android.util.Log.e("MainActivity", "Biometric authentication error: $errString")
                            // On error, fall back to password verification
                            navController.navigate(XianDestinations.PASSWORD_VERIFICATION) {
                                popUpTo(0) { inclusive = true }
                            }
                            shouldUseBiometric.value = false
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            result.cryptoObject?.cipher?.let { authenticatedCipher ->
                                if (walletManager.unlockWalletWithBiometricCipher(authenticatedCipher)) {
                                    android.util.Log.d("MainActivity", "Biometric authentication successful, wallet unlocked")
                                    passwordVerified = true
                                    shouldUseBiometric.value = false
                                } else {
                                    android.util.Log.e("MainActivity", "Failed to unlock wallet with biometric cipher")
                                    // Fall back to password verification
                                    navController.navigate(XianDestinations.PASSWORD_VERIFICATION) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    shouldUseBiometric.value = false
                                }
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            android.util.Log.w("MainActivity", "Biometric authentication failed, retrying")
                            // Don't fall back to password on failed attempts, let user retry biometric
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Wallet")
                    .setSubtitle("Use your fingerprint to unlock your wallet")
                    .setNegativeButtonText("Use Password")
                    .build()

                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            } else {
                android.util.Log.e("MainActivity", "Failed to get biometric cipher, falling back to password")
                // Fall back to password verification if cipher is null
                navController.navigate(XianDestinations.PASSWORD_VERIFICATION) {
                    popUpTo(0) { inclusive = true }
                }
                shouldUseBiometric.value = false
            }
        }
    }
    
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
        
        // Swap screen (default XIAN/USDC pair)
        composable(XianDestinations.SWAP) {
            SwapScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                viewModel = walletViewModel
            )
        }
        
        // Swap screen with optional token parameters
        composable(
            route = "${XianDestinations.SWAP}?fromToken={fromToken}&toToken={toToken}",
            arguments = listOf(
                navArgument("fromToken") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("toToken") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fromToken = backStackEntry.arguments?.getString("fromToken")
            val toToken = backStackEntry.arguments?.getString("toToken")
            SwapScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                initialFromToken = fromToken,
                initialToToken = toToken,
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
        
        composable(XianDestinations.STAKING) {
            StakingScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService,
                navigationViewModel = viewModel(
                    factory = NavigationViewModelFactory(SavedStateHandle())
                )
            )
        }
        
        composable(XianDestinations.PORTFOLIO_ANALYSIS) {
            PortfolioAnalysisScreen(
                navController = navController,
                navigationViewModel = viewModel(
                    factory = NavigationViewModelFactory(SavedStateHandle())
                ),
                walletViewModel = walletViewModel
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
                walletViewModel = walletViewModel
            )
        }

        composable(XianDestinations.SETTINGS_SECURITY) {
            SecuritySettingsScreen(
                navController = navController,
                walletManager = walletManager
            )
        }

        composable(XianDestinations.SETTINGS_NETWORK) {
            NetworkSettingsScreen(
                navController = navController,
                walletManager = walletManager,
                networkService = networkService
            )
        }

        // Card Background Selector screen
        composable(XianDestinations.CARD_BACKGROUND_SELECTOR) {
            CardBackgroundSelectorScreen(
                navController = navController,
                viewModel = walletViewModel
            )
        }

        composable(
            route = "${XianDestinations.ADDRESS_BOOK}?prefilledAddress={prefilledAddress}",
            arguments = listOf(
                navArgument("prefilledAddress") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val prefilledAddress = backStackEntry.arguments?.getString("prefilledAddress")
            AddressBookScreen(
                navController = navController,
                prefilledAddress = prefilledAddress,
                onAddressSelected = { selectedAddress ->
                    // Save selected address in savedStateHandle to be retrieved by SendToken screen
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", selectedAddress)
                    navController.popBackStack()
                }
            )
        }
    }
}