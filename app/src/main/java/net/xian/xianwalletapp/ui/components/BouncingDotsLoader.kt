package net.xian.xianwalletapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A custom loading indicator with 3 bouncing dots
 * @param modifier Modifier for the component
 * @param dotSize Size of each dot
 * @param dotColor Color of the dots
 * @param animationDuration Duration of the bounce animation in milliseconds
 */
@Composable
fun BouncingDotsLoader(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 600
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")
    
    // Create three separate animations with different delays
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1_scale"
    )
    
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = EaseInOutCubic,
                delayMillis = animationDuration / 3
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2_scale"
    )
    
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = EaseInOutCubic,
                delayMillis = (animationDuration * 2) / 3
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3_scale"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSize / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot 1
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(dot1Scale)
                .background(
                    color = dotColor,
                    shape = CircleShape
                )
        )
        
        // Dot 2
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(dot2Scale)
                .background(
                    color = dotColor,
                    shape = CircleShape
                )
        )
        
        // Dot 3
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(dot3Scale)
                .background(
                    color = dotColor,
                    shape = CircleShape
                )
        )
    }
}

/**
 * A smaller version of the bouncing dots loader for inline use
 */
@Composable
fun SmallBouncingDotsLoader(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary
) {
    BouncingDotsLoader(
        modifier = modifier,
        dotSize = 6.dp,
        dotColor = dotColor,
        animationDuration = 500
    )
}

/**
 * A large version of the bouncing dots loader for full-screen loading
 */
@Composable
fun LargeBouncingDotsLoader(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary
) {
    BouncingDotsLoader(
        modifier = modifier,
        dotSize = 12.dp,
        dotColor = dotColor,
        animationDuration = 700
    )
}