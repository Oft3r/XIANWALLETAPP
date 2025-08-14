package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel

// Data class to represent available backgrounds
data class CardBackground(
    val name: String,
    val displayName: String,
    val resourceId: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardBackgroundSelectorScreen(
    navController: NavController,
    viewModel: WalletViewModel
) {
    val context = LocalContext.current
    val selectedBackground by viewModel.selectedCardBackground.collectAsStateWithLifecycle()
    
    // Define available backgrounds - only wallpapers with consecutive numbers
    val availableBackgrounds = remember {
        listOf(
            CardBackground("none", "Default", 0), // No background option
            CardBackground("dark", "Dark", -1), // Dark background option (special case)
            CardBackground("wallpaper1", "Wallpaper 1", R.drawable.wallpaper1),
            CardBackground("wallpaper2", "Wallpaper 2", R.drawable.wallpaper2),
            CardBackground("wallpaper3", "Wallpaper 3", R.drawable.wallpaper3),
            CardBackground("wallpaper4", "Wallpaper 4", R.drawable.wallpaper4),
            CardBackground("wallpaper5", "Wallpaper 5", R.drawable.wallpaper5),
            CardBackground("wallpaper6", "Wallpaper 6", R.drawable.wallpaper6),
            CardBackground("wallpaper7", "Wallpaper 7", R.drawable.wallpaper7),
            CardBackground("wallpaper8", "Wallpaper 8", R.drawable.wallpaper8)
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Select Card Background",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(availableBackgrounds) { background ->
                BackgroundItem(
                    background = background,
                    isSelected = selectedBackground == background.name,
                    onSelect = {
                        viewModel.setSelectedCardBackground(
                            if (background.name == "none") null else background.name
                        )
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}

@Composable
private fun BackgroundItem(
    background: CardBackground,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) XianPrimary else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                background.resourceId > 0 -> {
                    // Show image background
                    Image(
                        painter = painterResource(id = background.resourceId),
                        contentDescription = background.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                background.resourceId == -1 -> {
                    // Show dark background option
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = Color(0xFF1A1A1A), // Dark color matching wallet theme
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dark",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> {
                    // Show default/no background option
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Default",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = XianPrimary,
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Background name overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = background.displayName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
