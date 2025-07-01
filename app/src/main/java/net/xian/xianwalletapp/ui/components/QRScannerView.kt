package net.xian.xianwalletapp.ui.components

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun QRScannerView(
    onQRCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Create camera executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Setup barcode scanner
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .align(Alignment.Center),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scan QR Code",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                    
                    // Camera Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    previewView = this
                                    setupCamera(ctx, lifecycleOwner, this, barcodeScanner, cameraExecutor) { qrCode ->
                                        if (!isProcessing) {
                                            isProcessing = true
                                            onQRCodeScanned(qrCode)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Scanning overlay
                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Black.copy(alpha = 0.7f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Processing...",
                                        color = Color.White,
                                        modifier = Modifier.padding(16.dp),
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    // Instruction text
                    Text(
                        text = "Position the QR code within the camera view",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    cameraExecutor: ExecutorService,
    onQRCodeScanned: (String) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        
        // Preview use case
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        
        // Image analysis use case for QR scanning
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy, barcodeScanner, onQRCodeScanned)
                }
            }
        
        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            // Handle camera binding failure
        }
    }, ContextCompat.getMainExecutor(context))
}

@androidx.camera.core.ExperimentalGetImage
private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onQRCodeScanned: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { qrCode ->
                        onQRCodeScanned(qrCode)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
