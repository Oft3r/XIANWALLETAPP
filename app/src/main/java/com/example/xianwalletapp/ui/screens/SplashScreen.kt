package com.example.xianwalletapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xianwalletapp.R // Import R class

/**
 * Splash screen for the Xian Wallet app
 * Displays the app name with a fade-in animation
 */
@Composable
fun SplashScreen() {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    
    LaunchedEffect(key1 = true) {
        startAnimation = true
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black // Change background to black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Image
            Image(
                painter = painterResource(id = R.drawable.xw), // Use the XW image
                contentDescription = "Xian Logo",
                modifier = Modifier
                    .size(350.dp) // Increased size
                    .padding(bottom = 0.dp) // Removed bottom padding
            )

            // Wallet Text removed
            
            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.White, // Set indicator color to white
                strokeWidth = 4.dp
            )
        }
    }
}