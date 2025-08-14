package net.xian.xianwalletapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import net.xian.xianwalletapp.ui.theme.XianDarkBackground
import net.xian.xianwalletapp.ui.theme.XianDarkSurface
import net.xian.xianwalletapp.ui.theme.XianPrimaryText
import net.xian.xianwalletapp.ui.theme.XianSecondaryText
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.ui.components.XianBottomNavBar
import net.xian.xianwalletapp.ui.components.StakeDialog
import net.xian.xianwalletapp.ui.components.UnstakeDialog
import net.xian.xianwalletapp.ui.components.ClaimRewardsDialog
import net.xian.xianwalletapp.ui.viewmodels.StakingViewModel
import net.xian.xianwalletapp.ui.viewmodels.StakingViewModelFactory
import net.xian.xianwalletapp.ui.viewmodels.FarmOneViewModel
import net.xian.xianwalletapp.ui.viewmodels.FarmOneViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakingScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    navigationViewModel: net.xian.xianwalletapp.ui.viewmodels.NavigationViewModel
) {
    // Initialize ViewModel
    val stakingViewModel: StakingViewModel = viewModel(
        factory = StakingViewModelFactory(networkService, walletManager)
    )
    val farmOneViewModel: FarmOneViewModel = viewModel(
        factory = FarmOneViewModelFactory(networkService, walletManager)
    )

    val uiState by stakingViewModel.uiState.collectAsStateWithLifecycle()
    val farmOneState by farmOneViewModel.uiState.collectAsStateWithLifecycle()
    
    var isManagePositionExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isManagePositionExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "ExpandIconRotation"
    )

    // Dialog states
    var showStakeDialog by remember { mutableStateOf(false) }
    var showUnstakeDialog by remember { mutableStateOf(false) }
    var showClaimDialog by remember { mutableStateOf(false) }

    // Get user's XIAN balance for staking
    var userBalance by remember { mutableStateOf(0.0) } // XIAN balance for main staking
    var userXwtBalance by remember { mutableStateOf(0.0) } // XWT balance for Farm 1
    
    LaunchedEffect(Unit) {
        navigationViewModel.syncSelectedItemWithRoute("staking")
        // Get user's XIAN balance
        val publicKey = walletManager.getPublicKey()
        if (publicKey != null) {
            userBalance = networkService.getTokenBalance("currency", publicKey).toDouble()
            userXwtBalance = networkService.getTokenBalance("con_xwt", publicKey).toDouble()
        }
    }

    // Clear messages after showing them
    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        if (uiState.errorMessage != null || uiState.successMessage != null) {
            kotlinx.coroutines.delay(2000) // Show message for 2 seconds
            stakingViewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Staking",
                            color = XianPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Hourglass loading indicator next to title
                        if (uiState.isLoading) {
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            var rotation by remember { mutableStateOf(0f) }
                            
                            // Continuous rotation while loading
                            LaunchedEffect(uiState.isLoading) {
                                while (uiState.isLoading) {
                                    rotation += 360f
                                    delay(1500) // Rotate every 1.5 seconds
                                }
                            }
                            
                            val animatedRotation by animateFloatAsState(
                                targetValue = rotation,
                                animationSpec = tween(
                                    durationMillis = 1500,
                                    easing = LinearEasing
                                ),
                                label = "HourglassRotation"
                            )
                            
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = "Loading",
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(animatedRotation),
                                tint = XianPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = XianPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            stakingViewModel.loadStakingInfo()
                            farmOneViewModel.loadFarmInfo()
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = XianPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            XianBottomNavBar(
                navController = navController,
                navigationViewModel = navigationViewModel
            )
        }
    ) { paddingValues ->
        val stakingScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(stakingScrollState)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {


            // Show error message
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Show success message
            uiState.successMessage?.let { success ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = success,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            // Staking Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = XianDarkSurface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = XianPrimary.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Header Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "XIAN Staking",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = XianPrimaryText
                        )
                        
                        // XIAN Token Logo
                        Image(
                            painter = painterResource(id = R.drawable.xian_logo),
                            contentDescription = "XIAN Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(XianPrimary.copy(alpha = 0.1f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Inside
                        )
                    }
                    
                    Text(
                        text = "Token: XIAN",
                        fontSize = 16.sp,
                        color = XianSecondaryText,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Divider
                    HorizontalDivider(
                        color = XianPrimary.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Stats Section
                    StakingStatRow(
                        label = "Annual Percentage Rate",
                        value = "${(uiState.stakingInfo.apr * 100).toInt()}%",
                        valueColor = XianPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StakingStatRow(
                        label = "Total Staked",
                        value = String.format("%.2f", uiState.stakingInfo.totalStaked),
                        valueColor = XianPrimaryText
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StakingStatRow(
                        label = "Lock Period",
                        value = uiState.stakingInfo.lockPeriod,
                        valueColor = XianPrimaryText
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Manage Your Position Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isManagePositionExpanded = !isManagePositionExpanded },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = XianDarkBackground
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = XianPrimary.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manage Your Position",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = XianPrimaryText
                            )
                            
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (isManagePositionExpanded) "Collapse" else "Expand",
                                tint = XianPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }
                    
                    // Expandable Content
                    AnimatedVisibility(visible = isManagePositionExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            // User Position Stats
                            StakingStatRow(
                                label = "Your Staked Amount",
                                value = String.format("%.6f", uiState.stakingInfo.userStaked),
                                valueColor = XianPrimary
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            StakingStatRow(
                                label = "Your Pending Rewards",
                                value = String.format("%.6f", uiState.stakingInfo.userRewards),
                                valueColor = XianPrimaryVariant
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Stake and Unstake Buttons Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Stake Button
                                Button(
                                    onClick = { showStakeDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = XianPrimary,
                                        contentColor = XianDarkBackground
                                    ),
                                    enabled = !uiState.isStaking
                                ) {
                                    if (uiState.isStaking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = XianDarkBackground
                                        )
                                    } else {
                                        Text(
                                            text = "Stake",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Unstake Button
                                OutlinedButton(
                                    onClick = { showUnstakeDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(2.dp, XianPrimary),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = XianPrimary
                                    ),
                                    enabled = !uiState.isUnstaking && uiState.stakingInfo.userStaked > 0
                                ) {
                                    if (uiState.isUnstaking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = XianPrimary
                                        )
                                    } else {
                                        Text(
                                            text = "Unstake",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            // Claim Rewards Button
                            OutlinedButton(
                                onClick = { showClaimDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, XianPrimaryVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = XianPrimaryVariant
                                ),
                                enabled = !uiState.isClaimingRewards && uiState.stakingInfo.userRewards > 0
                            ) {
                                if (uiState.isClaimingRewards) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = XianPrimaryVariant
                                    )
                                } else {
                                    Text(
                                        text = "Claim Rewards",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Farm ID 1 (con_multi_farmv1) Info Card (solo lectura)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = XianDarkSurface),
                border = BorderStroke(1.dp, XianPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)) {
                    // Header similar to XIAN card with logo on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "XWT Rewards",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = XianPrimaryText
                        )

                        Image(
                            painter = painterResource(id = R.drawable.xwtlogo),
                            contentDescription = "XWT Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(XianPrimary.copy(alpha = 0.1f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Inside
                        )
                    }

                    // Token label
                    Text(
                        text = "Token: XWT",
                        fontSize = 16.sp,
                        color = XianSecondaryText,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Divider
                    HorizontalDivider(
                        color = XianPrimary.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    val farmInfo = farmOneState.info

                    farmOneState.error?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Main farm stats (removed progress bar when loading per request)
                    // Calculate APR: budget / total_staked * 100 (annualized, days fixed at 365 per requerimiento)
                    val aprValue = remember(farmInfo.budget, farmInfo.totalStaked) {
                        val totalStakedVal = farmInfo.totalStaked
                        val budgetVal = farmInfo.budget
                        if (totalStakedVal > 0 && budgetVal > 0) {
                            (budgetVal / totalStakedVal) * 100.0
                        } else null
                    }
                    StakingStatRow(
                        label = "Annual Percentage Rate",
                        value = aprValue?.let { String.format("%.2f%%", it) } ?: "--"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StakingStatRow(
                        label = "Total Staked",
                        value = String.format("%.4f", farmInfo.totalStaked)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StakingStatRow(
                        label = "Duration (days)",
                        value = (farmInfo.durationDays ?: 0).toString()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección desplegable similar a "Manage Your Position"
                    var isFarmOnePositionExpanded by remember { mutableStateOf(false) }
                    val farmOneRotation by animateFloatAsState(targetValue = if (isFarmOnePositionExpanded) 180f else 0f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFarmOnePositionExpanded = !isFarmOnePositionExpanded },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = XianDarkBackground),
                        border = BorderStroke(1.dp, XianPrimary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manage Your Position",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = XianPrimaryText
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (isFarmOnePositionExpanded) "Collapse" else "Expand",
                                tint = XianPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(farmOneRotation)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isFarmOnePositionExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            StakingStatRow(label = "Staked", value = String.format("%.6f", farmInfo.userTotalDeposits), valueColor = XianPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            StakingStatRow(label = "Total Rewards", value = String.format("%.6f", farmInfo.userTotalRewards), valueColor = XianPrimaryVariant)
                            Spacer(modifier = Modifier.height(20.dp))

                            // Buttons row (Stake / Unstake)
                            var showFarm1StakeDialog by remember { mutableStateOf(false) }
                            var showFarm1UnstakeDialog by remember { mutableStateOf(false) }
                            var showFarm1ClaimDialog by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showFarm1StakeDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = XianPrimary,
                                        contentColor = XianDarkBackground
                                    ),
                                    enabled = !farmOneState.isLoading && !farmOneState.isDepositing
                                ) {
                                    if (farmOneState.isDepositing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = XianDarkBackground
                                        )
                                    } else {
                                        Text("Stake", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showFarm1UnstakeDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(2.dp, XianPrimary),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = XianPrimary),
                                    enabled = !farmOneState.isWithdrawing && farmInfo.userStaked > 0
                                ) {
                                    if (farmOneState.isWithdrawing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = XianPrimary
                                        )
                                    } else {
                                        Text("Unstake", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showFarm1ClaimDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(2.dp, XianPrimaryVariant),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = XianPrimaryVariant),
                                enabled = !farmOneState.isClaiming && farmInfo.userPendingRewards > 0
                            ) {
                                if (farmOneState.isClaiming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = XianPrimaryVariant
                                    )
                                } else {
                                    Text("Claim Rewards", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Simple dialogs reuse existing StakeDialog / UnstakeDialog / ClaimRewardsDialog but pointing to farmOneViewModel
                            if (showFarm1StakeDialog) {
                                // Reuse StakeDialog but pass XWT balance; visual title still says Stake XIAN internally.
                                // TODO: Refactor generic dialog to accept token symbol. For now we inform user in available line.
                                StakeDialog(
                                    isVisible = true,
                                    maxAmount = userXwtBalance,
                                    isLoading = farmOneState.isDepositing,
                                    needsPassword = walletManager.getUnlockedPrivateKey() == null,
                                    onDismiss = { showFarm1StakeDialog = false },
                                    onConfirm = { amount, password ->
                                        val amt = amount.toDoubleOrNull() ?: 0.0
                                        farmOneViewModel.approveAndDeposit(amt, password)
                                        showFarm1StakeDialog = false
                                    },
                                    tokenSymbol = "XWT"
                                )
                            }
                            if (showFarm1UnstakeDialog) {
                                UnstakeDialog(
                                    isVisible = true,
                                    maxAmount = farmInfo.userStaked,
                                    isLoading = farmOneState.isWithdrawing,
                                    needsPassword = walletManager.getUnlockedPrivateKey() == null,
                                    onDismiss = { showFarm1UnstakeDialog = false },
                                    onConfirm = { amount, password ->
                                        val amt = amount.toDoubleOrNull() ?: 0.0
                                        farmOneViewModel.withdraw(amt, password)
                                        showFarm1UnstakeDialog = false
                                    },
                                    tokenSymbol = "XWT"
                                )
                            }
                            if (showFarm1ClaimDialog) {
                                ClaimRewardsDialog(
                                    isVisible = true,
                                    rewardsAmount = farmInfo.userPendingRewards,
                                    isLoading = farmOneState.isClaiming,
                                    needsPassword = walletManager.getUnlockedPrivateKey() == null,
                                    onDismiss = { showFarm1ClaimDialog = false },
                                    onConfirm = { password ->
                                        farmOneViewModel.claimRewards(password)
                                        showFarm1ClaimDialog = false
                                    },
                                    tokenSymbol = "XWT"
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Dialogs
        StakeDialog(
            isVisible = showStakeDialog,
            maxAmount = userBalance,
            isLoading = uiState.isStaking,
            needsPassword = walletManager.getUnlockedPrivateKey() == null,
            onDismiss = { showStakeDialog = false },
            onConfirm = { amount, password ->
                stakingViewModel.stake(amount, password)
                showStakeDialog = false
            }
        )
        
        UnstakeDialog(
            isVisible = showUnstakeDialog,
            maxAmount = uiState.stakingInfo.userStaked,
            isLoading = uiState.isUnstaking,
            needsPassword = walletManager.getUnlockedPrivateKey() == null,
            onDismiss = { showUnstakeDialog = false },
            onConfirm = { amount, password ->
                stakingViewModel.unstake(amount, password)
                showUnstakeDialog = false
            }
        )
        
        ClaimRewardsDialog(
            isVisible = showClaimDialog,
            rewardsAmount = uiState.stakingInfo.userRewards,
            isLoading = uiState.isClaimingRewards,
            needsPassword = walletManager.getUnlockedPrivateKey() == null,
            onDismiss = { showClaimDialog = false },
            onConfirm = { password ->
                stakingViewModel.claimRewards(password)
                showClaimDialog = false
            },
            tokenSymbol = "XIAN"
        )
    }
}

@Composable
private fun StakingStatRow(
    label: String,
    value: String,
    valueColor: Color = XianPrimaryText
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = XianSecondaryText,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}