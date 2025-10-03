package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.network.OpenRouterService
import net.xian.xianwalletapp.ui.components.PasswordTextField
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(
    navController: NavController,
    walletManager: WalletManager
) {
    val coroutineScope = rememberCoroutineScope()
    val toastHostState = net.xian.xianwalletapp.ui.components.rememberToastHostState()

    var apiKey by remember { mutableStateOf(walletManager.getOpenRouterApiKey() ?: "") }
    var model by remember { mutableStateOf(walletManager.getOpenRouterModel()) }
    var testing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("AI Analysis Settings", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            net.xian.xianwalletapp.ui.components.TopToastHost(
                state = toastHostState,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure your OpenRouter API key and preferred model.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("OpenRouter API Key", fontWeight = FontWeight.Medium)
                PasswordTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Enter your OpenRouter API key") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Model", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("e.g. openrouter/auto or moonshotai/kimi-k2:free") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Quick select",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "openrouter/auto",
                        "moonshotai/kimi-k2:free",
                        "google/gemini-2.5-flash",
                        "openai/gpt-4o-mini"
                    )
                    presets.forEach { m ->
                        AssistChip(onClick = { model = m }, label = { Text(m) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                        walletManager.setOpenRouterApiKey(apiKey)
                        walletManager.setOpenRouterModel(model)
                        coroutineScope.launch {
                            toastHostState.show("AI settings saved", net.xian.xianwalletapp.ui.components.ToastType.Success)
                        }
                        },
                        colors = ButtonDefaults.buttonColors(contentColor = Color.Black)
                    ) { Text("Save") }

                    TextButton(onClick = {
                        apiKey = walletManager.getOpenRouterApiKey() ?: ""
                        model = walletManager.getOpenRouterModel()
                    }) { Text("Reset") }

                    OutlinedButton(onClick = {
                        if (apiKey.isBlank()) {
                            coroutineScope.launch { toastHostState.show("Enter API key to test", net.xian.xianwalletapp.ui.components.ToastType.Error) }
                        } else {
                            testing = true
                            coroutineScope.launch {
                                try {
                                    OpenRouterService.chatCompletion(
                                        systemPrompt = "You are a concise assistant.",
                                        userPrompt = "Reply with: OK",
                                        apiKey = apiKey,
                                        model = model,
                                        temperature = 0.0f,
                                        topP = 1.0f
                                    )
                                    toastHostState.show("AI settings test passed", net.xian.xianwalletapp.ui.components.ToastType.Success)
                                } catch (e: Exception) {
                                    toastHostState.show("Test failed: ${e.localizedMessage ?: "error"}", net.xian.xianwalletapp.ui.components.ToastType.Error)
                                } finally {
                                    testing = false
                                }
                            }
                        }
                    }) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test Configuration")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your key stays only on-device. Required for AI portfolio analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
