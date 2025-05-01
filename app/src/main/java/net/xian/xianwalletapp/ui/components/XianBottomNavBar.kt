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
import net.xian.xianwalletapp.ui.theme.XianBlue
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

    // Apply windowInsetsPadding directly and remove fixed height
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // .height(64.dp) // REMOVED fixed height
            .windowInsetsPadding(WindowInsets.navigationBars) // ADDED window insets padding
            .shadow(elevation = 8.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // .fillMaxHeight() // Might not be needed if Surface has no fixed height
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
                            label = "Portafolio",
                            selected = selectedItem == 0,
                            onClick = { 
                                navigationViewModel.setSelectedNavItem(0)
                                navController.navigate(XianDestinations.WALLET) {
                                    // Prevent multiple copies of the destination on the backstack
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
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
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            }
                        )
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
        targetValue = if (selected) 1.08f else 1.0f, // Reduced scale factor
        label = "scale"
    )
    
    // Ensure visibility by making the label more prominent when selected
    val textStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    
    // Define colors from the theme for gradient
    val yellowColor = MaterialTheme.colorScheme.primary
    val blueColor = XianBlue
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 8.dp) // Reduced padding
            .scale(animatedScale)
            .animateContentSize()
    ) {        Box(
            modifier = Modifier
                .size(40.dp) // Reduced size from 48dp to 40dp
                .clip(CircleShape)
                .then(
                    if (selected) 
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Yellow, blueColor),
                                start = Offset(0f, 0f),
                                end = Offset(100f, 100f)
                            )
                        )
                    else 
                        Modifier.background(Color.Transparent)
                )
                .border(
                    width = if (selected) 0.dp else 1.dp,
                    color = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                // Add shadow when selected for more emphasis
                .then(if (selected) Modifier.shadow(2.dp, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp) // Reduced icon size from 24dp to 20dp
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp)) // Reduced spacing
        
        Text(
            text = label,
            fontSize = 11.sp, // Reduced font size from 12sp to 11sp
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) XianBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
