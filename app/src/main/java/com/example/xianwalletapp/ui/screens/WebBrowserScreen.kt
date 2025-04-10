package com.example.xianwalletapp.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.wallet.WalletManager
import com.example.xianwalletapp.wallet.XianWebViewBridge
import com.example.xianwalletapp.wallet.AuthRequestListener
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch

/**
 * Web Browser screen with URL address bar and WebView
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebBrowserScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    initialUrl: String? = null // Argument for initial URL
) {
    // Decode the initial URL if provided, otherwise use default
    val decodedInitialUrl = remember(initialUrl) {
        initialUrl?.let {
            try {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                android.util.Log.e("WebBrowserScreen", "Failed to decode URL: $it", e)
                "https://xian.org" // Fallback on decoding error
            }
        } ?: "https://xian.org" // Default if null
    }

    var url by remember { mutableStateOf(decodedInitialUrl) }
    var currentUrl by remember { mutableStateOf(decodedInitialUrl) } // Initialize currentUrl as well
    var isLoading by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // State for managing the custom JS prompt dialog
    var showJsPromptDialog by remember { mutableStateOf(false) }
    var jsPromptRequest by remember { mutableStateOf<JsPromptRequest?>(null) }

    // State for the pre-authentication password dialog
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var authCallbacks by remember { mutableStateOf<Pair<((String) -> Unit), (() -> Unit)>?>(null) }
    var txDetailsForAuth by remember { mutableStateOf<String?>(null) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web Browser") },
                navigationIcon = {
                    IconButton(onClick = {
                        val webView = webViewRef.value
                        if (webView?.canGoBack() == true) {
                            webView.goBack() // Navigate back in WebView history
                        } else {
                            navController.popBackStack() // Navigate back in the app
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // URL address bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            // Ensure URL has http/https prefix
                            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                "https://$url"
                            } else {
                                url
                            }
                            url = formattedUrl
                            currentUrl = formattedUrl
                            webViewRef.value?.loadUrl(formattedUrl)
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                
                // Go button
                Button(
                    onClick = {
                        // Ensure URL has http/https prefix
                        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            "https://$url"
                        } else {
                            url
                        }
                        url = formattedUrl
                        currentUrl = formattedUrl
                        webViewRef.value?.loadUrl(formattedUrl)
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Go")
                }
                
                // Refresh button
                IconButton(onClick = { 
                    webViewRef.value?.reload()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            
            // WebView
            AndroidView(
                factory = { context ->
                    // --- Create AuthRequestListener Implementation ---
                    val authListener = object : AuthRequestListener {
                        override fun requestAuth(
                            txDetailsJson: String,
                            onSuccess: (txDetailsJson: String) -> Unit,
                            onFailure: () -> Unit
                        ) {
                            // Store callbacks and details, then show dialog
                            txDetailsForAuth = txDetailsJson
                            authCallbacks = Pair(onSuccess, onFailure)
                            passwordInput = "" // Clear previous input
                            authErrorMessage = null // Clear previous error
                            showPasswordDialog = true
                        }
                    }
                    // --- End AuthRequestListener Implementation ---

                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }
                            
                            // Rename lambda parameter to avoid collision with state variable 'url'
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                super.onPageFinished(view, loadedUrl) // Use renamed parameter
                                isLoading = false
                                // Use the renamed parameter 'loadedUrl' here as well
                                loadedUrl?.let { newUrl ->
                                    // Assign the loaded URL to the composable state variable 'url'
                                    url = newUrl // This 'url' is the state variable, which is correct
                                }
                                
                                // Inject JavaScript to intercept and handle events from dapp.js
                                val jsCode = """
                                (function() {
                                    // Flag to track if we've already initialized
                                    if (window.xianWalletInjected) return;
                                    window.xianWalletInjected = true;
                                    
                                    // Create a debug logger
                                    window.xianDebug = function(message, data) {
                                        console.log('XIAN-DEBUG: ' + message, data);
                                        // Log to Android for debugging
                                        if (typeof XianWalletBridge !== 'undefined') {
                                            XianWalletBridge.logDebug(message + (data ? ': ' + JSON.stringify(data) : ''));
                                        }
                                    };
                                    
                                    console.log('Xian Wallet Android bridge initialized');
                                    window.xianDebug('Bridge initialization started');
                                    
                                    // Improved error handling for all callbacks
                                    window.xianHandleError = function(error, callback) {
                                        console.error('XIAN-ERROR:', error);
                                        window.xianDebug('ERROR', error);
                                        
                                        if (callback) {
                                            callback({
                                                success: false,
                                                error: typeof error === 'string' ? error : (error.message || 'Unknown error')
                                            });
                                        }
                                    };
                                    
                                    // Listen for wallet info requests
                                    document.addEventListener('xianWalletGetInfo', function() {
                                        window.xianDebug('xianWalletGetInfo event received');
                                        try {
                                            const walletInfo = JSON.parse(XianWalletBridge.getWalletInfo());
                                            document.dispatchEvent(new CustomEvent('xianWalletInfo', {
                                                detail: walletInfo
                                            }));
                                        } catch (error) {
                                            window.xianHandleError(error);
                                        }
                                    });
                                    
                                    // Listen for sign message requests
                                    document.addEventListener('xianWalletSignMsg', function(event) {
                                        window.xianDebug('xianWalletSignMsg event received', event.detail);
                                        let passwordToUse = null; // Initialize password as null

                                        // Check if password was required on startup via the bridge
                                        const requirePasswordOnStartup = XianWalletBridge.isPasswordRequiredOnStartup();
                                        window.xianDebug('Password required on startup? ' + requirePasswordOnStartup);

                                        if (!requirePasswordOnStartup) {
                                            // If password NOT required on startup, prompt for it now
                                            passwordToUse = prompt('Enter your wallet password to sign the message', '');
                                            if (!passwordToUse) { // Check if user cancelled prompt
                                                document.dispatchEvent(new CustomEvent('xianWalletSignMsgResponse', {
                                                    detail: { success: false, error: 'User cancelled the operation' }
                                                }));
                                                return; // Stop if user cancelled prompt
                                            }
                                            // If user entered password, passwordToUse holds it
                                        }
                                        // If password WAS required on startup, passwordToUse remains null,
                                        // signaling the bridge to try the cached key.

                                        try {
                                            // Call signMessage with the message and passwordToUse (which might be null)
                                            window.xianDebug('Calling XianWalletBridge.signMessage. Password provided: ' + (passwordToUse !== null));
                                            const result = JSON.parse(XianWalletBridge.signMessage(event.detail.message, passwordToUse));
                                            document.dispatchEvent(new CustomEvent('xianWalletSignMsgResponse', {
                                                detail: result
                                            }));
                                        } catch (error) {
                                            window.xianHandleError(error, function(errorResult) {
                                                document.dispatchEvent(new CustomEvent('xianWalletSignMsgResponse', {
                                                    detail: errorResult
                                                }));
                                            });
                                        }
                                    });
                                    
                                    // Listen for transaction requests
                                    document.addEventListener('xianWalletSendTx', function(event) {
                                        window.xianDebug('xianWalletSendTx event received', event.detail);
                                        
                                        try {
                                            // Store transaction details for native dialog

                                            const txDetails = {
                                                contract: event.detail.contract,
                                                method: event.detail.method,
                                                kwargs: JSON.stringify(event.detail.kwargs),
                                                stampLimit: event.detail.stampLimit || 0
                                            };
                                            
                                            // Call native method to show transaction approval dialog
                                            XianWalletBridge.showTransactionApprovalDialog(
                                                JSON.stringify(txDetails)
                                            );
                                            
                                            // The response will be sent back via a callback from native code
                                            // See the implementation of showTransactionApprovalDialog in XianWebViewBridge
                                        } catch (error) {
                                            window.xianHandleError(error, function(errorResult) {
                                                document.dispatchEvent(new CustomEvent('xianWalletTxStatus', {
                                                    detail: errorResult
                                                }));
                                            });
                                        }
                                    });
                                    
                                    // Create a global handler for tx status that websites can use
                                    window.addEventListener('xianWalletTxStatus', function(event) {
                                        window.xianDebug('Transaction status received', event.detail);
                                        if (!event.detail.success) {
                                            console.error('Transaction failed:', event.detail.errors);
                                        }
                                    });
                                    
                                    // Dispatch ready event to notify dapp.js that the wallet is ready
                                    setTimeout(function() {
                                        document.dispatchEvent(new CustomEvent('xianReady'));
                                        window.xianDebug('xianReady event dispatched');
                                    }, 500);
                                })();
                                """
                                evaluateJavascript(jsCode, null)
                            }
                        }
                        // Set WebChromeClient to handle JS alerts, confirms, prompts
                        webChromeClient = object : WebChromeClient() {
                            override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: JsPromptResult?
                            ): Boolean {
                                if (result != null) {
                                    jsPromptRequest = JsPromptRequest(message ?: "", defaultValue ?: "", result)
                                    showJsPromptDialog = true
                                    return true // Indicate we're handling the prompt
                                }
                                return super.onJsPrompt(view, url, message, defaultValue, result)
                            }
                            // You can override onJsAlert and onJsConfirm here too if needed
                        }

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        // Add JavaScript interface
                        // Pass the listener implementation to the bridge constructor
                        val bridge = XianWebViewBridge(walletManager, networkService, authListener)
                        bridge.setWebView(this) // Pasar la referencia del WebView al bridge
                        addJavascriptInterface(bridge, "XianWalletBridge")
                        
                        loadUrl(currentUrl)
                    }.also {
                        webViewRef.value = it
                    }
                },
                update = { webView ->
                    // Update WebView if needed
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Custom JS Prompt Dialog
            if (showJsPromptDialog && jsPromptRequest != null) {
                var promptInput by remember { mutableStateOf(jsPromptRequest!!.defaultValue) }

                AlertDialog(
                    onDismissRequest = {
                        // Handle dismiss as cancel
                        jsPromptRequest?.result?.cancel()
                        showJsPromptDialog = false
                        jsPromptRequest = null
                    },
                    title = { Text(jsPromptRequest!!.message) },
                    text = {
                        TextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            label = { Text("Value") }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                jsPromptRequest?.result?.confirm(promptInput)
                                showJsPromptDialog = false
                                jsPromptRequest = null
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                jsPromptRequest?.result?.cancel()
                                showJsPromptDialog = false
                                jsPromptRequest = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- Password Pre-authentication Dialog ---
            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = {
                        // Treat dismiss as cancellation
                        authCallbacks?.second?.invoke() // Call onFailure
                        showPasswordDialog = false
                        authCallbacks = null
                        txDetailsForAuth = null
                        authErrorMessage = null
                    },
                    title = { Text("Authentication Required") },
                    text = {
                        Column {
                            Text("Enter your wallet password to proceed with the transaction.")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { /* Handle validation/auth on button click */ })
                            )
                            authErrorMessage?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch { // Use coroutine for walletManager call
                                    val unlocked = walletManager.unlockWallet(passwordInput)
                                    if (unlocked != null) {
                                        // Success
                                        authCallbacks?.first?.invoke(txDetailsForAuth ?: "") // Call onSuccess
                                        showPasswordDialog = false
                                        authCallbacks = null
                                        txDetailsForAuth = null
                                        authErrorMessage = null
                                    } else {
                                        // Failure
                                        authErrorMessage = "Invalid password"
                                    }
                                }
                            }
                        ) { Text("Unlock") }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                // Treat dismiss as cancellation
                                authCallbacks?.second?.invoke() // Call onFailure
                                showPasswordDialog = false
                                authCallbacks = null
                                txDetailsForAuth = null
                                authErrorMessage = null
                            }
                        ) { Text("Cancel") }
                    }
                )
            }

            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Helper data class to hold prompt request details
data class JsPromptRequest(
    val message: String,
    val defaultValue: String,
    val result: JsPromptResult
)