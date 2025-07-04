package net.xian.xianwalletapp.wallet

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.util.Log
import android.view.LayoutInflater
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.util.Locale
import android.view.View
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.crypto.XianCrypto
import net.xian.xianwalletapp.network.XianNetworkService
// kotlinx.coroutines.runBlocking is already imported below, remove duplicate if present
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Callback interface for requesting authentication from the UI layer
interface AuthRequestListener {
    fun requestAuth(txDetailsJson: String, onSuccess: (txDetailsJson: String) -> Unit, onFailure: () -> Unit)
}

/**
 * JavaScript interface for WebView to communicate with the Xian Wallet
 * This class acts as a bridge between web dApps and the Android wallet
 */
class XianWebViewBridge(
    private val walletManager: WalletManager,
    private val networkService: XianNetworkService,
    private val authRequestListener: AuthRequestListener // Add listener parameter
) {
    
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

        // Check if authentication is required before showing the dialog
        val needsAuth = !walletManager.getRequirePassword() || walletManager.getUnlockedPrivateKey() == null
        Log.d(TAG, "Needs authentication before showing dialog? $needsAuth")

        if (needsAuth) {
            // Request authentication from the UI layer via the callback
            authRequestListener.requestAuth(
                txDetailsJson = txDetailsJson,
                onSuccess = { authenticatedTxDetailsJson ->
                    // Auth successful, proceed to show the dialog on the UI thread
                    Log.d(TAG, "Authentication successful, proceeding to show dialog.")
                    proceedToShowDialog(authenticatedTxDetailsJson)
                },
                onFailure = {
                    // Auth failed or was cancelled
                    Log.w(TAG, "Authentication failed or cancelled by user.")
                    sendTransactionFailureResponse("Authentication required and was cancelled or failed")
                }
            )
        } else {
            // Already authenticated, proceed directly
            Log.d(TAG, "Already authenticated, proceeding directly to show dialog.")
            proceedToShowDialog(txDetailsJson)
        }
    }

    // Helper function to parse details and show the dialog (called after auth check)
    private fun proceedToShowDialog(txDetailsJson: String) {
         try {
            val txDetails = JSONObject(txDetailsJson)
            val contract = txDetails.getString("contract")
            val method = txDetails.getString("method")
            val kwargs = txDetails.getString("kwargs")
            val initialStampLimit = txDetails.optDouble("stampLimit", 0.0) // Read as Double

            // We need to run this on the UI thread since we're showing a dialog
            webView?.post {
                val context = webView?.context
                if (context != null) {
                    // Call the actual dialog display function (we'll rename/refactor this next)
                    displayTransactionDialog(context, contract, method, kwargs, initialStampLimit)
                } else {
                    // If context is null, we can't show a dialog, send failure response
                    Log.e(TAG, "Cannot show transaction dialog: WebView context is null.")
                    sendTransactionFailureResponse("Cannot show transaction dialog (Internal error)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction details after auth check", e)
            sendTransactionFailureResponse("Invalid transaction details: ${e.message}")
        }
    }
    
    // Renamed function, now assumes pre-authentication
    private fun displayTransactionDialog(context: Context, contract: String, method: String, kwargs: String, initialStampLimit: Double) { // Assumes pre-authentication
        val builder = AlertDialog.Builder(context)
        // Remove setTitle to avoid default title bar with sharp corners
        
        // Inflate a custom layout for the dialog
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_transaction_approval, null)
        
        // Set transaction details in the view
        view.findViewById<TextView>(R.id.tv_contract).text = contract
        view.findViewById<TextView>(R.id.tv_method).text = method
        view.findViewById<TextView>(R.id.tv_params).text = kwargs
        // Display initial stamp limit (usually 0 from dApp)
        val stampLimitTextView = view.findViewById<TextView>(R.id.tv_stamp_limit)
        stampLimitTextView.text = "${initialStampLimit.toInt()} Stamps (Provided by dApp)" // Show initial value

        // Find UI elements
        val estimateButton = view.findViewById<Button>(R.id.btn_estimate_fee) // Find button to remove/hide
        // TextView for displaying the estimated fee
        val estimatedFeeTextView = view.findViewById<TextView>(R.id.tv_estimated_fee)
        estimatedFeeTextView.visibility = View.VISIBLE // Make it visible by default
        estimatedFeeTextView.text = "Estimating fee..." // Initial text

        // Variable to store the successfully estimated stamp limit
        var estimatedStampLimit: Long? = null
        
        // Hide password input and estimate button as they are no longer needed
        view.findViewById<EditText>(R.id.et_password).visibility = View.GONE
        estimateButton.visibility = View.GONE // Hide the manual estimate button

        val errorMessageView = view.findViewById<TextView>(R.id.tv_error_message)
        errorMessageView.visibility = View.GONE // Start hidden

        
        builder.setView(view)

        // --- Automatic Fee Estimation Logic ---
        // Trigger estimation automatically when dialog is shown
        CoroutineScope(Dispatchers.Main).launch {
            val privateKey = walletManager.getUnlockedPrivateKey() // Should be available now due to pre-auth
            val publicKey = walletManager.getPublicKey() ?: ""
            var estimateErrorMsg: String? = null

            if (privateKey != null && publicKey.isNotEmpty()) {
                try {
                    val kwargsJson = JSONObject(kwargs)
                    Log.d(TAG, "Attempting automatic fee estimation...")
                    val estimatedStamps = networkService.estimateTransactionFee(
                        contract, method, kwargsJson, publicKey, privateKey
                    )
                    Log.d(TAG, "Raw estimation result: $estimatedStamps")

                    if (estimatedStamps != null && estimatedStamps > 0) {
                        estimatedStampLimit = estimatedStamps // Store the valid estimation
                        val stampRate = networkService.getStampRate() // Fetch rate again for display
                        Log.d(TAG, "Fetched stamp rate for display: $stampRate")
                        if (stampRate > 0) {
                            val xianEquivalent = estimatedStamps.toDouble() / stampRate
                            val formattedXian = String.format(Locale.US, "%.4f", xianEquivalent)
                            estimatedFeeTextView.text = "Estimated Fee: $estimatedStamps Stamps ($formattedXian Xian)"
                            Log.d(TAG, "Displaying estimated fee: ${estimatedFeeTextView.text}")
                        } else {
                            estimatedFeeTextView.text = "Estimated Fee: $estimatedStamps Stamps (Rate unavailable)"
                            Log.w(TAG, "Stamp rate unavailable for display.")
                        }
                    } else {
                        estimateErrorMsg = "Fee estimation failed (result: $estimatedStamps)"
                        estimatedStampLimit = null // Invalidate estimate
                        Log.e(TAG, estimateErrorMsg)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error during automatic fee estimation", e)
                    estimateErrorMsg = "Fee estimation error: ${e.message}"
                    estimatedStampLimit = null
                }
            } else {
                estimateErrorMsg = "Cannot estimate fee: Wallet key unavailable."
                estimatedStampLimit = null
                 Log.e(TAG, estimateErrorMsg)
            }

            // Display Estimation Result or Error
            if (estimateErrorMsg != null) {
                estimatedFeeTextView.text = estimateErrorMsg
                estimatedFeeTextView.setTextColor(context.getColor(android.R.color.holo_red_dark))
                // Keep initial stamp limit visible if estimation fails
                stampLimitTextView.visibility = View.VISIBLE
            } else if (estimatedStampLimit == null) {
                 // If estimation didn't produce a valid value but no specific error message
                 stampLimitTextView.visibility = View.VISIBLE // Show initial
                 estimatedFeeTextView.text = "Fee estimation unavailable." // Update text
                 estimatedFeeTextView.setTextColor(context.getColor(android.R.color.darker_gray))
            } else {
                 // Success - estimation text is already set
                 estimatedFeeTextView.setTextColor(context.getColor(R.color.text_secondary))
                 stampLimitTextView.visibility = View.GONE // Hide the initial "Provided by dApp" line
            }
        }
        // --- End Automatic Fee Estimation Logic ---
        
        // Create the dialog instance without default buttons
        val dialog = builder.create()
        
        // Make dialog background transparent to show only our rounded layout
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Show the dialog
        dialog.show()
        
        // Set up custom button click listeners
        val approveButton = view.findViewById<Button>(R.id.btn_approve)
        val rejectButton = view.findViewById<Button>(R.id.btn_reject)
        
        // Simplified error display function (now has access to approveButton)
        fun showError(message: String) {
             Log.e(TAG, "Error in transaction dialog: $message")
             errorMessageView.text = message
             errorMessageView.visibility = View.VISIBLE
             // Reactivar el botón para permitir otro intento
             approveButton.isEnabled = true
             approveButton.text = "Approve"
        }
        
        // Set up reject button
        rejectButton.setOnClickListener {
            // User rejected the transaction
            sendTransactionFailureResponse("User rejected the transaction")
            dialog.dismiss()
        }
        
        // Set up approve button click listener
        approveButton.setOnClickListener {
            // Deshabilitar el botón inmediatamente para mostrar feedback visual
            approveButton.isEnabled = false
            approveButton.text = "Processing..."
            
            // Password is no longer handled here, assume cached key is valid from pre-auth
            errorMessageView.visibility = View.GONE // Clear previous errors
            Log.d(TAG, "Approve clicked.")

            // 2. Determine the final stamp limit
            // Use the successfully estimated value if available, otherwise use the initial value from dApp.
            val finalStampLimit = estimatedStampLimit?.toDouble() ?: initialStampLimit
            Log.d(TAG, "Using final stamp limit for transaction: $finalStampLimit (Estimated: ${estimatedStampLimit != null})")

            // 3. Validate transaction parameters
            // 3. Validate transaction parameters (remains the same)
            try {
                if (contract.isBlank()) {
                    showError("Contract address cannot be empty")
                    return@setOnClickListener
                }
                if (method.isBlank()) {
                    showError("Method name cannot be empty")
                    return@setOnClickListener
                }

                // 4. Process the transaction
                // Pass the determined password (or null) and the final stamp limit.
                // Pass null for password (using cached key), pass the final determined stamp limit.
                processTransaction(contract, method, kwargs, null, finalStampLimit, dialog, errorMessageView)

            } catch (e: Exception) {
                // Catch errors during validation or the processTransaction call itself
                Log.e(TAG, "Validation or processing error on Approve", e)
                showError("Error: ${e.message ?: "Unknown error"}")
                // Optionally send failure back to JS
                sendTransactionFailureResponse("Approval error: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    // Password parameter is nullable (null means try using cached key)
    private fun processTransaction(contract: String, method: String, kwargs: String, password: String?,
                                  stampLimit: Double, dialog: AlertDialog, errorMessageView: TextView) { // Accept Double
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
                
                val txResult = runBlocking<net.xian.xianwalletapp.network.TransactionResult> {
                    try {
                        // Verify balance before sending the transaction
                        val publicKey = walletManager.getPublicKey() ?: ""
                        val balance = networkService.getTokenBalance("currency", publicKey)
                        
                        if (balance <= 0.0f) {
                            // Specific insufficient balance error
                            return@runBlocking net.xian.xianwalletapp.network.TransactionResult(
                                txHash = "",
                                success = false,
                                errors = "Insufficient XIAN balance to cover transaction fees"
                            )
                        }
                        
                        // Send the transaction if there is balance
                        // Convert Double back to Int for the network call (truncating decimals)
                        networkService.sendTransaction(contract, method, kwargsJson, privateKey, stampLimit.toInt())
                    } catch (e: Exception) {
                        // Catch any error during the transaction
                        Log.e(TAG, "Transaction failed", e)
                        net.xian.xianwalletapp.network.TransactionResult(
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
                    // Also send the error to the WebView so the dApp can handle it
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
            // Also send the error to the WebView
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
                val txResult = runBlocking<net.xian.xianwalletapp.network.TransactionResult> {
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