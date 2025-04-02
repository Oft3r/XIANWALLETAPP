package com.example.xianwalletapp.network

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull // Import for extension function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import com.example.xianwalletapp.crypto.XianCrypto
import com.example.xianwalletapp.wallet.WalletManager

/**
 * Service class for interacting with the Xian Network
 * Handles all network calls to the Xian RPC endpoints
 */
class XianNetworkService private constructor(private val context: Context) {
    companion object {
        // Singleton instance
        @Volatile
        private var instance: XianNetworkService? = null
        
        fun getInstance(context: Context): XianNetworkService {
            return instance ?: synchronized(this) {
                instance ?: XianNetworkService(context).also { instance = it }
            }
        }
    }
    
    // Referencia al WalletManager con contexto
    private val walletManager = WalletManager.getInstance(context)
    
    private fun deriveAddressFromPublicKey(publicKey: String): String {
        try {
            // Log the input public key for debugging
            android.util.Log.d("XianNetworkService", "Deriving address from public key: $publicKey")
            
            // Important: In Xian network, the address IS the public key
            // This matches the behavior in xian.js where the publicKey is used directly
            return publicKey
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error deriving address: ${e.message}", e)
            return publicKey // Return public key if derivation fails
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    
    // Lista de nodos alternativos para probar si el principal falla
    private val nodeUrls = listOf(
        "https://node.xian.org"
    )
    
    private var rpcUrl = nodeUrls[0] // Comenzar con el nodo principal
    private var explorerUrl = "https://explorer.xian.org"
    private var chainId: String? = null
    
    /**
     * Set the RPC URL for the Xian network
     */
    fun setRpcUrl(url: String) {
        rpcUrl = url
    }
    
    /**
     * Set the explorer URL for the Xian network
     */
    fun setExplorerUrl(url: String) {
        explorerUrl = url
    }

    // Variable para ayudar a depurar problemas de URL
    private fun logURL(url: String, message: String) {
        android.util.Log.d("XianNetworkService", "$message: $url")
        // También mostrar bytes para depuración
        val bytes = url.toByteArray()
        val hexString = bytes.joinToString("") { "%02x".format(it) }
        android.util.Log.d("XianNetworkService", "URL bytes: $hexString")
    }

    /**
     * Prueba conectividad con múltiples nodos y selecciona el primero que responda
     * @return true si se encontró un nodo funcional, false si todos fallan
     */
    suspend fun findWorkingNode(): Boolean = withContext(Dispatchers.IO) {
        var nodeFound = false
        
        for (nodeUrl in nodeUrls) {
            try {
                android.util.Log.d("XianNetworkService", "Probando conexión al nodo: $nodeUrl")
                
                val response = client.newCall(
                    Request.Builder()
                        .url("$nodeUrl/status")
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    android.util.Log.d("XianNetworkService", "Nodo funcional encontrado: $nodeUrl")
                    rpcUrl = nodeUrl
                    nodeFound = true
                    break
                } else {
                    android.util.Log.d("XianNetworkService", "El nodo $nodeUrl respondió con código: ${response.code}")
                }
            } catch (e: Exception) {
                android.util.Log.e("XianNetworkService", "Error al conectar con el nodo $nodeUrl: ${e.message}")
            }
        }
        
        return@withContext nodeFound
    }

    /**
     * Verifica la conectividad con el nodo Xian
     * @return true si se puede conectar, false en caso contrario
     */
    suspend fun checkNodeConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Intentar primero con el nodo configurado actualmente
            val response = client.newCall(
                Request.Builder()
                    .url("$rpcUrl/status")
                    .build()
            ).execute()
            
            val isConnected = response.isSuccessful
            android.util.Log.d("XianNetworkService", "Conexión con el nodo $rpcUrl: $isConnected, código: ${response.code}")
            
            if (isConnected) {
                return@withContext true
            }
            
            // Si falla, intentar encontrar un nodo alternativo
            return@withContext findWorkingNode()
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error al conectar con el nodo $rpcUrl: ${e.message}")
            
            // Si hay error, intentar buscar un nodo alternativo
            return@withContext findWorkingNode()
        }
    }

