---
description: Repository Information Overview
alwaysApply: true
---

# Xian Wallet App Information

## Summary
Xian Wallet App is a native Android mobile wallet designed to interact with the Xian network. It allows users to securely manage their keys, send and receive tokens and NFTs, interact with the network, and access additional features like an integrated news section and web browser.

## Structure
- **app/**: Main application module containing all source code
  - **src/main/java/net/xian/xianwalletapp/**: Core application code
  - **src/main/res/**: Android resources (layouts, drawables, values)
- **gradle/**: Gradle configuration files and wrapper
- **scripts/**: Utility scripts for development

## Language & Runtime
**Language**: Kotlin
**Version**: Kotlin 2.0.21
**Build System**: Gradle 8.11.1
**Package Manager**: Gradle
**Android SDK**: 
- **Compile SDK**: 35
- **Target SDK**: 35
- **Min SDK**: 26

## Dependencies
**Main Dependencies**:
- **UI**: Jetpack Compose 2024.09.00, Material3
- **Networking**: Retrofit 2.9.0, OkHttp 4.12.0
- **Security**: AndroidX Security Crypto 1.1.0-alpha06, Bouncy Castle 1.79
- **Database**: Room 2.6.1
- **Crypto**: Bitcoinj 0.16.2
- **QR Code**: ZXing 3.5.3, ML Kit Barcode Scanning 17.3.0
- **Camera**: CameraX 1.4.0
- **Charts**: Vico 1.15.0

**Development Dependencies**:
- Android Gradle Plugin 8.10.0
- Kotlin Compose Plugin 2.0.21

## Build & Installation
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Main Components
- **WalletManager.kt**: Central component for wallet management
- **XianCrypto.kt**: Cryptographic operations for the wallet
- **XianNetworkService.kt**: Network interaction with Xian blockchain
- **XianNavigation.kt**: Navigation system for the app
- **MainActivity.kt**: Main entry point for the application

## Features
- **Wallet Management**: Create, import, and secure wallets
- **Token & NFT Management**: Send/receive tokens, manage NFTs
- **Network Interaction**: Connect to Xian network nodes
- **Web Browser**: In-app browser with wallet integration
- **News Section**: Integrated news from Xian Reddit community
- **Security**: Password protection, encrypted storage
- **Snake Game**: Simple integrated game