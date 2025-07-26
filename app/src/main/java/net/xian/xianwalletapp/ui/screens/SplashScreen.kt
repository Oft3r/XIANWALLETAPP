package net.xian.xianwalletapp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.ui.components.LargeBouncingDotsLoader

/**
 * Splash screen for the Xian Wallet app
 * Displays the app name with a fade-in animation
 */
@Composable
fun SplashScreen() {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Fade in animation
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "alpha"
    )
    
    // 3D rotation animation for the coin
    val infiniteTransition = rememberInfiniteTransition(label = "coin_rotation")
    val coinRotationY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_y"
    )
    
    // Slight vertical floating animation
    val floatingAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )
    
    LaunchedEffect(key1 = true) {
        startAnimation = true
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D Coin with Front Face, Back Face, and Edge Ring
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(y = (-floatingAnimation).dp)
                    .graphicsLayer {
                        rotationY = coinRotationY
                        cameraDistance = 8f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    }
            ) {
                // Back face of coin (visible when rotated)
                Image(
                    painter = painterResource(id = R.drawable.xwallet),
                    contentDescription = "Back of Coin",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            rotationY = 180f // Flipped to show as back face
                            alpha = if (coinRotationY % 360 > 90 && coinRotationY % 360 < 270) 1f else 0f
                        }
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            color = Color(0xFF404040),
                            shape = CircleShape
                        )
                )
                
                // 3D Edge Ring (visible from the side)
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            alpha = kotlin.math.abs(kotlin.math.sin(Math.toRadians((coinRotationY % 360).toDouble()))).toFloat() * 0.8f + 0.2f
                        }
                        .border(
                            width = 12.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF505050), // Dark gray highlight
                                    Color(0xFF202020), // Very dark shadow
                                    Color(0xFF404040), // Dark gray
                                    Color(0xFF101010), // Almost black shadow
                                    Color(0xFF505050)  // Dark gray highlight
                                )
                            ),
                            shape = CircleShape
                        )
                        .background(Color.Transparent, CircleShape)
                )
                
                // Front face of coin
                Image(
                    painter = painterResource(id = R.drawable.xwallet),
                    contentDescription = "Front of Coin",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            alpha = if (coinRotationY % 360 <= 90 || coinRotationY % 360 >= 270) 1f else 0f
                        }
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            color = Color(0xFF404040),
                            shape = CircleShape
                        )
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.5f),
                            spotColor = Color.Black.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}