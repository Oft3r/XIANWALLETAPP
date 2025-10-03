# Xian Wallet App (Android)

## Description

Xian Wallet App is a native Android mobile wallet designed to interact with the Xian network. It allows users to securely manage their keys, send and receive tokens and NFTs, interact with the network, and access additional features.

## Main Features

*   **Wallet Management:**
    *   Create new Xian wallets.
    *   Import existing wallets using private keys.
    *   Secure storage of private keys using Android's `EncryptedSharedPreferences` and AES-256 GCM encryption.
    *   Password verification to unlock the wallet and access sensitive functions.
    *   Securely delete the wallet.
*   **Token & NFT Management:**
    *   Send and receive native Xian tokens (`currency`) and other contract-based tokens.
    *   Add and remove custom tokens from the visible list.
    *   View NFTs associated with the wallet address.
    *   Set a preferred NFT contract for display in the wallet header.
    *   Advanced transaction options available.
*   **Network Interaction:**
    *   Connection to Xian network RPC nodes to fetch information and send transactions.
    *   Configuration of custom RPC and block explorer URLs.
    *   Use of default URLs for RPC (`https://node.xian.org`) and explorer (`https://explorer.xian.org`).
*   **User Interface (UI):**
    *   Modern interface built with Jetpack Compose.
    *   Dedicated screens for welcome, wallet creation/import, main wallet view, send, receive, settings, etc.
    *   QR code generation for receiving tokens easily.
*   **Additional Functionalities:**
    *   **News:** Integrated news section fetching data from the Xian Reddit community.
    *   **Web Browser:** An in-app web browser for interacting with DApps or websites, integrated with wallet functions via `XianWebViewBridge`.
    *   **DApp Bubbles:** Minimize any DApp/web page into a draggable floating bubble that stays accessible across all screens. Tap to restore the session instantly.
    *   **Security Settings:** Option to require a password on application startup.
    *   **About Xian:** Screen providing information about the Xian network.
    *   **Snake Game:** A simple integrated Snake game.

## Portfolio Analysis (AI)

Get a clear view of your portfolio's composition, performance, and personalized insights.

- Overview: Total USD value, token allocation pie chart, and a 7-day weighted performance sparkline.
- Per-token details: Balance, USD value, and percentage weight.
- Insights: Optional AI-generated "Projection & Recommendations" summarizing trends and actionable suggestions.
- Access: From the Wallet screen, tap "Portfolio Analysis", or navigate to `portfolio_analysis`.

Requirements and behavior:
- Minimum balance gate: Requires at least $4.00 USDC equivalent in `XWT` to open the analysis screen.
- AI fee: Requesting an AI analysis costs $0.04 USDC (paid in `XWT`). The fee is only charged after a successful analysis.
- API key: Set an OpenRouter API key and preferred model in Settings -> AI. Keys are stored on-device.
- Models: Defaults to `openrouter/auto`. You can choose other supported models and test your configuration from the Settings screen.

Notes:
- If no API key is configured or an error occurs, the app falls back to a local summary with conservative guidance.
- The AI prompt is strictly constrained to your on-screen portfolio (no external tokens are suggested).

## Floating DApp Bubbles

Quickly minimize DApps to continue navigating the app, then restore with a tap.

- Minimize: In the Web Browser options menu, select "Minimize". The current page state is saved.
- Overlay: A draggable floating bubble (with site icon when available) appears and remains accessible on every screen.
- Restore: Tap the bubble to reopen the DApp in the Web Browser and continue exactly where you left off.
- Multiple sessions: You can keep multiple minimized DApps. Each bubble is independently draggable; positions persist during the session.
- Favicon caching: Favicons are cached to improve recognition and load times.

## How it Works

The application uses `WalletManager.kt` as the central component to handle wallet logic, including key generation (`XianCrypto.kt`), encryption, and secure storage. Interaction with the Xian network is handled via `XianNetworkService.kt` (using Retrofit). The user interface is built with Jetpack Compose, organizing different functionalities into `Screens` and using `ViewModels` to manage state.

## Technologies Used

*   **Language:** Kotlin
*   **UI:** Jetpack Compose
*   **Networking:** Retrofit
*   **Security:** AndroidX Security (EncryptedSharedPreferences, MasterKey)
*   **Persistence:** SharedPreferences (Encrypted)
*   **Asynchrony:** Kotlin Coroutines

## Donations

If you find this app useful, consider supporting its development with a small donation on XIAN:

`6da7b964efb6e1e6cdf1d13de5409fa4563e1497485c71dfaa30ac53d654d664`
