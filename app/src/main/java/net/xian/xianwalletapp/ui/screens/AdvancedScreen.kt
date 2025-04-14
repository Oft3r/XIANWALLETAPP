package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavController
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.ui.theme.XianBlue // Import XianBlue
// CryptoUtils import might be needed later for token creation
// import net.xian.xianwalletapp.utils.CryptoUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
// XianCrypto import removed as signing is handled by NetworkService
import java.math.BigDecimal
import org.json.JSONObject // Needed for kwargs

// Python smart contract template for a standard token
private const val TOKEN_CONTRACT_TEMPLATE = """
balances = Hash(default_value=0)
metadata = Hash()
TransferEvent = LogEvent(event="Transfer", params={"from":{'type':str, 'idx':True}, "to": {'type':str, 'idx':True}, "amount": {'type':(int, float, decimal)}})
ApproveEvent = LogEvent(event="Approve", params={"from":{'type':str, 'idx':True}, "to": {'type':str, 'idx':True}, "amount": {'type':(int, float, decimal)}})


@construct
def seed():
    balances[ctx.caller] = TOKEN_SUPPLY

    metadata['token_name'] = "TOKEN_NAME"
    metadata['token_symbol'] = "TOKEN_SYMBOL"
    metadata['token_logo_url'] = 'TOKEN_LOGO_URL'
    metadata['token_website'] = 'TOKEN_WEBSITE'
    metadata['total_supply'] = TOKEN_SUPPLY
    metadata['operator'] = ctx.caller


@export
def change_metadata(key: str, value: Any):
    assert ctx.caller == metadata['operator'], 'Only operator can set metadata!'
    metadata[key] = value


@export
def balance_of(address: str):
    return balances[address]


@export
def transfer(amount: float, to: str):
    assert amount > 0, 'Cannot send negative balances!'
    assert balances[ctx.caller] >= amount, 'Not enough coins to send!'

    balances[ctx.caller] -= amount
    balances[to] += amount
    TransferEvent({"from": ctx.caller, "to": to, "amount": amount})


@export
def approve(amount: float, to: str):
    assert amount > 0, 'Cannot send negative balances!'

    balances[ctx.caller, to] = amount
    ApproveEvent({"from": ctx.caller, "to": to, "amount": amount})


@export
def transfer_from(amount: float, to: str, main_account: str):
    assert amount > 0, 'Cannot send negative balances!'
    assert balances[main_account, ctx.caller] >= amount, f'Not enough coins approved to send! You have {balances[main_account, ctx.caller]} and are trying to spend {amount}'
    assert balances[main_account] >= amount, 'Not enough coins to send!'

    balances[main_account, ctx.caller] -= amount
    balances[main_account] -= amount
    balances[to] += amount
    TransferEvent({"from": main_account, "to": to, "amount": amount})
"""

