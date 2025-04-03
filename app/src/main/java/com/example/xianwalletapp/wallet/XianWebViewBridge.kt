package com.example.xianwalletapp.wallet

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import android.view.View
import com.example.xianwalletapp.R
import com.example.xianwalletapp.crypto.XianCrypto
import com.example.xianwalletapp.network.XianNetworkService
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * JavaScript interface for WebView to communicate with the Xian Wallet
 * This class acts as a bridge between web dApps and the Android wallet
 */
class XianWebViewBridge(private val walletManager: WalletManager, private val networkService: XianNetworkService) {
    
    private val TAG = "XianWebViewBridge"
    private var webView: WebView? = null
    
    // Set the WebView reference for callback purposes
    fun setWebView(webView: WebView) {
        this.webView = webView
    }
    
    /**
     * Get wallet information including address and lock status
     */
    @JavascriptInterface
    fun getWalletInfo(): String {
        Log.d(TAG, "getWalletInfo called from JavaScript")
        val publicKey = walletManager.getPublicKey() ?: ""
        
        return JSONObject().apply {
            put("address", publicKey)
            put("locked", false) // We assume the wallet is unlocked in the app context
            put("network", walletManager.getRpcUrl())
        }.toString()
    }
    
    /**
     * Log debug messages from JavaScript for troubleshooting
     */
    @JavascriptInterface
    fun logDebug(message: String) {
        Log.d(TAG, "JS Debug: $message")
    }

    /**
     * Check if password was required on app startup
     */
    @JavascriptInterface
    fun isPasswordRequiredOnStartup(): Boolean {
        Log.d(TAG, "isPasswordRequiredOnStartup called from JavaScript")
        return walletManager.getRequirePassword()
    }

    
    /**
     * Sign a message with the wallet's private key
     * Requires password authentication
     */
    
