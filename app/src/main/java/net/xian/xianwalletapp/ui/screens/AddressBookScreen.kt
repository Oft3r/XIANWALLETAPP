package net.xian.xianwalletapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.xian.xianwalletapp.data.db.AddressBookEntity
import net.xian.xianwalletapp.data.db.AppDatabase
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onAddressSelected: ((String) -> Unit)? = null,
    prefilledAddress: String? = null
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val addressBookDao = database.addressBookDao()
    
    var addresses by remember { mutableStateOf<List<AddressBookEntity>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<AddressBookEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Show dialog automatically if prefilledAddress is provided
    LaunchedEffect(prefilledAddress) {
        if (!prefilledAddress.isNullOrBlank()) {
            showAddDialog = true
        }
    }
    
    // Collect addresses from database
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            addressBookDao.getAllAddresses().collect { addressList ->
                addresses = addressList
            }
        } else {
            addressBookDao.searchAddresses(searchQuery).collect { addressList ->
                addresses = addressList
            }
        }
    }
    
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        bottomBar = {
            // Add new address button at bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = XianPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Address")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Address Book",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = XianPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { isSearchActive = !isSearchActive }
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (isSearchActive) "Close search" else "Search",
                        tint = XianPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search bar
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by name or address") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }
            
            if (isSearchActive) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Address list
            if (addresses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No addresses found" else "No saved addresses yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search term" else "Tap 'Add New Address' to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(addresses) { address ->
                        AddressBookItem(
                            address = address,
                            onEdit = { editingAddress = it },
                            onDelete = { addressToDelete ->
                                scope.launch {
                                    addressBookDao.deleteAddress(addressToDelete)
                                }
                            },
                            onSelect = if (onAddressSelected != null) {
                                { selectedAddress ->
                                    onAddressSelected(selectedAddress.walletAddress)
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog || editingAddress != null) {
        AddressBookDialog(
            address = editingAddress,
            prefilledAddress = if (editingAddress == null) prefilledAddress else null,
            onDismiss = {
                showAddDialog = false
                editingAddress = null
            },
            onSave = { name, walletAddress, notes ->
                scope.launch {
                    if (editingAddress != null) {
                        // Update existing address
                        val updatedAddress = editingAddress!!.copy(
                            name = name,
                            walletAddress = walletAddress,
                            notes = notes,
                            updatedAt = System.currentTimeMillis()
                        )
                        addressBookDao.updateAddress(updatedAddress)
                    } else {
                        // Add new address
                        val newAddress = AddressBookEntity(
                            name = name,
                            walletAddress = walletAddress,
                            notes = notes
                        )
                        addressBookDao.insertAddress(newAddress)
                    }
                    showAddDialog = false
                    editingAddress = null
                }
            }
        )
    }
}

@Composable
private fun AddressBookItem(
    address: AddressBookEntity,
    onEdit: (AddressBookEntity) -> Unit,
    onDelete: (AddressBookEntity) -> Unit,
    onSelect: ((AddressBookEntity) -> Unit)? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onSelect != null) {
                    onSelect(address)
                } else {
                    onEdit(address)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = address.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = XianPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = address.walletAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!address.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = address.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Row {
                    IconButton(
                        onClick = { onEdit(address) }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = XianPrimary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Address") },
            text = { Text("Are you sure you want to delete '${address.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(address)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressBookDialog(
    address: AddressBookEntity?,
    prefilledAddress: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(address?.name ?: "") }
    var walletAddress by remember { mutableStateOf(address?.walletAddress ?: prefilledAddress ?: "") }
    var notes by remember { mutableStateOf(address?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf(false) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (address != null) "Edit Address" else "Add New Address")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = false
                    },
                    label = { Text("Name") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Name is required") } } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = walletAddress,
                    onValueChange = { 
                        walletAddress = it
                        addressError = false
                    },
                    label = { Text("Wallet Address") },
                    isError = addressError,
                    supportingText = if (addressError) { { Text("Valid wallet address is required") } } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate inputs
                    nameError = name.isBlank()
                    addressError = walletAddress.isBlank() || !isValidWalletAddress(walletAddress)
                    
                    if (!nameError && !addressError) {
                        onSave(name.trim(), walletAddress.trim(), notes.trim())
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun isValidWalletAddress(address: String): Boolean {
    // Basic validation for wallet address (you can enhance this based on Xian wallet address format)
    return address.isNotBlank() && address.length >= 20 && address.all { it.isLetterOrDigit() }
}