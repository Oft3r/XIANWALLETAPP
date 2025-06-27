package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
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
                                                viewModel.addTokenAndRefresh(contractAddress)
                                                contractAddress = ""
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Token added successfully")
                                                }
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

            // Section: Added Tokens
            if (addedTokens.isNotEmpty()) {
                item {
                    Text(
                        text = "Added Tokens",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                }

                items(addedTokens) { contract ->
                    val tokenInfo = tokenInfoMap[contract]
                    AddedTokenItem(
                        contract = contract,
                        name = tokenInfo?.name ?: contract,
                        symbol = tokenInfo?.symbol ?: "",
                        logoUrl = tokenInfo?.logoUrl,
                        onRemoveClick = {
                            viewModel.removeToken(contract)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Token removed")
                            }
                        }
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
                        onAddClick = {
                            viewModel.addTokenAndRefresh(token.contract)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("${token.name} added")
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
    onRemoveClick: () -> Unit
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
                    else -> logoUrl
                },
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

            // Remove button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.error, CircleShape)
                    .clickable(onClick = onRemoveClick),
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

@Composable
private fun AvailableTokenItem(
    token: PredefinedToken,
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
                    else -> token.logoUrl
                },
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