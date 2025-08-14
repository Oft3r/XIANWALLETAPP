package net.xian.xianwalletapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryText
import net.xian.xianwalletapp.ui.theme.XianSecondaryText

// Replaced simple PasswordTextField with reusable component that supports visibility toggle (see PasswordTextField.kt)

@Composable
fun StakeDialog(
    isVisible: Boolean,
    maxAmount: Double,
    isLoading: Boolean,
    needsPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, password: String) -> Unit,
    tokenSymbol: String = "XIAN"
) {
    if (!isVisible) return

    var amount by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Stake $tokenSymbol",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = XianPrimaryText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Enter the amount of $tokenSymbol to stake:",
                    color = XianSecondaryText
                )
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        // Only allow numbers and decimal point
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amount = newValue
                        }
                    },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text(tokenSymbol) }
                )
                
                if (maxAmount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Available: ${String.format("%.2f", maxAmount)} $tokenSymbol",
                            color = XianSecondaryText,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = { amount = maxAmount.toString() }
                        ) {
                            Text("Max", color = XianPrimary)
                        }
                    }
                }
                
                if (needsPassword) {
                    PasswordTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Wallet Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val passwordToUse = if (needsPassword) password else ""
                    if (amount.isNotBlank() && (!needsPassword || password.isNotBlank())) {
                        onConfirm(amount, passwordToUse)
                    }
                },
                enabled = !isLoading && amount.isNotBlank() && (!needsPassword || password.isNotBlank()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = XianPrimary,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                } else {
                    Text("Stake")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UnstakeDialog(
    isVisible: Boolean,
    maxAmount: Double,
    isLoading: Boolean,
    needsPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, password: String) -> Unit,
    tokenSymbol: String = "XIAN"
) {
    if (!isVisible) return

    var amount by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unstake $tokenSymbol",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = XianPrimaryText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Enter the amount of $tokenSymbol to unstake:",
                    color = XianSecondaryText
                )
                
                Text(
                    text = "Note: Funds must be locked for 7 days before unstaking.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        // Only allow numbers and decimal point
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amount = newValue
                        }
                    },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text(tokenSymbol) }
                )
                
                if (maxAmount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Staked: ${String.format("%.2f", maxAmount)} $tokenSymbol",
                            color = XianSecondaryText,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = { amount = maxAmount.toString() }
                        ) {
                            Text("Max", color = XianPrimary)
                        }
                    }
                }
                
                if (needsPassword) {
                    PasswordTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Wallet Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val passwordToUse = if (needsPassword) password else ""
                    if (amount.isNotBlank() && (!needsPassword || password.isNotBlank())) {
                        onConfirm(amount, passwordToUse)
                    }
                },
                enabled = !isLoading && amount.isNotBlank() && (!needsPassword || password.isNotBlank()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Text("Unstake")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClaimRewardsDialog(
    isVisible: Boolean,
    rewardsAmount: Double,
    isLoading: Boolean,
    needsPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
    tokenSymbol: String = "XIAN"
) {
    if (!isVisible) return

    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Claim Rewards",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = XianPrimaryText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "You have ${String.format("%.6f", rewardsAmount)} $tokenSymbol in rewards to claim.",
                    color = XianSecondaryText
                )
                
                if (needsPassword) {
                    PasswordTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Wallet Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val passwordToUse = if (needsPassword) password else ""
                    if (!needsPassword || password.isNotBlank()) {
                        onConfirm(passwordToUse)
                    }
                },
                enabled = !isLoading && (!needsPassword || password.isNotBlank()) && rewardsAmount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = XianPrimary,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                } else {
                    Text("Claim", color = androidx.compose.ui.graphics.Color.Black)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}