package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.snapshotFlow
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel
import net.xian.xianwalletapp.ui.viewmodels.PredefinedToken

/**
 * Component for managing tokens - adding and removing tokens
 * This replaces the edit mode functionality and acts as a separate screen/tab
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTokenList(
    viewModel: WalletViewModel,
    onBackClick: () -> Unit,
    showBottomBar: Boolean,
    onShowBottomBarChange: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Collect state from ViewModel
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val predefinedTokens by viewModel.predefinedTokens.collectAsStateWithLifecycle()
    
    // Local state for manual token addition
    var contractAddress by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var textFieldWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    
    // Scroll state for managing bottom bar visibility
    val listState = rememberLazyListState()
    var lastScrollIndex by remember { mutableStateOf(0) }
    var lastScrollOffset by remember { mutableStateOf(0) }
    
    // Track scroll to show/hide bottom bar
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        if (index > lastScrollIndex || (index == lastScrollIndex && offset > lastScrollOffset + 10)) {
            // Scroll down (hide bottom bar)
            if (showBottomBar) onShowBottomBarChange(false)
        } else if (index < lastScrollIndex || (index == lastScrollIndex && offset < lastScrollOffset - 10)) {
            // Scroll up (show bottom bar)
            if (!showBottomBar) onShowBottomBarChange(true)
        }
        lastScrollIndex = index
        lastScrollOffset = offset
    }
    
    // Filter predefined tokens to show only those not already added
    val availableTokens = predefinedTokens.filter { predefined ->
        !tokens.contains(predefined.contract)
    }
    
    // Filter added tokens (exclude XIAN currency as it can't be removed)
    val addedTokens = tokens.filter { it != "currency" }
    
    // Local state for reordering (exclude "currency" from reorderable tokens)
    val reorderableTokens = tokens.filter { it != "currency" }
    var localTokenOrder by remember { mutableStateOf(reorderableTokens) }
    var isReorderMode by remember { mutableStateOf(false) }
    
    // Update local order when tokens change
    LaunchedEffect(tokens) {
        localTokenOrder = tokens.filter { it != "currency" }
    }
    
    // Function to move item in list
    fun moveItem(fromIndex: Int, toIndex: Int) {
        val mutableList = localTokenOrder.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        localTokenOrder = mutableList
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Manual token addition section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Add Token Manually",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        
                        // Box to anchor the dropdown and measure the TextField
                        Box {
                            OutlinedTextField(
                                value = contractAddress,
                                onValueChange = { contractAddress = it },
                                label = { Text("Token Contract Address") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        val widthInPixels = coordinates.size.width
                                        if (textFieldWidthPx != widthInPixels) {
                                            textFieldWidthPx = widthInPixels
                                        }
                                    },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (contractAddress.isNotBlank()) {
                                                viewModel.addTokenAndRefresh(contractAddress) { result ->
                                                    coroutineScope.launch {
                                                        val message = when (result) {
                                                            net.xian.xianwalletapp.wallet.TokenAddResult.SUCCESS ->
                                                                "Token added successfully"
                                                            net.xian.xianwalletapp.wallet.TokenAddResult.ALREADY_EXISTS ->
                                                                "Token is already in your wallet"
                                                            net.xian.xianwalletapp.wallet.TokenAddResult.INVALID_CONTRACT ->
                                                                "Invalid contract address"
                                                            net.xian.xianwalletapp.wallet.TokenAddResult.NO_ACTIVE_WALLET ->
                                                                "No active wallet found"
                                                            net.xian.xianwalletapp.wallet.TokenAddResult.FAILED ->
                                                                "Failed to add token"
                                                        }
                                                        snackbarHostState.showSnackbar(message)
                                                    }
                                                }
                                                contractAddress = ""
                                            }
                                        },
                                        enabled = !expanded && contractAddress.isNotBlank()
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add Token",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Section: Token Order (only show if there are non-currency tokens)
            if (localTokenOrder.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Token Order",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isReorderMode) {
                                Button(
                                    onClick = {
                                        // Reconstruct the full token list with "currency" first
                                        val fullTokenOrder = if (tokens.contains("currency")) {
                                            listOf("currency") + localTokenOrder
                                        } else {
                                            localTokenOrder
                                        }
                                        viewModel.reorderTokens(fullTokenOrder)
                                        isReorderMode = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Token order saved")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Save Order")
                                }
                                
                                TextButton(
                                    onClick = {
                                        localTokenOrder = tokens.filter { it != "currency" }
                                        isReorderMode = false
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            } else {
                                if (localTokenOrder.isNotEmpty()) {
                                    TextButton(
                                        onClick = { isReorderMode = true }
                                    ) {
                                        Text("Reorder")
                                    }
                                }
                            }
                        }
                    }
                }

                // Show only reorderable tokens (currency is excluded from this view)
                itemsIndexed(localTokenOrder) { index, contract ->
                    val tokenInfo = tokenInfoMap[contract]
                    AddedTokenItem(
                        contract = contract,
                        name = tokenInfo?.name ?: contract,
                        symbol = tokenInfo?.symbol ?: "",
                        logoUrl = tokenInfo?.logoUrl,
                        viewModel = viewModel, // Pass viewModel for image loader access
                        isReorderMode = isReorderMode,
                        canMoveUp = index > 0,
                        canMoveDown = index < localTokenOrder.size - 1,
                        onMoveUp = if (index > 0) { { moveItem(index, index - 1) } } else null,
                        onMoveDown = if (index < localTokenOrder.size - 1) { { moveItem(index, index + 1) } } else null,
                        onRemoveClick = if (!isReorderMode) {
                            {
                                viewModel.removeToken(contract)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Token removed")
                                }
                            }
                        } else null
                    )
                }
            }

            // Section: Available Tokens to Add
            if (availableTokens.isNotEmpty()) {
                item {
                    Text(
                        text = "Available Tokens",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(availableTokens) { token ->
                    AvailableTokenItem(
                        token = token,
                        viewModel = viewModel, // Pass viewModel for image loader access
                        onAddClick = {
                            viewModel.addTokenAndRefresh(token.contract) { result ->
                                coroutineScope.launch {
                                    val message = when (result) {
                                        net.xian.xianwalletapp.wallet.TokenAddResult.SUCCESS ->
                                            "${token.name} added successfully"
                                        net.xian.xianwalletapp.wallet.TokenAddResult.ALREADY_EXISTS ->
                                            "${token.name} is already in your wallet"
                                        net.xian.xianwalletapp.wallet.TokenAddResult.INVALID_CONTRACT ->
                                            "Invalid contract address"
                                        net.xian.xianwalletapp.wallet.TokenAddResult.NO_ACTIVE_WALLET ->
                                            "No active wallet found"
                                        net.xian.xianwalletapp.wallet.TokenAddResult.FAILED ->
                                            "Failed to add ${token.name}"
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                    )
                }
            }

            // Empty state if no tokens available to add
            if (availableTokens.isEmpty() && addedTokens.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All available tokens have been added.\nYou can add custom tokens using the contract address above.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // SnackbarHost positioned above the bottom navigation bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 16.dp)
        ) {
            SnackbarHost(snackbarHostState)
        }
    }
}

@Composable
private fun AddedTokenItem(
    contract: String,
    name: String,
    symbol: String,
    logoUrl: String?,
    viewModel: WalletViewModel, // Add viewModel parameter to access image loader
    isReorderMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = when {
                    contract == "con_xarb" -> "file:///android_asset/xarb.jpg"
                    contract == "con_xtfu" -> "https://snakexchange.org/icons/con_xtfu.png"
                    else -> logoUrl
                },
                imageLoader = viewModel.getImageLoader(), // Use the cached image loader
                contentDescription = "$name Logo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                placeholder = painterResource(id = R.drawable.ic_question_mark),
                error = painterResource(id = R.drawable.ic_question_mark)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = symbol.ifEmpty { contract },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            if (isReorderMode) {
                // Reorder controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Move Up button
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = canMoveUp
                    ) {
                        Icon(
                            Icons.Default.ArrowDropUp,
                            contentDescription = "Move Up",
                            tint = if (canMoveUp) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    
                    // Move Down button
                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = canMoveDown
                    ) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Move Down",
                            tint = if (canMoveDown) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                // Remove button (only show if not in reorder mode and onRemoveClick is provided)
                onRemoveClick?.let { removeClick ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .border(1.dp, MaterialTheme.colorScheme.error, CircleShape)
                            .clickable(onClick = removeClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableTokenItem(
    token: PredefinedToken,
    viewModel: WalletViewModel, // Add viewModel parameter to access image loader
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = when {
                    token.contract == "con_xarb" -> "file:///android_asset/xarb.jpg"
                    token.contract == "con_xtfu" -> "https://snakexchange.org/icons/con_xtfu.png"
                    else -> token.logoUrl
                },
                imageLoader = viewModel.getImageLoader(), // Use the cached image loader
                contentDescription = "${token.name} Logo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                placeholder = painterResource(id = R.drawable.ic_question_mark),
                error = painterResource(id = R.drawable.ic_question_mark)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = token.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = token.contract,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Add button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}