    /**
     * Get the next nonce for an address
     */
    suspend fun getNonce(address: String): Int = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder()
                    .url("$rpcUrl/abci_query?path=\"/get_next_nonce/$address\"")
                    .build()
            ).execute()

            val json = JSONObject(response.body?.string() ?: "{}")
            val base64Value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (base64Value == "AA==") return@withContext 0
            
            return@withContext if (base64Value != null) {
                String(Base64.decode(base64Value, Base64.DEFAULT)).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting nonce: ${e.message}")
            return@withContext 0
        }
    }

    /**
     * Get the balance of a token for a specific address using public key
     * Uses the same approach as the web wallet:
     * 1. First tries to use simulate_tx with balance_of function
     * 2. Falls back to direct variable access if the first method fails
     * 
     * @param contract The token contract address
     * @param publicKey The public key of the wallet to check balance for
     * @return The token balance as a Float
     */
    suspend fun getTokenBalance(contract: String, publicKey: String): Float = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Abortando obtención de balance.")
                return@withContext 0f
            }
            
            android.util.Log.d("XianNetworkService", "Usando nodo: $rpcUrl para obtener balance")
            
            // En Xian, la dirección y la clave pública son lo mismo
            val address = publicKey
            android.util.Log.d("XianNetworkService", "Getting balance for contract: $contract, address: $address")
            
            // MÉTODO 1: Usar simulate_tx con la función balance_of
            // Crear payload exactamente como en la versión web
            val payload = JSONObject().apply {
                put("sender", address)
                put("contract", contract)
                put("function", "balance_of")
                put("kwargs", JSONObject().apply {
                    put("address", address)
                })
            }
            
            // Convertir payload a hex string exactamente como en la versión web
            val payloadBytes = payload.toString().toByteArray()
            val payloadHex = payloadBytes.joinToString("") { "%02x".format(it) }
            
            // Es crucial usar exactamente el mismo formato de URL que en la versión web
            // En la versión web, las URLs se construyen así:
            // fetch(RPC + '/abci_query?path="/simulate_tx/' + hex + '"')
            val unescapedUrl = "$rpcUrl/abci_query?path=\"/simulate_tx/$payloadHex\""
            // Usamos java.net.URLEncoder para asegurarnos de que las comillas dobles se codifiquen correctamente
            // pero preservamos la forma /abci_query?path= sin codificar
            val baseUrl = "$rpcUrl/abci_query?path="
            val pathPart = "\"/simulate_tx/$payloadHex\""
            val encodedPathPart = java.net.URLEncoder.encode(pathPart, "UTF-8")
            // URLEncoder codifica los espacios como '+', los reemplazamos por %20
            val finalPathPart = encodedPathPart.replace("+", "%20")
            val simulateUrl = baseUrl + finalPathPart
            
            logURL(unescapedUrl, "URL sin escapar")
            logURL(simulateUrl, "URL final para simulate_tx")
            
            // Realizar la solicitud simulate_tx
            val simulateResponse = client.newCall(
                Request.Builder()
                    .url(simulateUrl)
                    .build()
            ).execute()
            
            val simulateResponseBody = simulateResponse.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Simulate response: $simulateResponseBody")
            
            val simulateJson = JSONObject(simulateResponseBody)
            val simulateValue = simulateJson.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            android.util.Log.d("XianNetworkService", "Simulate value (Base64): $simulateValue")
            
            if (simulateValue != null && simulateValue.isNotEmpty()) {
                try {
                    // Decodificar el valor Base64
                    val simulateDecodedBytes = Base64.decode(simulateValue, Base64.DEFAULT)
                    val simulateDecoded = String(simulateDecodedBytes)
                    android.util.Log.d("XianNetworkService", "Simulate decoded: $simulateDecoded")
                    
                    // Verificar valor decodificado siguiendo la lógica exacta de la versión web
                    if (simulateDecoded != "ée" && simulateDecoded != "AA==" && simulateDecoded != null && simulateDecoded.isNotEmpty() && simulateDecoded != "null") {
                        try {
                            // En la versión web, se extrae directamente el campo "result" del JSON
                            val decodedJson = JSONObject(simulateDecoded)
                            if (decodedJson.has("result")) {
                                val result = decodedJson.getString("result")
                                android.util.Log.d("XianNetworkService", "Result from JSON: $result")
                                
                                // Usar BigDecimal para evitar problemas de precisión con números decimales
                                val parsedBalance = try {
                                    java.math.BigDecimal(result)
                                } catch (e: Exception) {
                                    java.math.BigDecimal.ZERO
                                }
                                
                                // Formatear a 1 decimal en lugar de 8
                                val formattedBalance = parsedBalance.setScale(1, java.math.RoundingMode.HALF_UP).toFloat()
                                android.util.Log.d("XianNetworkService", "Final parsed balance from simulate: $formattedBalance")
                                return@withContext formattedBalance
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("XianNetworkService", "Error parsing simulate JSON: ${e.message}")
                            // Continuar con el fallback si hay error de parseo
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XianNetworkService", "Error decodificando Base64: ${e.message}")
                }
            }
            
            // MÉTODO 2 (FALLBACK): Usar getVariable como en la versión web
            android.util.Log.d("XianNetworkService", "Using getVariable fallback method")
            
            // Construir la URL para getVariable exactamente como en la versión web
            val unescapedVarUrl = "$rpcUrl/abci_query?path=\"/get/$contract.balances:$address\""
            val varPathPart = "\"/get/$contract.balances:$address\""
            val encodedVarPathPart = java.net.URLEncoder.encode(varPathPart, "UTF-8")
            val finalVarPathPart = encodedVarPathPart.replace("+", "%20")
            val variableUrl = baseUrl + finalVarPathPart
            
            logURL(unescapedVarUrl, "URL sin escapar para getVariable")
            logURL(variableUrl, "URL final para getVariable")
            
            val variableResponse = client.newCall(
                Request.Builder()
                    .url(variableUrl)
                    .build()
            ).execute()
            
            val variableResponseBody = variableResponse.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Variable response: $variableResponseBody")
            
            val variableJson = JSONObject(variableResponseBody)
            val variableValue = variableJson.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            android.util.Log.d("XianNetworkService", "Variable value (Base64): $variableValue")
            
            // Verificar si el valor es nulo o AA== (vacío) como en la versión web
            if (variableValue == null || variableValue == "AA==") {
                return@withContext 0f
            }
            
            try {
                // Decodificar el valor Base64
                val variableDecodedBytes = Base64.decode(variableValue, Base64.DEFAULT)
                val variableDecoded = String(variableDecodedBytes)
                android.util.Log.d("XianNetworkService", "Variable decoded: $variableDecoded")
                
                // Intentar parsear el valor como número
                val parsedBalance = try {
                    java.math.BigDecimal(variableDecoded)
                } catch (e: Exception) {
                    java.math.BigDecimal.ZERO
                }
                
                // Formatear a 1 decimal en lugar de 8
                val formattedBalance = parsedBalance.setScale(1, java.math.RoundingMode.HALF_UP).toFloat()
                android.util.Log.d("XianNetworkService", "Final parsed balance from variable: $formattedBalance")
                return@withContext formattedBalance
            } catch (e: Exception) {
                android.util.Log.e("XianNetworkService", "Error parsing variable value: ${e.message}")
                return@withContext 0f
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting token balance: ${e.message}")
            return@withContext 0f
        }
    }
    
    /**
     * Get token information for a specific contract
     * 
     * @param contract The token contract address
     * @return TokenInfo object with token details
     */
    suspend fun getTokenInfo(contract: String): TokenInfo = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Abortando obtención de token info.")
                // Use a more user-friendly fallback for currency
                return@withContext if (contract == "currency") {
                    TokenInfo(name = "Xian Currency", symbol = "XIAN", contract = contract)
                } else {
                    TokenInfo(name = contract, symbol = contract.take(3).uppercase(), contract = contract)
                }
            }
            
            // MÉTODO 1: Usar simulate_tx para obtener información del token
            val payload = JSONObject().apply {
                put("sender", "")
                put("contract", contract)
                put("function", "get_token_info")
                put("kwargs", JSONObject())
            }
            
            // Convertir payload a hex string
            val payloadBytes = payload.toString().toByteArray()
            val payloadHex = payloadBytes.joinToString("") { "%02x".format(it) }
            
            // Construir URL para simulate_tx
            val baseUrl = "$rpcUrl/abci_query?path="
            val pathPart = "\"/simulate_tx/$payloadHex\""
            val encodedPathPart = java.net.URLEncoder.encode(pathPart, "UTF-8")
            val finalPathPart = encodedPathPart.replace("+", "%20")
            val simulateUrl = baseUrl + finalPathPart
            
            // Realizar la solicitud simulate_tx
            val simulateResponse = client.newCall(
                Request.Builder()
                    .url(simulateUrl)
                    .build()
            ).execute()
            
            val simulateResponseBody = simulateResponse.body?.string() ?: "{}"
            val simulateJson = JSONObject(simulateResponseBody)
            val simulateValue = simulateJson.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (simulateValue != null && simulateValue.isNotEmpty()) {
                try {
                    // Decodificar el valor Base64
                    val simulateDecodedBytes = Base64.decode(simulateValue, Base64.DEFAULT)
                    val simulateDecoded = String(simulateDecodedBytes)
                    
                    // Parsear el JSON de respuesta
                    val decodedJson = JSONObject(simulateDecoded)
                    if (decodedJson.has("result")) {
                        val resultJson = JSONObject(decodedJson.getString("result"))
                        return@withContext TokenInfo(
                            name = resultJson.optString("name", contract),
                            symbol = resultJson.optString("symbol", contract.take(3).uppercase()),
                            decimals = resultJson.optInt("decimals", 8),
                            contract = contract
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XianNetworkService", "Error parsing token info: ${e.message}")
                }
            }
            
            // Si no se pudo obtener la información, devolver valores por defecto más amigables
            return@withContext if (contract == "currency") {
                TokenInfo(name = "Xian Currency", symbol = "XIAN", contract = contract)
            } else {
                TokenInfo(name = contract, symbol = contract.take(3).uppercase(), contract = contract)
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting token info: ${e.message}")
            // Si hay error, devolver valores por defecto más amigables
            return@withContext if (contract == "currency") {
                TokenInfo(name = "Xian Currency", symbol = "XIAN", contract = contract)
            } else {
                TokenInfo(name = contract, symbol = contract.take(3).uppercase(), contract = contract)
            }
        }
    }

    // --- Start of new NFT functions ---

    /**
     * Fetches the keys of NFTs owned by a specific public key.
     * Corresponds to the first GraphQL query in the web version.
     */
    private suspend fun getOwnedNftKeys(publicKey: String): List<String> = withContext(Dispatchers.IO) {
        val graphQLEndpoint = "$rpcUrl/graphql"
        val query = """
            query MyQuery {
              allStates(
                filter: {
                  key: { startsWith: "con_pixel_frames_info.S", endsWith: "owner" }
                  value: { equalTo: "$publicKey" }
                }
                offset: 0
                first: 100
                orderBy: UPDATED_DESC
              ) {
                nodes {
                  key
                }
              }
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("query", query)
        }.toString()

        val request = Request.Builder()
            .url(graphQLEndpoint)
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("XianNetworkService", "Failed to fetch owned NFT keys: ${response.code} ${response.message}")
                return@withContext emptyList()
            }

            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Owned NFT Keys Response: $responseBody")
            val json = JSONObject(responseBody)
            val nodes = json.optJSONObject("data")?.optJSONObject("allStates")?.optJSONArray("nodes")

            val keys = mutableListOf<String>()
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    val key = node.optString("key", "")
                    // Extract the NFT address part using the logic from the web version
                    // key format: con_pixel_frames_info.S:<nft_address>:owner
                    if (key.contains("con_pixel_frames_info.S") && key.contains(":owner")) {
                         val addressPart = key.substringAfter("con_pixel_frames_info.S") // Gives ":<nft_address>:owner" or "<nft_address>:owner"
                         val nftAddress = addressPart.removePrefix(":").split(":")[0] // Remove leading ":" if present, then split and take first part
                         if (nftAddress.isNotEmpty()) {
                             keys.add(nftAddress)
                             android.util.Log.d("XianNetworkService", "Found NFT Key: $nftAddress")
                        }
                    }
                }
            }
            return@withContext keys
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error fetching owned NFT keys: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Fetches the metadata for a specific NFT key.
     * Corresponds to the second GraphQL query in the web version.
     */
    private suspend fun getNftMetadata(nftKey: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val graphQLEndpoint = "$rpcUrl/graphql"
        // Note: The web version uses specific indices (9 for name, 3 for description).
        // This might be fragile. A more robust approach would be to filter by key suffix if possible.
        val query = """
            query MyQuery {
              i_0: allStates(
                filter: {key: {startsWith: "con_pixel_frames_info.S:$nftKey"}}
              ) {
                nodes {
                  key
                  value
                }
              }
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("query", query)
        }.toString()

        val request = Request.Builder()
            .url(graphQLEndpoint)
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("XianNetworkService", "Failed to fetch NFT metadata for $nftKey: ${response.code} ${response.message}")
                return@withContext Pair(null, null)
            }

            val responseBody = response.body?.string() ?: "{}"
             android.util.Log.d("XianNetworkService", "NFT Metadata Response for $nftKey: $responseBody")
            val json = JSONObject(responseBody)
            val nodes = json.optJSONObject("data")?.optJSONObject("i_0")?.optJSONArray("nodes")

            var name: String? = null
            var description: String? = null

            if (nodes != null && nodes.length() > 9) { // Check length before accessing indices
                 // Assuming indices 9 and 3 are correct based on web version
                 try {
                     name = nodes.getJSONObject(9)?.optString("value")
                     description = nodes.getJSONObject(3)?.optString("value")
                     android.util.Log.d("XianNetworkService", "Extracted Metadata for $nftKey - Name: $name, Desc: $description")
                 } catch (e: org.json.JSONException) {
                     android.util.Log.e("XianNetworkService", "Error accessing specific indices for NFT metadata $nftKey: ${e.message}")
                     // Fallback: Iterate to find keys ending with specific metadata names if indices fail
                     for (i in 0 until nodes.length()) {
                         val node = nodes.getJSONObject(i)
                         val key = node.optString("key", "")
                         val value = node.optString("value", "")
                         if (key.endsWith(":name")) name = value
                         if (key.endsWith(":description")) description = value
                     }
                     android.util.Log.d("XianNetworkService", "Fallback Metadata for $nftKey - Name: $name, Desc: $description")
                 }
            } else {
                 android.util.Log.w("XianNetworkService", "Metadata nodes array is null or too short for $nftKey")
            }

            return@withContext Pair(name, description)
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error fetching NFT metadata for $nftKey: ${e.message}", e)
            return@withContext Pair(null, null)
        }
    }

    /**
     * Fetches all NFTs owned by the user, including their metadata.
     */
    suspend fun getNfts(publicKey: String): List<NftInfo> = withContext(Dispatchers.IO) {
        android.util.Log.d("XianNetworkService", "Starting NFT fetch for publicKey: $publicKey")
        val nftKeys = getOwnedNftKeys(publicKey)
        if (nftKeys.isEmpty()) {
            android.util.Log.d("XianNetworkService", "No NFT keys found for $publicKey")
            return@withContext emptyList()
        }
        android.util.Log.d("XianNetworkService", "Found ${nftKeys.size} NFT keys for $publicKey")

        val nftInfoList = mutableListOf<NftInfo>()
        for (key in nftKeys) {
             android.util.Log.d("XianNetworkService", "Fetching metadata for NFT key: $key")
            val (name, description) = getNftMetadata(key)
            if (name != null) { // Only add if we could get at least the name
                val nft = NftInfo(
                    contractAddress = key, // Using the key as the unique identifier/address
                    name = name,
                    description = description ?: "No description", // Provide default if null
                    // Construct URLs based on the web version's pattern
                    imageUrl = "https://pixelsnek.xian.org/gif/${key}.gif",
                    viewUrl = "https://pixelsnek.xian.org/frames/${key}"
                )
                nftInfoList.add(nft)
                android.util.Log.d("XianNetworkService", "Added NFT to list: ${nft.name}")
            } else {
                 android.util.Log.w("XianNetworkService", "Skipping NFT with key $key due to missing name in metadata.")
            }
        }
        android.util.Log.d("XianNetworkService", "Finished fetching NFTs. Total found: ${nftInfoList.size}")
        return@withContext nftInfoList
    }

    // --- End of new NFT functions ---

    
    /**
     * Broadcast a signed transaction to the Xian network
     * 
     * @param signedTransaction The signed transaction hex string to broadcast
     * @return A Pair containing: success status (Boolean) and transaction hash (String)
     */
    suspend fun broadcastTransaction(signedTransaction: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Abortando broadcast de transacción.")
                return@withContext Pair(false, "")
            }
            
            // Log the transaction length and first/last part for debugging
            android.util.Log.d("XianNetworkService", "Broadcasting transaction, length: ${signedTransaction.length}")
            if (signedTransaction.length > 100) {
                android.util.Log.d("XianNetworkService", "Transaction (truncated): ${signedTransaction.substring(0, 50)}...${signedTransaction.substring(signedTransaction.length - 50)}")
            } else {
                android.util.Log.d("XianNetworkService", "Transaction: $signedTransaction")
            }
            
            // CLAVE: Construir URL para broadcast_tx_sync exactamente como en la versión web
            // Las comillas dobles dentro de la URL son críticas pero pueden causar problemas
            // con okhttp si no se manejan correctamente
            
            // Creamos la URL base sin las comillas adicionales
            val baseUrl = "$rpcUrl/broadcast_tx_sync?tx="
            
            // Agregamos las comillas dobles alrededor de la transacción hex de forma manual
            val fullUrl = baseUrl + "\"" + signedTransaction + "\""
            
            android.util.Log.d("XianNetworkService", "Broadcasting to URL: $fullUrl")
            
            // Realizar la solicitud usando la URL completa, permitiendo que OkHttp
            // haga su propia codificación URL
            val request = Request.Builder()
                .url(fullUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Broadcast response: $responseBody")
            
            val json = JSONObject(responseBody)
            
            // Análisis mejorado de la respuesta
            if (json.has("error")) {
                // Hay un error explícito en la respuesta
                val errorJson = json.getJSONObject("error")
                val errorMsg = errorJson.optString("message", "Unknown error")
                android.util.Log.e("XianNetworkService", "Transaction error from node: $errorMsg")
                return@withContext Pair(false, "")
            } else if (json.has("result")) {
                // Hay un resultado - verificar el código
                val resultJson = json.getJSONObject("result")
                val hash = resultJson.optString("hash", "")
                val code = resultJson.optInt("code", -1)
                
                // Log detallado del resultado
                android.util.Log.d("XianNetworkService", "Transaction result - hash: $hash, code: $code")
                
                if (code == 0) {
                    // Código 0 indica éxito total
                    android.util.Log.d("XianNetworkService", "Transaction successful!")
                    return@withContext Pair(true, hash)
                } else {
                    // Cualquier otro código indica error
                    val log = resultJson.optString("log", "Unknown error")
                    android.util.Log.e("XianNetworkService", "Transaction failed with code $code: $log")
                    return@withContext Pair(false, hash)
                }
            } else {
                // No hay error ni resultado - algo inesperado ocurrió
                android.util.Log.e("XianNetworkService", "Unexpected response format: $responseBody")
                return@withContext Pair(false, "")
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error broadcasting transaction: ${e.message}", e)
            return@withContext Pair(false, "")
        }
    }
    
    /**
     * Get transaction results by hash
     * 
     * @param txHash The transaction hash to query
     * @return The transaction results as a JSON string
     */
    suspend fun getTransactionResults(txHash: String): String = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Abortando obtención de resultados de transacción.")
                return@withContext "{\"error\":\"No connection to node\"}"
            }
            
            // Construir URL para tx
            val url = "$rpcUrl/tx?hash=\"$txHash\""
            
            // Realizar la solicitud
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            
            return@withContext responseBody
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting transaction results: ${e.message}")
            return@withContext "{\"error\":\"${e.message}\"}"
        }
    }
    
    /**
     * Get the chain ID from the Xian network
     * 
     * @return The chain ID as a String
     */
    suspend fun getChainID(): String = withContext(Dispatchers.IO) {
        try {
            // Check if we already have the chain ID cached
            if (chainId != null) {
                return@withContext chainId!!
            }
            
            // Verify connectivity with the node before continuing
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Abortando obtención de chain ID.")
                return@withContext "xian"
            }
            
            // Build URL for genesis endpoint as in the web version
            val url = "$rpcUrl/genesis"
            
            // Make the request
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Genesis response: $responseBody")
            
            val json = JSONObject(responseBody)
            
            // Extract chain_id from genesis data
            chainId = json.optJSONObject("result")?.optJSONObject("genesis")?.optString("chain_id", "xian")
            android.util.Log.d("XianNetworkService", "Chain ID: $chainId")
            
            return@withContext chainId ?: "xian"
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting chain ID: ${e.message}")
            return@withContext "xian" // Default fallback value
        }
    }
    
    /**
     * Estima la cantidad de stamps necesarios para una transacción
     * Replica la funcionalidad de estimateStamps en la versión web
     */
    suspend fun estimateStamps(transaction: JSONObject): Int = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Abortando estimación de stamps.")
                return@withContext 200000 // Valor por defecto más alto (antes 100000)
            }
            
            // Assume 'transaction' is the SIGNED transaction JSON object
            // Serialize the SIGNED transaction to format JSON
            val serializedTransaction = transaction.toString()
            val transactionBytes = serializedTransaction.toByteArray(Charsets.UTF_8)
            val transactionHex = XianCrypto.getInstance().toHexString(transactionBytes)
            
            // Construir URL para calculate_stamps
            val baseUrl = "$rpcUrl/abci_query?path="
            val pathPart = "\"/calculate_stamps/$transactionHex\""
            val encodedPathPart = java.net.URLEncoder.encode(pathPart, "UTF-8")
            val finalPathPart = encodedPathPart.replace("+", "%20")
            val calculateUrl = baseUrl + finalPathPart
            
            android.util.Log.d("XianNetworkService", "Estimando stamps con URL: $calculateUrl")
            
            // Realizar la solicitud
            val response = client.newCall(
                Request.Builder()
                    .url(calculateUrl)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Respuesta de estimación: $responseBody")
            
            val json = JSONObject(responseBody)
            val value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (value != null && value != "AA==") {
                // Decodificar el valor Base64
                val decodedBytes = Base64.decode(value, Base64.DEFAULT)
                val decoded = String(decodedBytes)
                
                // Parsear el JSON de respuesta
                val resultJson = JSONObject(decoded)
                val stampsUsed = resultJson.optInt("stamps_used", 0)
                val success = resultJson.optInt("status", -1) == 0
                
                android.util.Log.d("XianNetworkService", "Stamps estimados (sin margen): $stampsUsed, éxito: $success")
                
                // Use the exact estimated value, like in the Prueb version
                // Remove the 30% margin
                android.util.Log.d("XianNetworkService", "Using exact estimated stamps: $stampsUsed")

                // Return the exact estimated value if successful and positive
                return@withContext if (success && stampsUsed > 0) stampsUsed else 200000 // Fallback if status is not 0 or stampsUsed is 0/negative
            } else {
                android.util.Log.e("XianNetworkService", "No se pudo estimar stamps, usando valor por defecto")
                return@withContext 200000 // Valor por defecto más alto (antes 100000)
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error estimando stamps: ${e.message}", e)
            return@withContext 200000 // Valor por defecto más alto (antes 100000)
        }
    }

    /**
     * Send a transaction to the Xian blockchain
     * Método simplificado que maneja todo el proceso de creación, firma y envío de transacciones
     * 
     * @param contract El contrato a llamar
     * @param method El método a invocar
     * @param kwargs Los argumentos para el método
     * @param privateKey La clave privada para firmar
     * @param stampLimit El límite de stamps (gas) para la transacción
     * @return TransactionResult con el resultado de la transacción
     */
    suspend fun sendTransaction(
        contract: String,
        method: String,
        kwargs: JSONObject,
        privateKey: ByteArray,
        stampLimit: Int = 0
    ): TransactionResult = withContext(Dispatchers.IO) {
        try {
            // PASO 1: Verificar conexión y obtener clave pública
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible")
                return@withContext TransactionResult(
                    txHash = "", 
                    success = false, 
                    errors = "No connection to node. Please check your internet connection."
                )
            }
            
            // Obtener la clave pública
            val keyPair = XianCrypto.getInstance().createKeyPairFromSeed(privateKey)
            val publicKey = XianCrypto.getInstance().toHexString(keyPair.second)
            android.util.Log.d("XianNetworkService", "Usando clave pública: $publicKey")
            
            // PASO 2: Verificar balance
            val balance = getTokenBalance("currency", publicKey)
            android.util.Log.d("XianNetworkService", "Saldo actual: $balance XIAN")
            
            // PASO 3: Obtener nonce y chain ID
            val nonce = getNonce(publicKey)
            val chainId = getChainID()
            android.util.Log.d("XianNetworkService", "Obtenido - nonce: $nonce, chainId: $chainId")
            
            // PASO 4: Construir payload de transacción para estimación
            // Usamos un valor inicial de stamps para la estimación
            val initialStampLimit = if (stampLimit > 0) stampLimit else 100000
            
            val estimationPayload = JSONObject().apply {
                put("chain_id", chainId)
                put("sender", publicKey)
                put("nonce", nonce)
                put("contract", contract)
                put("function", method)
                put("kwargs", kwargs)
                put("stamps_supplied", initialStampLimit)
            }
            
            // PASO 5: Ordenar las claves alfabéticamente para firma determinista
            val orderedEstimationPayload = sortJsonObject(estimationPayload)
            
            // PASO 6: Crear transacción para estimación
            val estimationTx = JSONObject().apply {
                put("payload", orderedEstimationPayload)
                put("metadata", JSONObject().apply {
                    put("signature", "")
                })
            }
            
            // PASO 7: Firmar la transacción de estimación
            val signedEstimationTx = signTransaction(estimationTx, privateKey)
            
            // PASO 8: Estimar los stamps necesarios
            // PASO 8: Estimar los stamps necesarios using the SIGNED transaction
            // Pass the signedEstimationTx directly to the modified estimateStamps function
            val estimatedStamps = estimateStamps(signedEstimationTx)
            android.util.Log.d("XianNetworkService", "Stamps estimados (con margen): $estimatedStamps")
            
            // Use the estimated stamps directly, applying a sensible minimum fallback
            // Remove complex checks and maximums for now, rely on the Prueb logic's simpler approach
            val finalStampLimit = if (estimatedStamps > 0) {
                estimatedStamps
            } else {
                android.util.Log.w("XianNetworkService", "Estimation failed or returned 0, using default 200000")
                200000 // Default if estimation failed
            }

            android.util.Log.d("XianNetworkService", "Using final stamp limit based on estimation/default: $finalStampLimit")
            
            // Estimación aproximada de XIAN requeridos basado en stamps
            val stampRate = getStampRate()
            android.util.Log.d("XianNetworkService", "Tasa de stamps obtenida: $stampRate stamps/XIAN")
            
            // Calcular el costo en XIAN con precisión de BigDecimal
            val stampsBigDecimal = BigDecimal.valueOf(finalStampLimit.toLong())
            val rateBigDecimal = BigDecimal.valueOf(stampRate.toDouble())
            
            // Cálculo correcto: stamps ÷ (stamps/XIAN) = XIAN
            // Verificar que la tasa no sea cero para evitar error de división
            val estimatedXianCostBD = if (rateBigDecimal.compareTo(BigDecimal.ZERO) > 0) {
                stampsBigDecimal.divide(rateBigDecimal, 8, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal("0.1") // Valor por defecto si hay error con la tasa
            }
            
            val estimatedXianCost = estimatedXianCostBD.toFloat()
            
            // Verificar que el costo estimado sea razonable
            val finalEstimatedCost = when {
                estimatedXianCost <= 0.0f -> 0.1f // Valor mínimo si el cálculo falla
                estimatedXianCost > 10.0f -> 
                    // Si la comisión es extraordinariamente alta, limitarla y advertir
                    if (contract == "currency" && method == "transfer") {
                        android.util.Log.w("XianNetworkService", "¡Comisión excesivamente alta! Reduciendo de $estimatedXianCost a 0.5 XIAN")
                        0.5f // Para transferencias normales, esto es más que suficiente
                    } else {
                        // Para otras operaciones más complejas, permitir hasta 5.0 XIAN
                        android.util.Log.w("XianNetworkService", "¡Comisión alta! Reduciendo de $estimatedXianCost a 5.0 XIAN")
                        5.0f
                    }
                else -> estimatedXianCost
            }
            
            android.util.Log.d("XianNetworkService", "Costo estimado original: $estimatedXianCost XIAN")
            android.util.Log.d("XianNetworkService", "Costo estimado FINAL: $finalEstimatedCost XIAN (stamps: $finalStampLimit, tasa: $stampRate)")
            
            // Aumentar el margen de tolerancia para evitar rechazos por balance bajo
            if (balance < finalEstimatedCost - 0.1f) {
                android.util.Log.e("XianNetworkService", "Saldo insuficiente: $balance XIAN, se requiere aproximadamente: $finalEstimatedCost XIAN")
                return@withContext TransactionResult(
                    txHash = "", 
                    success = false, 
                    errors = "Saldo insuficiente ($balance XIAN). Necesitas aproximadamente $finalEstimatedCost XIAN para cubrir las comisiones de esta transacción."
                )
            }
            
            // PASO 9: Construir payload de transacción final con los stamps estimados
            val txPayload = JSONObject().apply {
                put("chain_id", chainId)
                put("sender", publicKey)
                put("nonce", nonce)
                put("contract", contract)
                put("function", method)
                put("kwargs", kwargs)
                put("stamps_supplied", finalStampLimit)
            }
            
            // PASO 10: Ordenar las claves alfabéticamente para firma determinista
            val orderedPayload = sortJsonObject(txPayload)
            android.util.Log.d("XianNetworkService", "Payload ordenado: $orderedPayload")
            
            // PASO 11: Serializar y firmar
            val serializedPayload = orderedPayload.toString()
            val signature = XianCrypto.getInstance().signTransaction(
                serializedPayload, 
                privateKey, 
                publicKey
            )
            android.util.Log.d("XianNetworkService", "Firma generada: $signature")
            
            // PASO 12: Construir transacción completa
            val fullTx = JSONObject().apply {
                put("payload", orderedPayload)
                put("metadata", JSONObject().apply {
                    put("signature", signature)
                })
            }
            
            // PASO 13: Serializar a formato hexadecimal
            val txString = fullTx.toString()
            val txBytes = txString.toByteArray(Charsets.UTF_8)
            val txHex = XianCrypto.getInstance().toHexString(txBytes)
            android.util.Log.d("XianNetworkService", "Transacción hex (primeros 50 caracteres): ${txHex.take(50)}...")
            
            // PASO 14: Broadcast a la red
            val baseUrl = "$rpcUrl/broadcast_tx_sync?tx="
            val urlWithTx = baseUrl + "\"" + txHex + "\""
            android.util.Log.d("XianNetworkService", "URL de broadcast: $urlWithTx")
            
            val request = Request.Builder()
                .url(urlWithTx)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Respuesta de broadcast: $responseBody")
            
            // PASO 15: Analizar respuesta
            val responseJson = JSONObject(responseBody)
            
            // Verificar si hay un error explícito
            if (responseJson.has("error")) {
                val errorMsg = responseJson.getJSONObject("error").optString("message", "Unknown error")
                android.util.Log.e("XianNetworkService", "Error en respuesta: $errorMsg")
                return@withContext TransactionResult(
                    txHash = "", 
                    success = false, 
                    errors = errorMsg
                )
            }
            
            // Verificar si hay un resultado
            if (responseJson.has("result")) {
                val resultJson = responseJson.getJSONObject("result")
                val hash = resultJson.optString("hash", "")
                val code = resultJson.optInt("code", -1)
                
                android.util.Log.d("XianNetworkService", "Código de respuesta: $code, Hash: $hash")
                
                // Código 0 es éxito
                if (code == 0) {
                    return@withContext TransactionResult(
                        txHash = hash,
                        success = true
                    )
                } else {
                    // Cualquier otro código es error
                    val log = resultJson.optString("log", "Unknown error")
                    android.util.Log.e("XianNetworkService", "Error de transacción con código $code: $log")
                    
                    // Mejorar el mensaje de error para hacerlo más claro
                    val userFriendlyError = if (log.contains("too few stamps")) {
                        "Error de transacción: Stamps insuficientes. Por favor intenta nuevamente con una cantidad más pequeña o contacta al soporte."
                    } else {
                        "Error de transacción: $log"
                    }
                    
                    return@withContext TransactionResult(
                        txHash = hash,
                        success = false,
                        errors = userFriendlyError
                    )
                }
            }
            
            // Si no hay error ni resultado, algo raro pasó
            android.util.Log.e("XianNetworkService", "Respuesta inesperada: $responseBody")
            return@withContext TransactionResult(
                txHash = "",
                success = false,
                errors = "Unexpected response from node"
            )
            
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Excepción en sendTransaction: ${e.message}", e)
            return@withContext TransactionResult(
                txHash = "",
                success = false,
                errors = "Exception: ${e.message}"
            )
        }
    }
    
    /**
     * Obtiene la tasa de conversión de stamps a XIAN
     * @return La tasa de stamps por XIAN
     */
    suspend fun getStampRate(): Float = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No hay conexión con ningún nodo disponible. Usando tasa por defecto.")
                return@withContext 10000.0f // Valor por defecto más razonable (antes 20000.0f)
            }
            
            // Construir URL para obtener la tasa de stamps
            val url = "$rpcUrl/abci_query?path=\"/get/stamp_cost.S:value\""
            
            // Realizar la solicitud
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Respuesta de tasa de stamps: $responseBody")
            val json = JSONObject(responseBody)
            val value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (value != null && value != "AA==") {
                // Decodificar el valor Base64
                val decodedBytes = Base64.decode(value, Base64.DEFAULT)
                val decoded = String(decodedBytes)
                android.util.Log.d("XianNetworkService", "Valor de tasa de stamps decodificado: $decoded")
                
                try {
                    // Intentar convertir a doble precisión para evitar problemas con notación científica
                    val stampRateValue = decoded.toDoubleOrNull()
                    
                    if (stampRateValue != null) {
                        // La tasa es stamps por XIAN, así que debería ser un valor como 100,000
                        // Si es anormalmente bajo (como 0.0001), podría estar invertido
                        val stampRate = if (stampRateValue < 1.0) {
                            // Si es extremadamente bajo, asumir que está invertido (XIAN por stamp)
                            // y corregirlo (convertir a stamps por XIAN)
                            android.util.Log.d("XianNetworkService", "Corrigiendo tasa invertida: $stampRateValue")
                            (1.0 / stampRateValue).toFloat()
                        } else {
                            // Parece razonable, usar como está
                            stampRateValue.toFloat()
                        }
                        
                        // Verificar que la tasa esté en un rango razonable para evitar costos excesivos
                        val finalRate = when {
                            stampRate < 1000.0f -> 10000.0f // Demasiado bajo, usar valor por defecto
                            stampRate > 1000000.0f -> 100000.0f // Demasiado alto, limitar
                            else -> stampRate
                        }
                        
                        android.util.Log.d("XianNetworkService", "Tasa de stamps FINAL: $finalRate stamps/XIAN")
                        return@withContext finalRate
                    } else {
                        android.util.Log.e("XianNetworkService", "No se pudo convertir a número: $decoded")
                        return@withContext 10000.0f // Usar valor por defecto
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XianNetworkService", "Error procesando tasa de stamps: ${e.message}")
                    return@withContext 10000.0f // Valor por defecto en caso de error
                }
            } else {
                android.util.Log.e("XianNetworkService", "No se pudo obtener la tasa de stamps, usando valor por defecto")
                return@withContext 10000.0f // Más razonable (antes 20000.0f)
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error obteniendo tasa de stamps: ${e.message}", e)
            return@withContext 10000.0f // Más razonable (antes 20000.0f)
        }
    }
    
    /**
     * Función auxiliar para ordenar un JSONObject por sus claves
     */
    private fun sortJsonObject(jsonObj: JSONObject): JSONObject {
        val sorted = JSONObject()
        val keys = ArrayList<String>()
        
        val iterator = jsonObj.keys()
        while (iterator.hasNext()) {
            keys.add(iterator.next())
        }
        
        keys.sort()
        
        for (key in keys) {
            sorted.put(key, jsonObj.get(key))
        }
        
        return sorted
    }
    
    /**
     * Firma una transacción exactamente como se hace en la versión web
     * 
     * @param transaction El objeto de transacción sin firmar
     * @param privateKey La clave privada para firmar la transacción
     * @return El objeto de transacción firmado
     */
    suspend fun signTransaction(transaction: JSONObject, privateKey: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        try {
            // Obtener la clave pública desde el wallet manager
            val publicKey = walletManager.getPublicKey() ?: throw Exception("No se pudo obtener la clave pública")
            
            // Obtener el nonce
            val nonce = getNonce(publicKey)
            android.util.Log.d("XianNetworkService", "Nonce para la transacción: $nonce")
            
            // Clonar el payload para no modificar el original
            val payload = JSONObject(transaction.getJSONObject("payload").toString())
            
            // Agregar el nonce y el sender al payload
            payload.put("nonce", nonce)
            payload.put("sender", publicKey)
            
            android.util.Log.d("XianNetworkService", "Payload con nonce y sender: $payload")
            
            // Ordenar las claves en el payload (para generar una firma determinista)
            // CLAVE: Usar un enfoque más cuidadoso para ordenar las claves
            val orderedPayload = JSONObject()
            
            // Obtener todas las claves y ordenarlas alfabéticamente
            val keys = ArrayList<String>()
            val iterator = payload.keys()
            while (iterator.hasNext()) {
                keys.add(iterator.next())
            }
            keys.sort()
            
            // Construir el payload ordenado
            for (key in keys) {
                orderedPayload.put(key, payload.get(key))
            }
            
            android.util.Log.d("XianNetworkService", "Payload ordenado: $orderedPayload")
            
            // Convertir el payload ordenado a cadena
            val serializedTransaction = orderedPayload.toString()
            android.util.Log.d("XianNetworkService", "Payload serializado: $serializedTransaction")
            
            // Convertir a bytes usando UTF-8, igual que en la versión web
            val transactionBytes = serializedTransaction.toByteArray(Charsets.UTF_8)
            
            // Combinar la clave privada y la clave pública como en la versión web
            val combinedKey = ByteArray(64)
            System.arraycopy(privateKey, 0, combinedKey, 0, 32)
            System.arraycopy(XianCrypto.getInstance().fromHexString(publicKey), 0, combinedKey, 32, 32)
            
            // Usar Ed25519Signer con la clave combinada
            val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
            val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(combinedKey, 0)
            signer.init(true, privateKeyParams)
            signer.update(transactionBytes, 0, transactionBytes.size)
            val signature = signer.generateSignature()
            
            // Convertir firma a formato hexadecimal
            val signatureHex = XianCrypto.getInstance().toHexString(signature)
            android.util.Log.d("XianNetworkService", "Firma generada: $signatureHex")
            
            // Crear un nuevo objeto de transacción con el payload ordenado
            val signedTransaction = JSONObject()
            signedTransaction.put("payload", orderedPayload)
            signedTransaction.put("metadata", JSONObject().apply {
                put("signature", signatureHex)
            })
            
            android.util.Log.d("XianNetworkService", "Transacción firmada final: $signedTransaction")
            
            return@withContext signedTransaction
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error firmando transacción: ${e.message}", e)
            throw e
        }
    }
}