    /**
     * Show a native transaction approval dialog
     * This is called from JavaScript to show a native Android dialog
     */
    @JavascriptInterface
    fun showTransactionApprovalDialog(txDetailsJson: String) {
        Log.d(TAG, "showTransactionApprovalDialog called from JavaScript: $txDetailsJson")
        
        try {
            val txDetails = JSONObject(txDetailsJson)
            val contract = txDetails.getString("contract")
            val method = txDetails.getString("method")
            val kwargs = txDetails.getString("kwargs")
            val stampLimit = txDetails.optInt("stampLimit", 0)
            
            // We need to run this on the UI thread since we're showing a dialog
            webView?.post {
                val context = webView?.context
                if (context != null) {
                    showNativeTransactionDialog(context, contract, method, kwargs, stampLimit)
                } else {
                    // If context is null, we can't show a dialog, so we send a failure response
                    sendTransactionFailureResponse("Cannot show transaction dialog")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction details", e)
            sendTransactionFailureResponse("Invalid transaction details: ${e.message}")
        }
    }
    
    private fun showNativeTransactionDialog(context: Context, contract: String, method: String, kwargs: String, stampLimit: Int) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Transaction Request")
        
        // Inflate a custom layout for the dialog
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_transaction_approval, null)
        
        // Set transaction details in the view
        view.findViewById<TextView>(R.id.tv_contract).text = contract
        view.findViewById<TextView>(R.id.tv_method).text = method
        view.findViewById<TextView>(R.id.tv_params).text = kwargs
        view.findViewById<TextView>(R.id.tv_stamp_limit).text = stampLimit.toString()
        
        val passwordInput = view.findViewById<EditText>(R.id.et_password)
        val errorMessageView = view.findViewById<TextView>(R.id.tv_error_message)

        // Check if password input is needed
        val requirePasswordOnStartup = walletManager.getRequirePassword()
        val cachedKey = walletManager.getUnlockedPrivateKey()
        val needsPasswordInput = !requirePasswordOnStartup || cachedKey == null

        if (needsPasswordInput) {
            android.util.Log.d(TAG, "Transaction dialog requires password input.")
            passwordInput.visibility = View.VISIBLE
            errorMessageView.visibility = View.GONE // Start hidden
        } else {
            android.util.Log.d(TAG, "Transaction dialog using cached key, password input hidden.")
            passwordInput.visibility = View.GONE
            errorMessageView.visibility = View.GONE
        }

        // Function to display error messages (only if password field is visible)
        fun showError(message: String) {
             if (needsPasswordInput) {
                errorMessageView.text = message
                errorMessageView.visibility = View.VISIBLE
             } else {
                 // Log error if password field is hidden
                 Log.e(TAG, "Error in transaction dialog (password hidden): $message")
                 // Optionally send failure back to JS? Handled in processTransaction
             }
        }
        
        builder.setView(view)
        
        // Add buttons first, before creating the dialog
        builder.setPositiveButton("Approve") { _, _ ->
            // This will be overridden below to prevent automatic dismissal
        }
        
        builder.setNegativeButton("Reject") { _, _ ->
            // User rejected the transaction
            sendTransactionFailureResponse("User rejected the transaction")
        }
        
        // Create the dialog instance after setting the buttons
        val dialog = builder.create()
        
        // Show the dialog
        dialog.show()
        
        // Override the positive button click listener
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var passwordToUse: String? = null // Null if using cached key

            if (needsPasswordInput) {
                passwordToUse = passwordInput.text.toString()
                errorMessageView.visibility = View.GONE // Clear previous errors
                if (passwordToUse.isEmpty()) {
                    showError("Password is required")
                    return@setOnClickListener // Stop if password needed but empty
                }
            } else {
                 // Not requesting password input, will rely on cached key in processTransaction
                 Log.d(TAG, "Approve clicked, proceeding with cached key.")
            }

            // Validate the transaction parameters before processing
            try {
                if (contract.isBlank()) {
                    showError("Contract address cannot be empty")
                    return@setOnClickListener
                }
                if (method.isBlank()) {
                    showError("Method name cannot be empty")
                    return@setOnClickListener
                }

                // Process the transaction (pass null password if using cached key)
                processTransaction(contract, method, kwargs, passwordToUse, stampLimit, dialog, errorMessageView)

            } catch (e: Exception) {
                Log.e(TAG, "Validation or processing error", e)
                showError("Error: ${e.message ?: "Unknown error"}")
                // Send failure back to JS as well
                sendTransactionFailureResponse("Validation error: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    // Password parameter is nullable (null means try using cached key)
    private fun processTransaction(contract: String, method: String, kwargs: String, password: String?,
                                  stampLimit: Int, dialog: AlertDialog, errorMessageView: TextView) {
        val showErrorInDialog = password != null // Only show error in dialog if password was requested

        try {
            var privateKey: ByteArray? = null
            var errorMsg: String? = null

            if (password != null) {
                // If password was provided, try unlocking with it (this also caches)
                Log.d(TAG, "processTransaction attempting unlock with provided password.")
                privateKey = walletManager.unlockWallet(password)
                if (privateKey == null) {
                    errorMsg = "Invalid password"
                }
            } else {
                // If password was null, try getting the cached key
                Log.d(TAG, "processTransaction attempting to use cached key.")
                privateKey = walletManager.getUnlockedPrivateKey()
                if (privateKey == null) {
                    // This should ideally not happen if startup auth was required and successful,
                    // but handle it just in case.
                    errorMsg = "Failed to retrieve cached key. Please restart the app or try again."
                     // Force password next time? Or just fail? For now, just fail.
                }
            }

            if (privateKey == null) {
                Log.e(TAG, errorMsg ?: "Unknown error retrieving private key")
                if (showErrorInDialog && errorMsg != null) {
                    errorMessageView.text = errorMsg
                    errorMessageView.visibility = View.VISIBLE
                }
                // Send failure back to JS
                sendTransactionFailureResponse(errorMsg ?: "Failed to access private key")
            } else {
                // Parse the kwargs JSON
                val kwargsJson = JSONObject(kwargs)
                
                val txResult = runBlocking<com.example.xianwalletapp.network.TransactionResult> {
                    try {
                        // Verificar el saldo antes de enviar la transacción
                        val publicKey = walletManager.getPublicKey() ?: ""
                        val balance = networkService.getTokenBalance("currency", publicKey)
                        
                        if (balance <= 0.0f) {
                            // Error específico de saldo insuficiente
                            return@runBlocking com.example.xianwalletapp.network.TransactionResult(
                                txHash = "", 
                                success = false, 
                                errors = "Insufficient XIAN balance to cover transaction fees"
                            )
                        }
                        
                        // Enviar la transacción si hay saldo
                        networkService.sendTransaction(contract, method, kwargsJson, privateKey, stampLimit)
                    } catch (e: Exception) {
                        // Capturar cualquier error durante la transacción
                        Log.e(TAG, "Transaction failed", e)
                        com.example.xianwalletapp.network.TransactionResult(
                            txHash = "", 
                            success = false, 
                            errors = parseErrorMessage(e.message ?: "Unknown error")
                        )
                    }
                }
                
                if (txResult.success) {
                    // Send success response back to JavaScript
                    val resultJson = JSONObject().apply {
                        put("success", true)
                        put("txid", txResult.txHash)
                        put("status", "pending")
                    }.toString()
                    
                    // Dismiss the dialog only on success
                    dialog.dismiss()
                    sendJavaScriptResponse(resultJson)
                } else {
                    // Show error but don't dismiss dialog
                    val errorMsg = txResult.errors ?: "Unknown transaction error"
                    Log.e(TAG, "Transaction failed: $errorMsg")
                    // Always show error in dialog now
                    errorMessageView.text = getHumanReadableError(errorMsg)
                    errorMessageView.visibility = View.VISIBLE
                    // También enviar el error al WebView para que la dApp pueda manejarlo
                    sendTransactionFailureResponse(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing transaction", e)
            Log.e(TAG, "Error processing transaction", e) // Keep original log
            val errorMsg = "Transaction error: ${parseErrorMessage(e.message ?: "Unknown error")}"
            // Always show error in dialog now
            errorMessageView.text = errorMsg
            errorMessageView.visibility = View.VISIBLE
            // También enviar el error al WebView
            sendTransactionFailureResponse(e.message ?: "Unknown error")
        }
    }
    
    // Helper function to parse and make error messages more readable
    private fun parseErrorMessage(error: String): String {
        return when {
            error.contains("insufficient") || error.contains("balance") -> 
                "Insufficient balance to cover transaction fees"
            error.contains("gas") || error.contains("stamp") -> 
                "Not enough stamps/gas to execute transaction"
            error.contains("connection") || error.contains("connect") -> 
                "Connection to blockchain node failed"
            error.contains("time") && error.contains("out") -> 
                "Transaction timed out"
            else -> error
        }
    }
    
    // Convert technical error messages to user-friendly messages
    private fun getHumanReadableError(error: String): String {
        val friendlyMessage = parseErrorMessage(error)
        
        // Add extra information for specific common errors
        if (friendlyMessage.contains("Insufficient balance")) {
            return "$friendlyMessage\nPlease deposit XIAN tokens to your wallet to cover transaction fees."
        }
        
        return friendlyMessage
    }
    
    private fun sendTransactionFailureResponse(errorMessage: String) {
        val resultJson = JSONObject().apply {
            put("success", false)
            put("errors", errorMessage)
        }.toString()
        
        sendJavaScriptResponse(resultJson)
    }
    
    private fun sendJavaScriptResponse(resultJson: String) {
        // resultJson should be a valid JSON string like {"success":true,...}
        webView?.post {
            // Construct JS code to dispatch the event with the JSON string
            // embedded directly as a JavaScript object literal.
            val jsCode = """
                document.dispatchEvent(new CustomEvent('xianWalletTxStatus', {
                    detail: $resultJson // Embed JSON directly as an object literal
                }));
            """.trimIndent()

            Log.d(TAG, "Executing JS: $jsCode") // Log the JS being executed
            webView?.evaluateJavascript(jsCode, null)
        }
    }
    
    /**
     * Sign a message with the wallet's private key.
     * Uses cached key if available and appropriate, otherwise uses provided password.
     * Password parameter is nullable from JS. Null means "try cached key".
     */
    @JavascriptInterface
    fun signMessage(message: String, password: String?): String { // Password can be null from JS
        Log.d(TAG, "signMessage called from JavaScript. Password provided: ${password != null}")

        var privateKey: ByteArray? = null
        var errorMsg: String? = null
        val requirePasswordOnStartup = walletManager.getRequirePassword()

        if (requirePasswordOnStartup && password == null) {
            // Startup required password, JS didn't provide one, try cached key
            Log.d(TAG, "signMessage attempting to use cached key.")
            privateKey = walletManager.getUnlockedPrivateKey()
            if (privateKey == null) {
                errorMsg = "Failed to retrieve cached key for signing."
            }
        } else if (password != null) {
            // Password was provided (either because startup didn't require it, or JS sent it anyway)
             Log.d(TAG, "signMessage attempting unlock with provided password.")
            if (password.isEmpty()){
                 errorMsg = "Password cannot be empty."
            } else {
                privateKey = walletManager.unlockWallet(password) // Try unlocking (also caches)
                if (privateKey == null) {
                    errorMsg = "Invalid password for signing."
                }
            }
        } else {
             // Startup didn't require password, but JS didn't provide one either
             errorMsg = "Password is required for signing when not authenticated at startup."
        }


        return try {
            if (privateKey != null) {
                val signature = XianCrypto.getInstance().signMessage(message.toByteArray(), privateKey)
                JSONObject().apply {
                    put("success", true)
                    put("signature", signature)
                    put("message", message)
                }.toString()
            } else {
                 Log.e(TAG, "signMessage failed: $errorMsg")
                 JSONObject().apply {
                    put("success", false)
                    put("error", errorMsg ?: "Failed to access private key for signing")
                }.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing message", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error during signing")
            }.toString()
        }
    }

    /**
     * Send a transaction to the Xian blockchain (Older method).
     * Uses cached key if available and appropriate, otherwise uses provided password.
     * Password parameter is nullable. Null means "try cached key".
     */
    @JavascriptInterface
    fun sendTransaction(contract: String, method: String, kwargs: String, password: String?, stampLimit: Int = 0): String { // Password can be null
        Log.d(TAG, "sendTransaction (older method) called. Password provided: ${password != null}")

        var privateKey: ByteArray? = null
        var errorMsg: String? = null
        val requirePasswordOnStartup = walletManager.getRequirePassword()

         if (requirePasswordOnStartup && password == null) {
            // Startup required password, JS didn't provide one, try cached key
            Log.d(TAG, "sendTransaction (older) attempting to use cached key.")
            privateKey = walletManager.getUnlockedPrivateKey()
            if (privateKey == null) {
                errorMsg = "Failed to retrieve cached key for transaction."
            }
        } else if (password != null) {
            // Password was provided
             Log.d(TAG, "sendTransaction (older) attempting unlock with provided password.")
             if (password.isEmpty()){
                 errorMsg = "Password cannot be empty."
             } else {
                privateKey = walletManager.unlockWallet(password) // Try unlocking (also caches)
                if (privateKey == null) {
                    errorMsg = "Invalid password for transaction."
                }
             }
        } else {
             // Startup didn't require password, but JS didn't provide one either
             errorMsg = "Password is required for transaction when not authenticated at startup."
        }


        return try {
             if (privateKey != null) {
                val kwargsJson = JSONObject(kwargs)
                val txResult = runBlocking<com.example.xianwalletapp.network.TransactionResult> {
                    networkService.sendTransaction(contract, method, kwargsJson, privateKey, stampLimit)
                }
                 // Return full result including potential errors from sendTransaction
                 JSONObject().apply {
                     put("success", txResult.success)
                     put("txid", txResult.txHash)
                     put("status", if (txResult.success) "pending" else "failed")
                     if (!txResult.success) {
                         put("errors", txResult.errors ?: "Unknown transaction error")
                     }
                 }.toString()
             } else {
                 Log.e(TAG, "sendTransaction (older) failed: $errorMsg")
                 JSONObject().apply {
                    put("success", false)
                    put("errors", errorMsg ?: "Failed to access private key for transaction")
                }.toString()
             }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending transaction (older)", e)
            JSONObject().apply {
                put("success", false)
                put("errors", e.message ?: "Unknown error during transaction")
            }.toString()
        }
    }
    
    /**
     * Get transaction results by hash
     */
    @JavascriptInterface
    fun getTransactionResults(txHash: String): String {
        Log.d(TAG, "getTransactionResults called from JavaScript: txHash=$txHash")
        
        return try {
            val txResult = runBlocking<String> {
                networkService.getTransactionResults(txHash)
            }
            
            JSONObject().apply {
                put("success", true)
                put("result", txResult)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transaction results", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }
    
    /**
     * Get the balance of a token for the current wallet
     */
    @JavascriptInterface
    fun getBalance(contract: String): String {
        Log.d(TAG, "getBalance called from JavaScript: contract=$contract")
        
        return try {
            val publicKey = walletManager.getPublicKey() ?: ""
            
            // Use runBlocking to call the suspend function from a non-suspend context
            val balance = runBlocking {
                networkService.getTokenBalance(contract, publicKey)
            }
            
            JSONObject().apply {
                put("success", true)
                put("balance", balance.toString())
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting balance", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }
}