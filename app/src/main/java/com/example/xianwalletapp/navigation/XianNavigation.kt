package com.example.xianwalletapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
        composable(XianDestinations.WEB_BROWSER) {
            // TODO: Implement web browser screen
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
        }
    }
}