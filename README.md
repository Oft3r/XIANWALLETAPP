# Xian Wallet App (Android)

## Description

Xian Wallet App is a native Android mobile wallet designed to interact with the Xian network. It allows users to securely manage their keys, send and receive tokens, and stay informed about the network.

## Main Features

*   **Wallet Management:**
    *   Create new Xian wallets.
    *   Import existing wallets using private keys.
    *   Secure storage of private keys using Android's `EncryptedSharedPreferences` and AES-256 GCM encryption.
    *   Password verification to unlock the wallet and access sensitive functions.
    *   Securely delete the wallet.
*   **Token Management:**
    *   Send and receive native Xian tokens (`currency`) and other contract-based tokens.
    *   Add and remove custom tokens from the visible list.
    *   (Potentially) Advanced transaction features.
*   **Network Interaction:**
    *   Connection to Xian network RPC nodes to fetch information and send transactions.
    *   Configuration of custom RPC and block explorer URLs.
    *   Use of default URLs for RPC (`https://node.xian.org`) and explorer (`https://explorer.xian.org`).
*   **User Interface (UI):**
    *   Modern interface built with Jetpack Compose.
    *   Dedicated screens for welcome, wallet creation/import, main wallet view, send, receive, settings, etc.
    *   QR code support (likely for addresses and/or payment requests).
*   **Additional Functionalities:**
    *   **News:** Integrated news section, possibly fetching data from Reddit.
    *   **Web Browser:** An in-app web browser, potentially for interacting with DApps via `XianWebViewBridge`.
    *   **Messaging:** A possible messaging feature (based on `MessengerScreen.kt`).
    *   **Security:** Option to require a password on application startup.

## How it Works

The application uses `WalletManager.kt` as the central component to handle wallet logic, including key generation (`XianCrypto.kt`), encryption, and secure storage. Interaction with the Xian network is handled via `XianNetworkService.kt` (likely using Retrofit). The user interface is built with Jetpack Compose, organizing different functionalities into `Screens` and using `ViewModels` to manage state.

## Technologies Used (Inferred)

*   **Language:** Kotlin
*   **UI:** Jetpack Compose
*   **Networking:** Retrofit
*   **Security:** AndroidX Security (EncryptedSharedPreferences, MasterKey)
*   **Persistence:** SharedPreferences (Encrypted)
*   **Asynchrony:** Kotlin Coroutines

## Donations

If you find this app useful, consider supporting its development with a small donation on XIAN:

`6da7b964efb6e1e6cdf1d13de5409fa4563e1497485c71dfaa30ac53d654d664`