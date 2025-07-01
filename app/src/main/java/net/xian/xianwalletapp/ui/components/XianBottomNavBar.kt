package net.xian.xianwalletapp.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Work // Icono de maleta para Portafolio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModel

/**
 * Shared bottom navigation bar for use in all main screens
 */
@Composable
fun XianBottomNavBar(
    navController: NavController,
    navigationViewModel: NavigationViewModel,
    modifier: Modifier = Modifier
    ) {
    val selectedItem by navigationViewModel.selectedNavItem.collectAsStateWithLifecycle()
    // Apply centering using padding with proper alignment
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Make the bar 95% of screen width
                .windowInsetsPadding(WindowInsets.navigationBars)
                .shadow(elevation = 8.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            // Apply less rounded corners for a more rectangular look
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // .fillMaxHeight() // Might not be needed if Surface has no fixed height
                    .clip(RoundedCornerShape(12.dp)) // Less rounded corners
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp), // Keep internal padding
                    // .navigationBarsPadding() // REMOVED: Let Scaffold handle bottom bar positioning
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Portfolio Item (Wallet)
                    CustomNavItem(
                        icon = Icons.Default.Work,
                        label = "Portfolio",
                        selected = selectedItem == 0,
                        onClick = { 
                            navigationViewModel.setSelectedNavItem(0)
                            navController.navigate(XianDestinations.WALLET) {
                                // Prevent multiple copies of the destination on the backstack
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = false // Changed from true to false
                                }
                                // Restore state when returning to this destination
                                restoreState = true
                                // Avoid duplicate destinations
                                launchSingleTop = true
                            }
                        }
                    )
                    // Web Browser Item
                    CustomNavItem(
                        icon = Icons.Default.Language,
                        label = "Browser",
                        selected = selectedItem == 1,
                        onClick = { 
                            navigationViewModel.setSelectedNavItem(1)
                            navController.navigate(XianDestinations.WEB_BROWSER) {
                                popUpTo(navController.graph.startDestinationId) { saveState = false } // Changed from true to false
                                restoreState = true
                                launchSingleTop = true
                            }
                        }
                    )
                    // Advanced Item (Build)
                    CustomNavItem(
                        icon = Icons.Default.Build,
                        label = "Advanced",
                        selected = selectedItem == 2,
                        onClick = { 
                            navigationViewModel.setSelectedNavItem(2)
                            navController.navigate(XianDestinations.ADVANCED) {
                                popUpTo(navController.graph.startDestinationId) { saveState = false } // Changed from true to false
                                restoreState = true
                                launchSingleTop = true
                            }
                        }
                    )
                    // News Item
                    CustomNavItem(
                        icon = Icons.Default.Newspaper,
                        label = "News",
                        selected = selectedItem == 3,
                        onClick = { 
                            navigationViewModel.setSelectedNavItem(3)
                            navController.navigate(XianDestinations.NEWS) {
                                popUpTo(navController.graph.startDestinationId) { saveState = false } // Changed from true to false
                                restoreState = true
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Custom navigation item with enhanced visual styling
 */
@Composable
private fun CustomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f, // Slightly increased scale for better visual feedback
        label = "scale"
    )
    
    // Define colors from the theme for gradient
    val primaryColor = XianPrimary
    val secondaryColor = XianPrimaryVariant
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp) // Reduced padding for smaller button
            .scale(animatedScale)
            .animateContentSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp) // Smaller rectangular button
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (selected)
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(primaryColor, secondaryColor),
                                start = Offset(0f, 0f),
                                end = Offset(100f, 100f)
                            )
                        )
                    else
                        Modifier.background(Color.Transparent)
                )
                // Add shadow when selected for more emphasis
                .then(if (selected) Modifier.shadow(4.dp, RoundedCornerShape(8.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp) // Standard icon size
            )
        }
    }
}
