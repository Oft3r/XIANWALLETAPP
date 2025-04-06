package com.example.xianwalletapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.xianwalletapp.ui.screens.WebBrowserScreen // Import the screen
// SnakeGameScreen import removed
import com.example.xianwalletapp.wallet.WalletManager // Assuming you need these
import com.example.xianwalletapp.network.XianNetworkService // Assuming you need these
/**
 * Navigation routes for the Xian wallet app
 */
object XianDestinations {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val CREATE_WALLET = "create_wallet"
    const val IMPORT_WALLET = "import_wallet"
    const val WALLET = "wallet"
    const val SEND_TOKEN = "send_token"
    const val RECEIVE_TOKEN = "receive_token"
    const val WEB_BROWSER = "web_browser"
    const val MESSENGER = "messenger"
    const val NEWS = "news"
    const val SETTINGS = "settings"
    const val PASSWORD_VERIFICATION = "password_verification"
    const val SETTINGS_SECURITY = "settings_security"
    const val SETTINGS_NETWORK = "settings_network"
    // SNAKE_GAME destination removed

}

/**
 * Navigation arguments
 */
object XianNavArgs {
    const val TOKEN_CONTRACT = "token_contract"
    const val TOKEN_SYMBOL = "token_symbol"
}

/**
 * Main navigation graph for the Xian wallet app
 */
@Composable
fun XianNavGraph(
    navController: NavHostController,
    walletManager: WalletManager, // Add WalletManager parameter
    networkService: XianNetworkService, // Add XianNetworkService parameter
    startDestination: String = XianDestinations.SPLASH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Splash screen
        composable(XianDestinations.SPLASH) {
            // TODO: Implement splash screen
        }

        // Welcome screen (create or import wallet)
        composable(XianDestinations.WELCOME) {
            // TODO: Implement welcome screen
        }

        // Create wallet screen
        composable(XianDestinations.CREATE_WALLET) {
            // TODO: Implement create wallet screen
        }

        // Import wallet screen
        composable(XianDestinations.IMPORT_WALLET) {
            // TODO: Implement import wallet screen
        }

        // Main wallet screen
        composable(XianDestinations.WALLET) {
            // TODO: Implement wallet screen
        }

        // Send token screen
        composable(
            route = "${XianDestinations.SEND_TOKEN}?${XianNavArgs.TOKEN_CONTRACT}={${XianNavArgs.TOKEN_CONTRACT}}&${XianNavArgs.TOKEN_SYMBOL}={${XianNavArgs.TOKEN_SYMBOL}}"
        ) {
            // TODO: Implement send token screen
        }

        // Receive token screen
        composable(XianDestinations.RECEIVE_TOKEN) {
            // TODO: Implement receive token screen
        }

        // Web Browser screen
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
            // Instances are now passed as parameters to XianNavGraph

            WebBrowserScreen(
                navController = navController,
                walletManager = walletManager, // Pass the instance from XianNavGraph parameters
                networkService = networkService, // Pass the instance from XianNavGraph parameters
                initialUrl = initialUrl // Pass the extracted URL
            )
        }

        // Messenger screen
        composable(XianDestinations.MESSENGER) {
            // TODO: Implement messenger screen
        }

        // News screen
        composable(XianDestinations.NEWS) {
            // TODO: Implement news screen
        }

        // Settings screen
        composable(XianDestinations.SETTINGS) {
            // TODO: Implement settings screen
            // Note: The actual SettingsScreen composable is likely called from MainActivity or similar,
            // passing the navController. This NavHost entry just defines the route.
            // If SettingsScreen itself needs to be defined here, it would look like:
            // SettingsScreen(navController = navController, /* other required params */)
        }

        // Snake Game composable block removed
    }
}