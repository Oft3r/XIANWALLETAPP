package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.ui.theme.XIANWALLETAPPTheme
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors

/**
 * Welcome screen that allows users to create a new wallet or import an existing one
 */
@Composable
fun WelcomeScreen(navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.xwallet),
                contentDescription = "Xian Logo",
                modifier = Modifier
                    .size(130.dp)
                    .padding(bottom = 0.5.dp)
                    .clip(CircleShape)
                    .border(width = 8.dp, color = MaterialTheme.colorScheme.inverseOnSurface, shape = CircleShape)
            )
            
            // Title
            Text(
                text = "XIAN WALLET",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Subtitle
            Text(
                text = "Secure and easy-to-use wallet for Xian Network",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // Create Wallet Button
            Button(
                onClick = { navController.navigate(XianDestinations.CREATE_WALLET) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = xianButtonColors(XianButtonType.PRIMARY)
            ) {
                Text("Create New Wallet", fontSize = 16.sp, color = Color.Black)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Import Wallet Button
            Button(
                onClick = { navController.navigate(XianDestinations.IMPORT_WALLET) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = xianButtonColors(XianButtonType.SECONDARY)
            ) {
                Text("Import Existing Wallet", fontSize = 16.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    XIANWALLETAPPTheme {
        WelcomeScreen(navController = rememberNavController())
    }
}