/**
 * Screen for advanced functionalities like token creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService
) {
    val coroutineScope = rememberCoroutineScope() // Scope for launching coroutines

    // --- State for Create Token ---
    var tokenName by remember { mutableStateOf("") }
    var tokenSymbol by remember { mutableStateOf("") }
    var tokenSupply by remember { mutableStateOf("") }
    var tokenLogoUrl by remember { mutableStateOf("") }
    var tokenWebsiteUrl by remember { mutableStateOf("") }
    var tokenContractName by remember { mutableStateOf("") }

    // --- State for Advanced Transaction ---
    var advContractName by remember { mutableStateOf("") }
    var advFunctionList by remember { mutableStateOf<List<XianNetworkService.ContractMethod>>(emptyList()) } // Store full method info
    var advSelectedFunction by remember { mutableStateOf<XianNetworkService.ContractMethod?>(null) }
    var advArguments by remember { mutableStateOf<Map<String, MutableState<String>>>(emptyMap()) } // Map arg name to its input state
    var advEstimatedFee by remember { mutableStateOf<Long?>(null) }
    var advIsLoadingFunctions by remember { mutableStateOf(false) }
    var advIsEstimatingFee by remember { mutableStateOf(false) }
    var advErrorMessage by remember { mutableStateOf<String?>(null) }
    var advSuccessMessage by remember { mutableStateOf<String?>(null) }
    var advIsSubmitting by remember { mutableStateOf(false) }
    var advShowPasswordDialog by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) } // For debouncing network calls
    var feeEstimationJob by remember { mutableStateOf<Job?>(null) } // Separate job for fee estimation debounce
    // --- General State ---
    var errorMessage by remember { mutableStateOf<String?>(null) } // General error for password/unlock issues
    var successMessage by remember { mutableStateOf<String?>(null) } // General success message
    var isLoading by remember { mutableStateOf(false) } // General loading state (e.g., for unlock)
    var showPasswordDialog by remember { mutableStateOf(false) } // Dialog for Create Token
    // TODO: Consider separate password dialog state for Adv Tx if needed


    // Auto-update Create Token contract name
    LaunchedEffect(tokenName) {
        tokenContractName = "con_" + tokenName.replace(" ", "_").lowercase()
    }

    // Debounced function fetching for Advanced Transaction contract name
    LaunchedEffect(advContractName) {
        debounceJob?.cancel() // Cancel previous debounce job if any
        if (advContractName.isNotBlank()) {
            debounceJob = coroutineScope.launch {
                delay(800) // Wait 800ms after last input
                advIsLoadingFunctions = true
                advErrorMessage = null
                advFunctionList = emptyList()
                advSelectedFunction = null
                advArguments = emptyMap()
                advEstimatedFee = null
                try {
                    // Assuming networkService.getContractMethods returns List<ContractMethod>?
                    // data class ContractMethod(val name: String, val arguments: List<Argument>)
                    // data class Argument(val name: String, val type: String)
                    val methods = networkService.getContractMethods(advContractName)
                    if (methods != null) {
                        advFunctionList = methods
                        if (methods.isEmpty()) {
                             advErrorMessage = "Contract found, but it has no exported methods."
                        }
                    } else {
                        advErrorMessage = "Contract '$advContractName' not found or error fetching methods."
                    }
                } catch (e: Exception) {
                    println("Error fetching contract methods: ${e.message}")
                    advErrorMessage = "Error fetching functions: ${e.message}"
                } finally {
                    advIsLoadingFunctions = false
                }
            }
        } else {
             advFunctionList = emptyList()
             advSelectedFunction = null
             advArguments = emptyMap()
             advEstimatedFee = null
             advErrorMessage = null // Clear error if contract name is cleared
        }
    }

    // Update arguments when function selection changes
    LaunchedEffect(advSelectedFunction) {
        val selectedFunc = advSelectedFunction
        if (selectedFunc != null) {
            // Create mutable state for each argument of the selected function
            advArguments = selectedFunc.arguments.associate { arg ->
                arg.name to mutableStateOf("")
            }
            advEstimatedFee = null // Reset fee estimation when function changes
            // TODO: Trigger debounced fee estimation here if desired
        } else {
            advArguments = emptyMap() // Clear arguments if no function is selected
            advEstimatedFee = null
        }
        advErrorMessage = null // Clear previous errors
        advSuccessMessage = null
    }

    // --- Fee Estimation Logic ---
    suspend fun estimateAdvancedTransactionFee() {
        val contract = advContractName
        val function = advSelectedFunction
        val argsMap = advArguments
        val pubKey = walletManager.getPublicKey() // Needed for potential signing during estimation

        // Ensure we have the necessary info and a key (even if cached/unlocked)
        if (contract.isBlank() || function == null || pubKey == null) {
            advEstimatedFee = null
            // Don't show error here, just clear fee if inputs incomplete
            return
        }

        // Attempt to get a private key (needed for signing the estimation tx)
        // We don't prompt for password here, estimation fails if locked.
        val privKey = walletManager.getUnlockedPrivateKey()
        if (privKey == null) {
             advEstimatedFee = null // Clear fee if wallet is locked
             // Optionally set a message: advErrorMessage = "Unlock wallet to estimate fee"
             println("Cannot estimate fee: Wallet locked.")
             advIsEstimatingFee = false // Ensure loading stops
             return
        }


        advIsEstimatingFee = true
        advErrorMessage = null // Clear previous errors

        try {
            // 1. Parse/Validate Arguments for Estimation
            val kwargs = JSONObject()
            var parsingError: String? = null
            function.arguments.forEach { argDef ->
                val valueString = argsMap[argDef.name]?.value ?: ""
                if (valueString.isBlank()) {
                    parsingError = "Argument '${argDef.name}' cannot be empty for fee estimation."
                    return@forEach // Exit inner loop
                }
                try {
                     val parsedValue: Any = when (argDef.type.lowercase()) {
                         "int" -> valueString.toLongOrNull() ?: throw IllegalArgumentException("Invalid integer format for '${argDef.name}'")
                         "float", "decimal" -> valueString.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number format for '${argDef.name}'")
                         "bool" -> when (valueString.lowercase()) {
                             "true" -> true
                             "false" -> false
                             else -> throw IllegalArgumentException("Invalid boolean format for '${argDef.name}' (use true/false)")
                         }
                         "str" -> valueString
                         "dict", "list", "any" -> try { JSONObject(valueString) } catch (e: org.json.JSONException) { try { org.json.JSONArray(valueString) } catch (e2: org.json.JSONException) { if (argDef.type.lowercase() == "any") valueString else throw IllegalArgumentException("Invalid JSON format for '${argDef.name}'") } }
                         else -> valueString
                     }
                     kwargs.put(argDef.name, parsedValue)
                } catch (e: Exception) {
                    parsingError = "Argument Error for '${argDef.name}': ${e.message}"
                    return@forEach // Exit inner loop
                }
            }

            if (parsingError != null) {
                throw IllegalArgumentException(parsingError)
            }

            // 2. Call Network Service for Estimation
            // Assuming a function like this exists:
             val estimatedStamps = networkService.estimateTransactionFee(
                 contract = contract,
                 method = function.name,
                 kwargs = kwargs,
                 publicKey = pubKey,
                 privateKey = privKey // Pass private key for signing the estimation tx
             )

            if (estimatedStamps == null || estimatedStamps <= 0) {
                 // Handle cases where estimation returns null or non-positive value
                 // It might mean the transaction would fail validation on the node.
                 advEstimatedFee = null
                 // Consider setting an error message based on node feedback if available
                 advErrorMessage = "Fee estimation failed. Transaction might be invalid."
                 println("Fee estimation returned invalid value: $estimatedStamps")
            } else {
                advEstimatedFee = estimatedStamps
                println("Estimated fee: $estimatedStamps stamps")
            }

        } catch (e: Exception) {
            println("Error during fee estimation: ${e.message}")
            advErrorMessage = "Fee Estimation Error: ${e.message}"
            advEstimatedFee = null
        } finally {
            advIsEstimatingFee = false
        }
    }

    // Function to trigger debounced estimation
    fun triggerDebouncedFeeEstimation() {
        feeEstimationJob?.cancel()
        // Only estimate if contract and function are selected
        if (advContractName.isNotBlank() && advSelectedFunction != null) {
             // Check if all arguments for the selected function have non-blank values
            val allArgsFilled = advSelectedFunction!!.arguments.all { argDef ->
                advArguments[argDef.name]?.value?.isNotBlank() == true
            }
            if (allArgsFilled) {
                feeEstimationJob = coroutineScope.launch {
                    delay(900) // Slightly longer delay for fee estimation
                    estimateAdvancedTransactionFee()
                }
            } else {
                 advEstimatedFee = null // Clear fee if args are not filled
            }
        } else {
             advEstimatedFee = null // Clear fee if contract/function not selected
        }
    }

     // Trigger estimation when function selection changes (if args are already filled somehow, unlikely)
    LaunchedEffect(advSelectedFunction) {
        val selectedFunc = advSelectedFunction
        if (selectedFunc != null) {
            // Recreate state when function changes. Use mutableStateOf directly here, not remember.
             advArguments = selectedFunc.arguments.associate { arg ->
                 arg.name to mutableStateOf("") // Removed remember
             }
            advEstimatedFee = null
            triggerDebouncedFeeEstimation() // Trigger estimation after args are reset/created
        } else {
            advArguments = emptyMap()
            advEstimatedFee = null
        }
        advErrorMessage = null
        advSuccessMessage = null
    }

    // This LaunchedEffect observes the *values* of the arguments.
    // It creates a key that changes whenever any argument value changes.
    val argumentValuesKey = advArguments.values.joinToString { it.value }
    LaunchedEffect(argumentValuesKey, advSelectedFunction) {
         // This will run when the selected function changes OR any argument value changes.
         // The LaunchedEffect(advSelectedFunction) above handles the function change case
         // primarily for resetting the argument structure. This one handles value changes.
         if (advSelectedFunction != null) { // Only trigger if a function is selected
            triggerDebouncedFeeEstimation()
         }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // --- General Messages ---
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (successMessage != null) {
                Text(
                    text = successMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // --- Informational Text ---
            Text(
                text = "Create new tokens or interact directly with smart contracts.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp) // Add some space below the text
            )
            Spacer(modifier = Modifier.height(8.dp)) // Add a small spacer before the first card

            // --- Create Token Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(colors = listOf(Color.Yellow, XianBlue))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Create Token", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Create a new fungible token on the blockchain.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))

                    OutlinedTextField(
                        value = tokenName,
                        onValueChange = { tokenName = it; errorMessage = null; successMessage = null },
                        label = { Text("Token Name (*)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tokenSymbol,
                        onValueChange = { tokenSymbol = it; errorMessage = null; successMessage = null },
                        label = { Text("Token Symbol (*)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tokenSupply,
                        onValueChange = { tokenSupply = it.filter { char -> char.isDigit() }; errorMessage = null; successMessage = null },
                        label = { Text("Token Supply (*)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = tokenLogoUrl,
                        onValueChange = { tokenLogoUrl = it },
                        label = { Text("Token Logo URL") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tokenWebsiteUrl,
                        onValueChange = { tokenWebsiteUrl = it },
                        label = { Text("Token Website URL") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tokenContractName,
                        onValueChange = { /* Read Only */ },
                        label = { Text("Token Contract Name (*)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        readOnly = true,
                        enabled = false
                    )
                    Text("(*) Required fields", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))

                    Spacer(modifier = Modifier.height(16.dp))

                    // Create Token Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                // --- Input Validation ---
                                errorMessage = null
                                successMessage = null
                                if (tokenName.isBlank() || tokenSymbol.isBlank() || tokenSupply.isBlank() || tokenContractName.isBlank()) {
                                    errorMessage = "Please fill out all required (*) fields for Create Token."
                                    return@Button
                                }
                                val supplyAmount = tokenSupply.toBigDecimalOrNull()
                                if (supplyAmount == null || supplyAmount <= BigDecimal.ZERO) {
                                    errorMessage = "Token supply must be a positive number."
                                    return@Button
                                }
                                val publicKey = walletManager.getPublicKey()
                                if (publicKey == null) {
                                     errorMessage = "Public key not found. Cannot proceed."
                                     return@Button
                                }
                                showPasswordDialog = true // Show dialog for confirmation/password
                            },
                            enabled = !isLoading
                        ) {
                            if (isLoading) { // Use general isLoading for now
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Create Token")
                            }
                        }
                        Button(
                            onClick = { navController.popBackStack() }, // Consider if Cancel should be here or global
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Cancel Create") // Clarify button action
                        }
                    }
                }
            } // End of Create Token Card

            Spacer(modifier = Modifier.height(16.dp)) // Spacer between cards

            // --- Advanced Transaction Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(colors = listOf(Color.Yellow, XianBlue))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Advanced Transaction", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Directly interact with a smart contract.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))

                    // Contract Name Input
                    OutlinedTextField(
                        value = advContractName,
                        onValueChange = {
                            advContractName = it
                            advSelectedFunction = null // Reset function when contract changes
                            advFunctionList = emptyList()
                            // TODO: Trigger function fetching logic here (debounced)
                            advErrorMessage = null
                            advSuccessMessage = null
                        },
                        label = { Text("Contract Name") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )

                    // Function Dropdown
                    var functionDropdownExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = functionDropdownExpanded,
                        onExpandedChange = { functionDropdownExpanded = !functionDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = advSelectedFunction?.name ?: "",
                            onValueChange = { }, // Input field is read-only in dropdown mode
                            label = { Text("Function") },
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = functionDropdownExpanded)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), // Important for anchoring the dropdown
                            enabled = advFunctionList.isNotEmpty() || advIsLoadingFunctions // Enable if loading or list has items
                        )

                        ExposedDropdownMenu(
                            expanded = functionDropdownExpanded && (advFunctionList.isNotEmpty() || advIsLoadingFunctions || advErrorMessage != null),
                            onDismissRequest = { functionDropdownExpanded = false }
                        ) {
                            if (advIsLoadingFunctions) {
                                DropdownMenuItem(
                                    text = { Text("Loading functions...") },
                                    onClick = { },
                                    enabled = false
                                )
                            } else if (advFunctionList.isEmpty() && advContractName.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text(advErrorMessage ?: "No functions found") },
                                    onClick = { },
                                    enabled = false
                                )
                            } else {
                                advFunctionList.forEach { function ->
                                    DropdownMenuItem(
                                        text = { Text(function.name) },
                                        onClick = {
                                            advSelectedFunction = function
                                            functionDropdownExpanded = false
                                            // Argument state update is handled by LaunchedEffect(advSelectedFunction)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // Loading indicator specifically for functions (if needed separate from dropdown)
                    // if (advIsLoadingFunctions) {
                    //     CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally).padding(top = 4.dp))
                    // }

                    // Argument Inputs (Dynamically Generated)
                    if (advSelectedFunction != null && advArguments.isNotEmpty()) {
                        advSelectedFunction!!.arguments.forEach { argDefinition ->
                            val argumentState = advArguments[argDefinition.name]
                            if (argumentState != null) {
                                OutlinedTextField(
                                    value = argumentState.value,
                                    onValueChange = {
                                        argumentState.value = it
                                        // TODO: Trigger debounced fee estimation here
                                        advErrorMessage = null // Clear errors on input change
                                    },
                                    label = { Text("${argDefinition.name} (${argDefinition.type})") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    // Adjust keyboard type based on argDefinition.type if possible
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = when (argDefinition.type.lowercase()) {
                                            "int", "float", "decimal" -> KeyboardType.Decimal // Corrected type
                                            else -> KeyboardType.Text
                                        }
                                    ),
                                    singleLine = argDefinition.type.lowercase() !in listOf("dict", "list", "any") // Allow multiline for complex types
                                )
                            }
                        }
                    } else if (advSelectedFunction != null && advSelectedFunction!!.arguments.isEmpty()) {
                         Text("Selected function takes no arguments.", modifier = Modifier.padding(vertical = 8.dp))
                    } else if (advSelectedFunction == null && advContractName.isNotBlank() && !advIsLoadingFunctions) {
                         Text("Select a function to see arguments.", modifier = Modifier.padding(vertical = 8.dp))
                    }

                    // --- Error/Success Messages for Adv Tx ---
                     if (advErrorMessage != null) {
                        Text(
                            text = advErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (advSuccessMessage != null) {
                        Text(
                            text = advSuccessMessage!!,
                            color = MaterialTheme.colorScheme.primary, // Or a success color
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }


                    // TODO: Add Fee Estimation Display
                    if (advIsEstimatingFee) {
                         Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(" Estimating fee...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                         }
                    } else if (advEstimatedFee != null) {
                        // TODO: Fetch stamp rate and display Xian equivalent
                        Text("Estimated Fee: $advEstimatedFee Stamps", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    }


                    Spacer(modifier = Modifier.height(16.dp))

                    // Advanced Transaction Action Button
                     Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center // Center the single button for now
                    ) {
                        Button(
                            onClick = {
                                // --- Input Validation ---
                                advErrorMessage = null
                                advSuccessMessage = null
                                if (advContractName.isBlank()) {
                                    advErrorMessage = "Contract Name cannot be empty."
                                    return@Button
                                }
                                if (advSelectedFunction == null) {
                                    advErrorMessage = "Please select a function."
                                    return@Button
                                }
                                // Basic argument validation (check if all required fields are filled)
                                var missingArg: String? = null
                                advSelectedFunction!!.arguments.forEach { argDef ->
                                    if (advArguments[argDef.name]?.value?.isBlank() == true) {
                                        missingArg = argDef.name
                                        return@forEach // Exit forEach early
                                    }
                                }
                                if (missingArg != null) {
                                     advErrorMessage = "Argument '$missingArg' cannot be empty."
                                     return@Button
                                }
                                // TODO: Add more specific argument type validation if needed before showing dialog

                                // --- Show Dialog ---
                                advShowPasswordDialog = true
                            },
                            enabled = !advIsSubmitting // Use specific loading state
                        ) {
                             if (advIsSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Send Advanced Tx")
                            }
                        }
                    }
                }
            } // End of Advanced Transaction Card

        } // End of main Column

        // --- Password/Confirmation Dialog for Create Token ---
        if (showPasswordDialog) {
            // Determine if password field is needed based on cached key status *when dialog is shown*
            val needsPasswordInput = walletManager.getUnlockedPrivateKey() == null

            PasswordPromptDialog(
                showPasswordField = needsPasswordInput,
                onDismiss = { showPasswordDialog = false },
                onConfirm = { password -> // password is String? (null if field wasn't shown)
                    showPasswordDialog = false
                    isLoading = true // Use general isLoading
                    coroutineScope.launch {
                        var keyToUse: ByteArray? = null
                        try {
                            // --- 1. Get Private Key (Unlock if necessary) ---
                            val currentPublicKey = walletManager.getPublicKey() // Get public key once
                            if (currentPublicKey == null) {
                                throw IllegalStateException("Public key not found.")
                            }

                            if (needsPasswordInput) {
                                if (password == null || password.isEmpty()) {
                                     throw IllegalStateException("Password is required.")
                                }
                                keyToUse = walletManager.unlockWallet(password)
                                if (keyToUse == null) {
                                    throw IllegalStateException("Invalid password.")
                                }
                                println("Wallet unlocked successfully for transaction.")
                            } else {
                                keyToUse = walletManager.getUnlockedPrivateKey()
                                if (keyToUse == null) {
                                     throw IllegalStateException("Wallet became locked. Please try again.")
                                }
                                println("Using cached key for transaction.")
                            }

                            // --- 2. Estimate Fee ---
                            val finalContractCodeForEstimation = TOKEN_CONTRACT_TEMPLATE
                            val estimatedStamps = networkService.estimateContractSubmissionFee(
                                contractName = tokenContractName,
                                contractCode = finalContractCodeForEstimation,
                                publicKey = currentPublicKey,
                                privateKey = keyToUse
                            )

                            if (estimatedStamps <= 0) {
                                throw RuntimeException("Failed to estimate transaction fee (returned $estimatedStamps).")
                            }
                            println("Estimated stamps: $estimatedStamps")

                            // --- 3. Prepare Final Contract Code ---
                            val supplyAmount = tokenSupply.toBigDecimal() // Already validated
                            val finalContractCode = TOKEN_CONTRACT_TEMPLATE
                                .replace("\"TOKEN_NAME\"", "\"${tokenName.replace("\"", "\\\"")}\"")
                                .replace("\"TOKEN_SYMBOL\"", "\"${tokenSymbol.replace("\"", "\\\"")}\"")
                                .replace("TOKEN_SUPPLY", supplyAmount.toPlainString())
                                .replace("'TOKEN_LOGO_URL'", "'${tokenLogoUrl.replace("'", "\\'")}'")
                                .replace("'TOKEN_WEBSITE'", "'${tokenWebsiteUrl.replace("'", "\\'")}'")

                            // --- 4. Prepare Kwargs ---
                            val kwargs = JSONObject().apply {
                                put("name", tokenContractName)
                                put("code", finalContractCode)
                            }

                            // --- 5. Send Transaction ---
                            val txResult = networkService.sendTransaction(
                                contract = "submission",
                                method = "submit_contract",
                                kwargs = kwargs,
                                privateKey = keyToUse,
                                stampLimit = estimatedStamps
                            )

                            // --- 6. Handle Result ---
                            if (txResult.success) {
                                successMessage = "Token created successfully! Tx Hash: ${txResult.txHash}"
                                walletManager.addToken(tokenContractName)
                                // Clear fields
                                tokenName = ""
                                tokenSymbol = ""
                                tokenSupply = ""
                                tokenLogoUrl = ""
                                tokenWebsiteUrl = ""
                            } else {
                                errorMessage = "Token creation failed: ${txResult.errors ?: "Unknown error"}"
                            }

                        } catch (e: Exception) {
                            println("Error during token creation process: ${e.message}")
                            errorMessage = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }

         // --- Password/Confirmation Dialog for Advanced Transaction ---
         if (advShowPasswordDialog) {
             val needsPasswordInputAdv = walletManager.getUnlockedPrivateKey() == null

             PasswordPromptDialog(
                 showPasswordField = needsPasswordInputAdv,
                 onDismiss = { advShowPasswordDialog = false },
                 onConfirm = { password ->
                     advShowPasswordDialog = false
                     advIsSubmitting = true
                     advErrorMessage = null
                     advSuccessMessage = null
                     coroutineScope.launch {
                         var keyToUse: ByteArray? = null
                         try {
                             // --- 1. Get Private Key ---
                             val currentPublicKey = walletManager.getPublicKey() ?: throw IllegalStateException("Public key not found.")

                             if (needsPasswordInputAdv) {
                                 if (password.isNullOrEmpty()) throw IllegalStateException("Password is required.")
                                 keyToUse = walletManager.unlockWallet(password) ?: throw IllegalStateException("Invalid password.")
                                 println("Wallet unlocked successfully for Adv transaction.")
                             } else {
                                 keyToUse = walletManager.getUnlockedPrivateKey() ?: throw IllegalStateException("Wallet became locked. Please try again.")
                                 println("Using cached key for Adv transaction.")
                             }

                             // Ensure keyToUse is non-null before proceeding (compiler needs this guarantee)
                             val finalPrivateKey = keyToUse ?: throw IllegalStateException("Private key acquisition failed unexpectedly.")


                             // --- 2. Parse and Validate Arguments ---
                             val kwargs = JSONObject()
                             try {
                                 advSelectedFunction!!.arguments.forEach { argDef ->
                                     val valueString = advArguments[argDef.name]?.value ?: ""
                                     val parsedValue: Any = when (argDef.type.lowercase()) {
                                         "int" -> valueString.toLongOrNull() ?: throw IllegalArgumentException("Invalid integer format for '${argDef.name}'")
                                         "float", "decimal" -> valueString.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number format for '${argDef.name}'") // Network service might handle BigDecimal conversion if needed
                                         "bool" -> when (valueString.lowercase()) {
                                             "true" -> true
                                             "false" -> false
                                             else -> throw IllegalArgumentException("Invalid boolean format for '${argDef.name}' (use true/false)")
                                         }
                                         "str" -> valueString
                                         "dict", "list", "any" -> try {
                                             // Try parsing as JSON Object first (most common for dict/any)
                                             JSONObject(valueString)
                                         } catch (e: org.json.JSONException) {
                                             try {
                                                 // Then try as JSON Array (for list/any)
                                                 org.json.JSONArray(valueString)
                                             } catch (e2: org.json.JSONException) {
                                                 // If it's 'any' and not valid JSON, treat as string
                                                 if (argDef.type.lowercase() == "any") valueString
                                                 else throw IllegalArgumentException("Invalid JSON format for '${argDef.name}' (Type: ${argDef.type})")
                                             }
                                         }
                                         else -> valueString // Default to string for unknown types
                                     }
                                     kwargs.put(argDef.name, parsedValue)
                                 }
                             } catch (e: Exception) {
                                 // Catch parsing/validation errors specifically
                                 throw IllegalArgumentException("Argument Error: ${e.message}")
                             }


                             // --- 3. Estimate Fee (Re-estimate just before sending for accuracy) ---
                             // TODO: Implement a dedicated estimation call in NetworkService if possible
                             // For now, using a high default or the last estimated value if available
                             val stampLimit = advEstimatedFee ?: 50000L // Use estimated or a high default
                             println("Using stamp limit: $stampLimit for ${advContractName}.${advSelectedFunction!!.name}")
                             // Ideally, re-estimate here:
                             // advIsEstimatingFee = true // Show indicator
                             // val estimatedStamps = networkService.estimateTransaction(...)
                             // advIsEstimatingFee = false
                             // if (estimatedStamps <= 0) throw RuntimeException("Failed to estimate fee.")
                             // stampLimit = estimatedStamps


                            // --- 4. Send Transaction ---
                            // Convert Long stampLimit to Int safely
                            val stampLimitInt = if (stampLimit > Int.MAX_VALUE) {
                                println("Warning: Estimated stamp limit ($stampLimit) exceeds Int.MAX_VALUE. Clamping.")
                                Int.MAX_VALUE
                            } else {
                                stampLimit.toInt()
                            }

                            val txResult = networkService.sendTransaction(
                                contract = advContractName,
                                method = advSelectedFunction!!.name,
                                kwargs = kwargs,
                                privateKey = finalPrivateKey, // Use the non-nullable local variable
                                stampLimit = stampLimitInt // Pass the Int value
                            )

                             // --- 5. Handle Result ---
                             if (txResult.success) {
                                 advSuccessMessage = "Transaction sent successfully! Tx Hash: ${txResult.txHash}"
                                 // Optionally clear fields on success
                                 // advContractName = "" // Keep contract/function selected for potential reuse
                                 // advSelectedFunction = null
                                 // advArguments = emptyMap()
                             } else {
                                 advErrorMessage = "Transaction failed: ${txResult.errors ?: "Unknown error"}"
                             }

                         } catch (e: Exception) {
                             println("Error during advanced transaction process: ${e.message}")
                             advErrorMessage = "Error: ${e.message}" // Display specific parsing or network errors
                         } finally {
                             advIsSubmitting = false
                         }
                     }
                 }
             )
         }
    }
}


@Composable
private fun PasswordPromptDialog(
    showPasswordField: Boolean, // Add parameter to control password field visibility
    onConfirm: (String?) -> Unit, // Password might be null if not shown
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { if (showPasswordField) Text("Password Required") else Text("Confirm Transaction") }, // Adjust title
        text = {
            Column {
                if (showPasswordField) {
                    Text("Wallet is locked. Please enter your password to proceed.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                } else {
                    Text("Confirm token creation transaction?") // Confirmation text when unlocked
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(if (showPasswordField) password else null) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}