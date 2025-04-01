package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
import com.example.xianwalletapp.utils.CryptoUtils // Import CryptoUtils
import kotlinx.coroutines.launch // Import coroutine scope
import java.nio.charset.StandardCharsets

/**
 * Screen for the decentralized messenger functionality
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class) // Added ExperimentalStdlibApi
@Composable
fun MessengerScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService
) {
    var messageText by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope() // Scope for launching coroutines

    // List to hold messages (Ideally managed by a ViewModel)
    val messages = remember { mutableStateListOf<MessageItem>() }


    // Effect to load messages on initial composition
    LaunchedEffect(Unit) {
        // TODO: Implement actual message loading
        println("MessengerScreen: LaunchedEffect - Simulating message loading")
        // 1. Get user keys
        val userPublicKey = walletManager.getPublicKey()
        // TODO: Securely obtain decrypted private key bytes.
        // This requires the user's password, e.g., via walletManager.unlockWallet(password)
        // val decryptedPrivateKeyBytes: ByteArray? = getDecryptedPrivateKeySecurely()
        val decryptedPrivateKeyBytes: ByteArray? = null // Placeholder

        if (userPublicKey == null /* || decryptedPrivateKeyBytes == null */) { // Uncomment when key retrieval is implemented
             errorMessage = "Cannot load messages: Wallet keys unavailable or not unlocked."
             return@LaunchedEffect
        }

        // TODO: Convert private key bytes to hex seed if needed by CryptoUtils, or use bytes directly
        // val userPrivateKeySeedHex = decryptedPrivateKeyBytes?.toHexString() // Example if hex is needed

        // 2. TODO: Fetch encrypted messages from network (XianNetworkService)
        // val networkMessages = networkService.fetchMessages(userPublicKey)
        val simulatedNetworkMessages = listOf<Pair<String, String>>() // Pair<Sender, EncryptedHex>

        // 3. TODO: Load locally stored sent messages
        // val localSentMessages = loadMessagesFromLocalStorage(userPublicKey)
        val simulatedLocalSentMessages = listOf<MessageItem>()

        // 4. Decrypt network messages (using implemented CryptoUtils)
        val decryptedMessages = simulatedNetworkMessages.mapNotNull { (sender, encryptedHex) ->
             try {
                 // TODO: Ensure decryptedPrivateKeyBytes is not null before proceeding
                 if (decryptedPrivateKeyBytes == null) {
                     println("Cannot decrypt: Private key not available")
                     return@mapNotNull null
                 }
                 // Convert keys needed for decryption
                 val userPrivateKeySeedHex = decryptedPrivateKeyBytes.toHexString() // Convert seed bytes to hex
                 val userCurvePrivKey = CryptoUtils.convertEd25519PrivateKeyToCurve25519(userPrivateKeySeedHex)
                 val userCurvePubKey = CryptoUtils.convertEd25519PublicKeyToCurve25519(userPublicKey)

                 val decryptedBytes = CryptoUtils.decryptBoxSealOpenEquivalent(
                     encryptedHex,
                     userCurvePubKey,
                     userCurvePrivKey
                 )

                 if (decryptedBytes != null) {
                     // TODO: Get actual timestamp from network message data
                     MessageItem(sender, decryptedBytes.toString(StandardCharsets.UTF_8), "Decrypted Time", false)
                 } else {
                     println("Decryption returned null for message from $sender")
                     null
                 }
             } catch (e: Exception) {
                 println("Decryption failed for message from $sender: $e")
                 null // Skip message if decryption fails
             }
         }
        // val simulatedDecryptedMessages = listOf<MessageItem>() // Keep simulation if needed for testing

        // 5. Combine and update UI list
        messages.clear()
        messages.addAll(simulatedLocalSentMessages) // Add locally stored sent messages
        messages.addAll(decryptedMessages) // Add decrypted network messages
        // TODO: Sort combined messages by timestamp
        println("MessengerScreen: Message loading simulation complete.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xian Messenger") },
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
                .padding(16.dp)
        ) {
            // Description Text
            Text(
                text = "Send encrypted messages to other users on the chain. Only the recipient can decrypt and read the message, no one else. Your 100% privacy is guaranteed.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Recipient field
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; errorMessage = null },
                label = { Text("Recipient Address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Messages list
            Text(
                text = "Messages",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Beta Feature: Messenger may not function correctly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp).align(Alignment.CenterHorizontally)
            )


            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages) { message ->
                    MessageCard(message)
                }
            }

            // Message input and send button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Type a message") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() && recipient.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    errorMessage = null
                                    val senderPublicKey = walletManager.getPublicKey()
                                    // TODO: Securely obtain decrypted private key bytes.
                                    // This requires the user's password, e.g., via walletManager.unlockWallet(password)
                                    // val decryptedPrivateKeyBytes: ByteArray? = getDecryptedPrivateKeySecurely()
                                    val decryptedPrivateKeyBytes: ByteArray? = null // Placeholder

                                    val recipientPublicKey = recipient // Assuming recipient is a valid public key hex

                                    if (senderPublicKey == null /* || decryptedPrivateKeyBytes == null */) { // Uncomment when key retrieval is implemented
                                        errorMessage = "Wallet keys not available or not unlocked."
                                        return@launch
                                    }

                                    // 1. Convert recipient public key
                                    val recipientCurveKey = try {
                                        CryptoUtils.convertEd25519PublicKeyToCurve25519(recipientPublicKey)
                                    } catch (e: Exception) {
                                        errorMessage = "Invalid recipient public key format: ${e.message}"
                                        return@launch
                                    }

                                    // 2. Encrypt message using implemented CryptoUtils function
                                    val messageBytes = messageText.toByteArray(StandardCharsets.UTF_8)
                                    val encryptedMessageHex = try {
                                        CryptoUtils.encryptBoxSealEquivalent(messageBytes, recipientCurveKey)
                                    } catch (e: Exception) {
                                         errorMessage = "Encryption failed: ${e.message}"
                                         return@launch
                                    }

                                    // 3. TODO: Build and sign transaction using XianNetworkService
                                    // Requires the decrypted private key bytes for signing
                                    // val transaction = buildSaveMessageTransaction(encryptedMessageHex, recipientPublicKey)
                                    // val signedTx = signTransaction(transaction, decryptedPrivateKeyBytes!!) // Pass actual bytes

                                    // 4. TODO: Broadcast transaction using XianNetworkService
                                    // val success = broadcastTransaction(signedTx)

                                    // 5. If successful:
                                    // TODO: Implement local storage for sent messages
                                    // saveMessageLocally(senderPublicKey, recipientPublicKey, messageText, timestamp)

                                    // Add to UI temporarily (replace with load from storage/network)
                                    messages.add(
                                        MessageItem(
                                            senderPublicKey,
                                            messageText, // Show plaintext for outgoing
                                            "Sending...", // Placeholder time
                                            true
                                        )
                                    )
                                    messageText = "" // Clear input

                                    // TODO: Trigger message list refresh after successful send + local save
                                    // loadMessages()

                                } catch (e: Exception) {
                                    // TODO: Provide more specific error handling
                                    errorMessage = "Error sending message: ${e.message}"
                                    println("Error sending message: $e")
                                }
                            }
                        } else {
                            errorMessage = "Please enter a message and recipient address"
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Data class for message items
 */
data class MessageItem(
    val sender: String,
    val content: String,
    val time: String,
    val isOutgoing: Boolean
)

/**
 * Composable for displaying a message card
 */
@Composable
fun MessageCard(message: MessageItem) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.isOutgoing) "You" else message.sender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    text = message.time,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}