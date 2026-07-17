package com.example.ui

import android.content.Intent
import kotlin.random.Random
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatMessage
import com.example.data.Load
import com.example.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun getHighContrastTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color(0xFF44474E),
    focusedPlaceholderColor = Color.Gray,
    unfocusedPlaceholderColor = Color.Gray,
    focusedBorderColor = Color(0xFF005AC1),
    unfocusedBorderColor = Color(0xFF74777F)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KgiApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allLoads by viewModel.allLoads.collectAsStateWithLifecycle()
    val trackingProgress by viewModel.loadTrackingProgress.collectAsStateWithLifecycle()
    val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val currentScreen = viewModel.currentScreen
    val lang = viewModel.language

    // State for payment/commission blocking dialog
    var showCommissionDialog by remember { mutableStateOf(false) }
    var commissionAmountToPay by remember { mutableStateOf(0.0) }
    var activeLoadIdForCommission by remember { mutableStateOf<Int?>(null) }
    var activeDriverIdForCommission by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = Localization.get("app_title", lang),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = com.example.ui.theme.Color005AC1,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("app_logo_text")
                            )
                            Text(
                                text = "अब ट्रांसपोर्ट नहीं रुकेगा",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = com.example.ui.theme.Color74777F
                            )
                        }
                    },
                    actions = {
                        // Multi-language Toggle Button
                        IconButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.testTag("language_toggle")
                        ) {
                            Text(
                                text = if (lang == "en") "हिं" else "EN",
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Color005AC1,
                                fontSize = 14.sp
                            )
                        }

                        if (currentUser != null) {
                            IconButton(
                                onClick = { viewModel.logout() },
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = Localization.get("nav_logout", lang),
                                    tint = com.example.ui.theme.Color1B1B1F
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    )
                )
                HorizontalDivider(color = com.example.ui.theme.ColorE0E2EC, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "landing" -> PremiumLandingScreen(viewModel = viewModel, onNavigate = { viewModel.currentScreen = it })
                "login_driver" -> LoginScreen(viewModel = viewModel, role = "DRIVER")
                "signup_driver" -> SignupDriverScreen(viewModel = viewModel)
                "login_shipper" -> LoginScreen(viewModel = viewModel, role = "SHIPPER")
                "signup_shipper" -> SignupShipperScreen(viewModel = viewModel)
                "driver_home" -> DriverHomeScreen(
                    viewModel = viewModel,
                    allLoads = allLoads,
                    lang = lang
                )
                "shipper_home" -> ShipperHomeScreen(
                    viewModel = viewModel,
                    allLoads = allLoads,
                    lang = lang,
                    trackingProgress = trackingProgress,
                    onBlockCommission = { loadId, driverId, amount ->
                        activeLoadIdForCommission = loadId
                        activeDriverIdForCommission = driverId
                        commissionAmountToPay = amount
                        showCommissionDialog = true
                    }
                )
            }

            // Commission & UPI QR Payment Dialog
            if (showCommissionDialog) {
                CommissionPaymentDialog(
                    viewModel = viewModel,
                    amount = commissionAmountToPay,
                    onDismiss = { showCommissionDialog = false },
                    onPaymentConfirmed = {
                        showCommissionDialog = false
                        val loadId = activeLoadIdForCommission
                        val driverId = activeDriverIdForCommission
                        if (loadId != null && driverId != null) {
                            viewModel.payCommission(loadId)
                            viewModel.acceptDriverForLoad(
                                loadId = loadId,
                                driverId = driverId,
                                onBlock = {},
                                onSuccess = {
                                    Toast.makeText(context, "Driver Accepted Successfully!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Landing Screen
// -------------------------------------------------------------
@Composable
fun PremiumLandingScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val lang = viewModel.language

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(com.example.ui.theme.ColorFDFBFF)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Section - Bold Typography Title & Tagline
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KGI DIESELS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = com.example.ui.theme.Color005AC1,
                modifier = Modifier.testTag("hero_brand_badge")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get("hindi_tagline", lang),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = com.example.ui.theme.Color74777F
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Display Headline
            Text(
                text = "DISTRIBUTION",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color1B1B1F,
                lineHeight = 40.sp,
                modifier = Modifier.testTag("hero_title")
            )
            Text(
                text = "REDEFINED.",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color005AC1,
                lineHeight = 40.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting India's commercial freight network.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = com.example.ui.theme.Color44474E,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Gorgeous Flashing 0% Commission Promo Banner
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("promo_commission_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Warm soft amber background
            border = BorderStroke(2.dp, Color(0xFFF97316)) // Orange border
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF97316), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Offer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "0% COMMISSION AD",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFC2410C),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "0% Commission On Job providing And Driving hiring.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Beautiful Interactive Highway / Truck Art Illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Draw schematic dynamic highway
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pathColor = Color.White.copy(alpha = 0.15f)
                val dashColor = Color(0xFFFBBF24)
                
                // Highway curves
                drawLine(pathColor, Offset(0f, size.height * 0.7f), Offset(size.width, size.height * 0.7f), strokeWidth = 4f)
                drawLine(pathColor, Offset(0f, size.height * 0.9f), Offset(size.width, size.height * 0.9f), strokeWidth = 4f)
                
                // Dash lines
                val dashWidth = 20f
                val gapWidth = 20f
                var x = 0f
                while (x < size.width) {
                    drawLine(dashColor, Offset(x, size.height * 0.8f), Offset(x + dashWidth, size.height * 0.8f), strokeWidth = 3f)
                    x += dashWidth + gapWidth
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No commission before 3rd trip!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Fast matching • Verified Shippers & Drivers",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Entrance / Role Selection Buttons
        // -------------------------------------------------------------
        Text(
            text = "CHOOSE YOUR PANEL TO ENTER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = com.example.ui.theme.Color74777F,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DRIVER PANEL
            Button(
                onClick = { onNavigate("login_driver") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("driver_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "DRIVER / TRANSPORTER PANEL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Get loads, accept cargo, view assistance",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // SHIPPER PANEL
            Button(
                onClick = { onNavigate("login_shipper") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("shipper_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color1B1B1F)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "SHIPPER / SENDER PANEL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Post load/parcel, find trucks, calculate fares",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LandingScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {}

@Composable
fun OldLandingScreenDisabled(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val lang = viewModel.language
    var dropdownExpanded by remember { mutableStateOf(false) }
    var truckSize by remember { mutableStateOf("LCV (3 Ton)") }
    var rateKmText by remember { mutableStateOf("12") }
    var distanceText by remember { mutableStateOf("100") }
    var weightText by remember { mutableStateOf("3") }
    val defaultRates = mapOf("LCV (3 Ton)" to 12)
    val truckSizes = listOf("LCV (3 Ton)")
    val distanceCost = 0.0
    val weightCost = 0.0
    val totalFare = 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(com.example.ui.theme.ColorFDFBFF)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Section - Bold Typography Title & Tagline
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KGI DIESELS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = com.example.ui.theme.Color005AC1,
                modifier = Modifier.testTag("hero_brand_badge")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get("hindi_tagline", lang),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = com.example.ui.theme.Color74777F
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Display Headline
            Text(
                text = "DISTRIBUTION",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color1B1B1F,
                lineHeight = 40.sp,
                modifier = Modifier.testTag("hero_title")
            )
            Text(
                text = "REDEFINED.",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color005AC1,
                lineHeight = 40.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting India's commercial freight network.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = com.example.ui.theme.Color44474E,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Gorgeous Flashing 0% Commission Promo Banner
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("promo_commission_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Warm soft amber background
            border = BorderStroke(2.dp, Color(0xFFF97316)) // Orange border
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF97316), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Offer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "0% COMMISSION AD",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFC2410C),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "0% Commission On Job providing And Driving hiring.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // -------------------------------------------------------------
        // Fare Calculator Card Section
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("landing_calculator_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Calculator Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fare Calculator",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color1B1B1F
                    )
                    
                    // Live Rates Badge
                    Box(
                        modifier = Modifier
                            .background(com.example.ui.theme.Color005AC1, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LIVE RATES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 1. Truck Size Selection Box with Overlapping Label
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(truckSize, fontSize = 14.sp, color = com.example.ui.theme.Color1B1B1F)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = com.example.ui.theme.Color1B1B1F)
                        }
                    }
                    // Overlapping label
                    Text(
                        text = "TRUCK SIZE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color44474E,
                        modifier = Modifier
                            .offset(x = 12.dp, y = (-6).dp)
                            .background(com.example.ui.theme.ColorE0E2EC)
                            .padding(horizontal = 4.dp)
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        truckSizes.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size) },
                                onClick = {
                                    truckSize = size
                                    rateKmText = defaultRates[size]?.toString() ?: "22"
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Distance and Weight 2-Column Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Distance Input
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = distanceText,
                            onValueChange = { distanceText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = com.example.ui.theme.Color1B1B1F),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                        Text(
                            text = "DISTANCE (KM)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.Color44474E,
                            modifier = Modifier
                                .offset(x = 12.dp, y = (-6).dp)
                                .background(com.example.ui.theme.ColorE0E2EC)
                                .padding(horizontal = 4.dp)
                        )
                    }

                    // Weight Input
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = com.example.ui.theme.Color1B1B1F),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                        Text(
                            text = "WEIGHT (TONS)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.Color44474E,
                            modifier = Modifier
                                .offset(x = 12.dp, y = (-6).dp)
                                .background(com.example.ui.theme.ColorE0E2EC)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Dynamic Calculation Card with dashed divider
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "Base Rate (₹${rateKmText}/km)",
                                fontSize = 12.sp,
                                color = com.example.ui.theme.Color44474E
                            )
                            Text(
                                text = "₹${"%,.0f".format(distanceCost)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Color1B1B1F
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom dashed divider
                        Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                            drawLine(
                                color = com.example.ui.theme.ColorC4C6CF,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ESTIMATED TOTAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Color44474E
                            )
                            Text(
                                text = "₹${"%,.0f".format(totalFare)}*",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = com.example.ui.theme.Color005AC1,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CALCULATE FARE main button inside card
                Button(
                    onClick = {
                        // Triggers re-computation visually
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    Text(
                        text = "CALCULATE FARE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Entrance / Role Selection Buttons at the Bottom
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // I AM A DRIVER
            OutlinedButton(
                onClick = { onNavigate("login_driver") },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("driver_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, com.example.ui.theme.Color005AC1),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.Color005AC1)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I AM A DRIVER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SEND FREIGHT
            OutlinedButton(
                onClick = { onNavigate("login_shipper") },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("shipper_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, com.example.ui.theme.Color1B1B1F),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.Color1B1B1F)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SEND FREIGHT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Authentication & Signup
// -------------------------------------------------------------
@Composable
fun LoginScreen(viewModel: MainViewModel, role: String) {
    val lang = viewModel.language
    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (role == "DRIVER") Icons.Default.LocalShipping else Icons.Default.Inventory,
            contentDescription = null,
            tint = com.example.ui.theme.Color005AC1,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = (if (role == "DRIVER") "Driver" else "Shipper") + " " + Localization.get("login_title", lang),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = com.example.ui.theme.Color1B1B1F,
            letterSpacing = (-1).sp
        )

        Text(
            text = "Enter your 10-digit registered mobile number",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = com.example.ui.theme.Color74777F,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Bold design with +91 block next to input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .background(com.example.ui.theme.ColorE0E2EC, RoundedCornerShape(12.dp))
                    .border(1.5.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+91",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.Color1B1B1F
                )
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 10) {
                        phone = clean
                    }
                },
                placeholder = { Text("10-Digit Phone Number", color = com.example.ui.theme.Color74777F) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = getHighContrastTextFieldColors(),
                trailingIcon = {
                    if (phone.length == 10) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Valid phone number",
                            tint = Color(0xFF137333)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("login_phone_input")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.login(phone, role) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = phone.length == 10,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("login_submit_btn"),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
        ) {
            Text(Localization.get("login_btn", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                viewModel.currentScreen = if (role == "DRIVER") "signup_driver" else "signup_shipper"
            },
            modifier = Modifier.testTag("signup_redirect")
        ) {
            Text(
                text = "Don't have an account? " + Localization.get("signup_title", lang),
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.Color005AC1
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Demo Accounts helper section with One-tap Quick Logins
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (lang == "en") "Evaluator Quick Login Help:" else "परीक्षण के लिए त्वरित लॉगिन सहायता:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = com.example.ui.theme.Color1B1B1F
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lang == "en") "Tap an account below to auto-fill details and log in instantly!" else "विवरण स्वतः भरने और तुरंत लॉगिन करने के लिए नीचे टैप करें!",
                    fontSize = 12.sp,
                    color = com.example.ui.theme.Color44474E
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                if (role == "DRIVER") {
                    Button(
                        onClick = {
                            phone = "9112233445"
                            viewModel.login("9112233445", "DRIVER") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AUTO-FILL: Karan Singh (Driver)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            phone = "9876543210"
                            viewModel.login("9876543210", "SHIPPER") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AUTO-FILL: Rajesh Senders (Shipper)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignupDriverScreen(viewModel: MainViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var truckNumber by remember { mutableStateOf("") }
    var truckSize by remember { mutableStateOf("19 Feet") }

    // Documents
    var rcFile by remember { mutableStateOf("") }
    var dlFile by remember { mutableStateOf("") }
    var aadhaarFile by remember { mutableStateOf("") }
    var permitFile by remember { mutableStateOf("") }

    val truckSizes = listOf("14 Feet", "17 Feet", "19 Feet", "22 Feet", "24 Feet", "32 Feet")
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Document Pickers Launcher
    val rcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        rcFile = uri?.toString() ?: ""
    }
    val dlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        dlFile = uri?.toString() ?: ""
    }
    val aadhaarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        aadhaarFile = uri?.toString() ?: ""
    }
    val permitLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        permitFile = uri?.toString() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Driver Onboarding",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Complete your registration to access freight loads",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Demo doc autofill
        Button(
            onClick = {
                name = "Demo Transporter"
                phone = "9" + (100000000 + Random.nextInt(900000000))
                truckNumber = "DL-01-CA-9988"
                truckSize = "22 Feet"
                rcFile = "simulated_rc_photo.jpg"
                dlFile = "simulated_dl_photo.jpg"
                aadhaarFile = "simulated_aadhaar_photo.jpg"
                permitFile = "simulated_permit_photo.jpg"
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Auto-Fill Realistic Demo Driver Docs", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Localization.get("name_label", lang)) },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(Localization.get("phone_label", lang)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = truckNumber,
            onValueChange = { truckNumber = it.uppercase() },
            label = { Text(Localization.get("truck_num_label", lang)) },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Truck Size Dropdown Selection
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = truckSize,
                onValueChange = {},
                readOnly = true,
                label = { Text(Localization.get("truck_size_label", lang)) },
                trailingIcon = {
                    IconButton(onClick = { dropdownExpanded = true }) {
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                },
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                truckSizes.forEach { size ->
                    DropdownMenuItem(
                        text = { Text(size) },
                        onClick = {
                            truckSize = size
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document uploading section
        Text(
            text = "Required Documents (Verification)",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(vertical = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val widthModifier = Modifier.widthIn(max = 160.dp)
            DocUploadCard(
                label = Localization.get("rc_label", lang),
                isUploaded = rcFile.isNotEmpty(),
                onClick = { rcLauncher.launch("image/*") },
                modifier = widthModifier
            )
            DocUploadCard(
                label = Localization.get("dl_label", lang),
                isUploaded = dlFile.isNotEmpty(),
                onClick = { dlLauncher.launch("image/*") },
                modifier = widthModifier
            )
            DocUploadCard(
                label = Localization.get("aadhaar_label", lang),
                isUploaded = aadhaarFile.isNotEmpty(),
                onClick = { aadhaarLauncher.launch("image/*") },
                modifier = widthModifier
            )
            DocUploadCard(
                label = Localization.get("permit_label", lang),
                isUploaded = permitFile.isNotEmpty(),
                onClick = { permitLauncher.launch("image/*") },
                modifier = widthModifier
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.signupDriver(
                    name = name,
                    phone = phone,
                    truckSize = truckSize,
                    truckNumber = truckNumber,
                    rc = rcFile,
                    dl = dlFile,
                    aadhaar = aadhaarFile,
                    permit = permitFile
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(Localization.get("submit_register", lang), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.currentScreen = "landing" }) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DocUploadCard(label: String, isUploaded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isUploaded) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (isUploaded) Color(0xFF137333) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isUploaded) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                contentDescription = null,
                tint = if (isUploaded) Color(0xFF137333) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = if (isUploaded) Color(0xFF137333) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignupShipperScreen(viewModel: MainViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Shipper / Load Owner Signup",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Localization.get("name_label", lang)) },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(Localization.get("phone_label", lang)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.signupShipper(name, phone) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(Localization.get("signup_btn", lang), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.currentScreen = "landing" }) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
    }
}

// -------------------------------------------------------------
// 3. Driver Side Interface
// -------------------------------------------------------------
@Composable
fun DriverHomeScreen(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String
) {
    var selectedTab by remember { mutableStateOf("loads") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = when (selectedTab) {
                "loads" -> 0
                "jobs" -> 1
                "trips" -> 2
                "assistance" -> 3
                "profile" -> 4
                else -> 0
            },
            modifier = Modifier.testTag("driver_top_navigation")
        ) {
            Tab(
                selected = selectedTab == "loads",
                onClick = { selectedTab = "loads" },
                text = { Text("Get Loads", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "jobs",
                onClick = { selectedTab = "jobs" },
                text = { Text("Find Jobs", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "trips",
                onClick = { selectedTab = "trips" },
                text = { Text("Ongoing Trip", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "assistance",
                onClick = { selectedTab = "assistance" },
                text = { Text("Assistance", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "profile",
                onClick = { selectedTab = "profile" },
                text = { Text("Profile & Help", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                "loads" -> DriverLoadsTab(viewModel = viewModel, allLoads = allLoads, lang = lang)
                "jobs" -> DriverJobsTab(viewModel = viewModel, lang = lang)
                "trips" -> DriverTripsTab(viewModel = viewModel, allLoads = allLoads, lang = lang)
                "assistance" -> DriverServicesTab(viewModel = viewModel, lang = lang)
                "profile" -> DriverProfileTab(viewModel = viewModel, lang = lang)
            }
        }
    }
}

@Composable
fun DriverProfileTab(viewModel: MainViewModel, lang: String) {
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Driver Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(com.example.ui.theme.Color005AC1, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = driver.name.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = driver.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = com.example.ui.theme.Color1B1B1F
                        )
                        Text(
                            text = "+91 ${driver.phone}",
                            fontSize = 14.sp,
                            color = com.example.ui.theme.Color44474E
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = com.example.ui.theme.ColorC4C6CF)

                Spacer(modifier = Modifier.height(12.dp))

                // Vehicle details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("VEHICLE SIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color74777F)
                        Text(driver.truckSize, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color1B1B1F)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("VEHICLE NUMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color74777F)
                        Text(driver.truckNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color1B1B1F)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Document Verification Status
        Text("Document Verification Status", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = com.example.ui.theme.Color1B1B1F)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DocStatusRow(label = "Registration Certificate (RC)", isVerified = driver.rcPath.isNotEmpty())
                DocStatusRow(label = "Driving License (DL)", isVerified = driver.dlPath.isNotEmpty())
                DocStatusRow(label = "Aadhaar Card", isVerified = driver.aadhaarPath.isNotEmpty())
                DocStatusRow(label = "National Permit", isVerified = driver.permitPath.isNotEmpty())
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Help & Support Helpline Section
        Text("Emergency Help & Assistance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = com.example.ui.theme.Color1B1B1F)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF)),
            border = BorderStroke(1.5.dp, com.example.ui.theme.Color005AC1)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = com.example.ui.theme.Color005AC1,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "KGI Support Helpline",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color1B1B1F
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "If you face any issues with a load, payment, or commission, contact our 24/7 dedicated helper immediately.",
                    fontSize = 12.sp,
                    color = com.example.ui.theme.Color44474E
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:9660033436"))
                            context.startActivity(dialIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CALL SUPPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "Opening Live WhatsApp Assistant...", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, com.example.ui.theme.Color005AC1),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LIVE CHAT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DocStatusRow(label: String, isVerified: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isVerified) Color(0xFF137333) else Color.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, fontSize = 13.sp, color = com.example.ui.theme.Color1B1B1F)
        }
        Text(
            text = if (isVerified) "VERIFIED" else "PENDING",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isVerified) Color(0xFF137333) else Color.Red
        )
    }
}

@Composable
fun DriverLoadsTab(viewModel: MainViewModel, allLoads: List<Load>, lang: String) {
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    var searchDest by remember { mutableStateOf("") }
    var selectedTruckSizeFilter by remember { mutableStateOf("All") }

    val truckSizes = listOf("All", "14 Feet", "17 Feet", "19 Feet", "22 Feet", "24 Feet", "32 Feet")

    // Filtered loads
    val filteredLoads = allLoads.filter { load ->
        val matchesDest = load.dropLocation.contains(searchDest, ignoreCase = true) ||
                load.pickupLocation.contains(searchDest, ignoreCase = true)
        val matchesSize = selectedTruckSizeFilter == "All" || load.truckSize == selectedTruckSizeFilter
        matchesDest && matchesSize && load.status == "POSTED"
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search & Filters
        OutlinedTextField(
            value = searchDest,
            onValueChange = { searchDest = it },
            label = { Text("Search Destination / Pickup") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("loads_search")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal size filter
        Text("Filter by Truck Size:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            truckSizes.forEach { size ->
                FilterChip(
                    selected = selectedTruckSizeFilter == size,
                    onClick = { selectedTruckSizeFilter = size },
                    label = { Text(size) },
                    modifier = Modifier.testTag("filter_chip_$size")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLoads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No matching loads available right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLoads) { load ->
                    DriverLoadItemCard(load = load, driverId = driver.id, onInterest = {
                        viewModel.expressInterest(load.id)
                    })
                }
            }
        }
    }
}

@Composable
fun DriverLoadItemCard(load: Load, driverId: Int, onInterest: () -> Unit) {
    val isInterested = load.getInterestedDriverIds().contains(driverId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("load_item_${load.id}"),
        elevation = CardDefaults.cardElevation(3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                    Text(load.truckSize, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(
                    text = "₹${"%,.0f".format(load.totalFare)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Route representation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Box(modifier = Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.primary))
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(load.pickupLocation, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(load.dropLocation, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Load Type", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(load.loadType, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Weight", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("${load.weightTons} Tons", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Distance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("${load.distanceKm.toInt()} km", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy rules: Phone numbers hidden until accepted
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Shipper contact hidden until load is accepted.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onInterest() },
                enabled = !isInterested,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInterested) Color.Gray else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isInterested) "Interest Expressed ✓" else "Send Interest / Apply",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DriverServicesTab(viewModel: MainViewModel, lang: String) {
    val gpsActive = viewModel.gpsEnabled
    var selectedCategory by remember { mutableStateOf("garages") }
    val context = LocalContext.current

    // Location search inputs
    var stateQuery by remember { mutableStateOf("Rajasthan") }
    var cityQuery by remember { mutableStateOf("Jaipur") }
    var areaQuery by remember { mutableStateOf("NH-48 Bypass") }
    var pincodeQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "National Assistance & Radar Locator",
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = com.example.ui.theme.Color005AC1,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // GPS/Location permission / live simulator toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (gpsActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (gpsActive) Color(0xFF005AC1) else Color.Red,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (gpsActive) "Live GPS Radar Active" else "GPS Radar Paused",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = com.example.ui.theme.Color1B1B1F
                        )
                        Text(
                            text = if (gpsActive) "Broadcasting Live Location coordinates" else "Enable live GPS radar locator",
                            fontSize = 12.sp,
                            color = com.example.ui.theme.Color44474E
                        )
                    }
                }
                Switch(
                    checked = gpsActive,
                    onCheckedChange = { viewModel.toggleGps() },
                    modifier = Modifier.testTag("gps_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!gpsActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = com.example.ui.theme.Color44474E,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Please toggle GPS on to view nearby services",
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color44474E,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Animated Radar Mockup
            RadarRadar(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(20.dp))

            // Location Search Panel
            Text(
                text = "Enter Search Location for Nearby Services",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = com.example.ui.theme.Color1B1B1F
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // State and City side-by-side
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stateQuery,
                            onValueChange = { stateQuery = it },
                            label = { Text("State", fontSize = 11.sp) },
                            placeholder = { Text("Rajasthan") },
                            singleLine = true,
                            colors = getHighContrastTextFieldColors(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = cityQuery,
                            onValueChange = { cityQuery = it },
                            label = { Text("City", fontSize = 11.sp) },
                            placeholder = { Text("Jaipur") },
                            singleLine = true,
                            colors = getHighContrastTextFieldColors(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Area and Pincode side-by-side
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = areaQuery,
                            onValueChange = { areaQuery = it },
                            label = { Text("Area/Landmark", fontSize = 11.sp) },
                            placeholder = { Text("NH-48 Bypass") },
                            singleLine = true,
                            colors = getHighContrastTextFieldColors(),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = pincodeQuery,
                            onValueChange = { input ->
                                val clean = input.filter { it.isDigit() }
                                if (clean.length <= 6) pincodeQuery = clean
                            },
                            label = { Text("Pincode (Optional)", fontSize = 10.sp) },
                            placeholder = { Text("302001") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = getHighContrastTextFieldColors(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Category choice grid
            Text(
                text = "Service Categories",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = com.example.ui.theme.Color1B1B1F
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.serviceCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.id,
                        onClick = { selectedCategory = cat.id },
                        label = { Text(cat.name, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("cat_chip_${cat.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Query results
            val services = viewModel.getNearbyServices(
                categoryId = selectedCategory,
                city = cityQuery,
                pincode = pincodeQuery,
                state = stateQuery,
                area = areaQuery
            )

            Text(
                text = "Assistance Outposts Found (${services.size} points)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.Color44474E
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                services.forEachIndexed { index, service ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(com.example.ui.theme.Color005AC1, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = service.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = com.example.ui.theme.Color1B1B1F
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = service.description,
                                    fontSize = 12.sp,
                                    color = com.example.ui.theme.Color44474E
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = com.example.ui.theme.Color005AC1,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${service.distanceKm} km away",
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.Color005AC1,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.phone}"))
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(com.example.ui.theme.Color005AC1.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call service",
                                    tint = com.example.ui.theme.Color005AC1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadarRadar(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .background(Color(0xFF0F172A), CircleShape)
            .border(2.dp, Color(0xFF10B981).copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Radar sweeps using Canvas
        Canvas(modifier = Modifier.fillMaxSize().rotate(angle)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            // Sweep light
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, Color(0xFF10B981).copy(alpha = 0.5f))
                ),
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = true,
                size = size
            )
            // Sweep line
            val lineEnd = Offset(
                x = center.x + radius * cos(0.0).toFloat(),
                y = center.y + radius * sin(0.0).toFloat()
            )
            drawLine(
                color = Color(0xFF10B981),
                start = center,
                end = lineEnd,
                strokeWidth = 2f
            )
        }

        // Radar static rings
        Box(modifier = Modifier.size(110.dp).border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), CircleShape))
        Box(modifier = Modifier.size(60.dp).border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), CircleShape))

        // Center dot
        Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))

        // Simulated local targets
        TargetDot(offset = Offset(-30f, -40f))
        TargetDot(offset = Offset(45f, 20f))
        TargetDot(offset = Offset(-25f, 50f))
    }
}

@Composable
fun TargetDot(offset: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .offset(x = offset.x.dp, y = offset.y.dp)
            .size(6.dp)
            .background(Color(0xFF10B981).copy(alpha = alpha), CircleShape)
    )
}

@Composable
fun DriverTripsTab(viewModel: MainViewModel, allLoads: List<Load>, lang: String) {
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val driverTrips = allLoads.filter { it.assignedDriverId == driver.id }

    var showChatDialogLoad by remember { mutableStateOf<Load?>(null) }
    var sosMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your Trip Assignments", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(12.dp))

        if (driverTrips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "You have no active trip assignments yet.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(driverTrips) { load ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Status Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Trip #${load.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Badge(
                                    containerColor = when (load.status) {
                                        "ACCEPTED" -> Color(0xFF0284C7)
                                        "ONGOING" -> Color(0xFF10B981)
                                        "COMPLETED" -> Color(0xFF6B7280)
                                        else -> Color.Gray
                                    }
                                ) {
                                    Text(
                                        text = Localization.get("status_" + load.status.lowercase(), lang),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Route: ${load.pickupLocation} ➔ ${load.dropLocation}", fontWeight = FontWeight.Bold)
                            Text("Cargo: ${load.loadType} (${load.weightTons} Tons)")
                            Text("Fare Reward: ₹${"%,.2f".format(load.totalFare)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // Contact info (Unlocked because driver accepted, hidden after 2 completed trips)
                            val completedTripsCount = allLoads.count { it.assignedDriverId == driver.id && it.status == "COMPLETED" }
                            val hideShipperDetails = completedTripsCount >= 2

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hideShipperDetails) {
                                    Column {
                                        Text("Shipper: Contact Hidden", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                                        Text("Hidden under policy (First 2 trips completed)", fontSize = 12.sp, color = Color.Gray)
                                    }
                                } else {
                                    Column {
                                        Text("Shipper: ${load.shipperName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Phone: ${load.shipperPhone}", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${load.shipperPhone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.clip(CircleShape).background(Color(0xFFE0F2FE))
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Call shipper", tint = Color(0xFF0369A1))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons depending on state
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (load.status) {
                                    "ACCEPTED" -> {
                                        Button(
                                            onClick = { viewModel.updateTripStatus(load.id, "ONGOING") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Start Trip", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    "ONGOING" -> {
                                        Button(
                                            onClick = { viewModel.updateTripStatus(load.id, "COMPLETED") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Text("Complete Trip", fontWeight = FontWeight.Bold)
                                        }

                                        // SOS emergency button
                                        Button(
                                            onClick = {
                                                sosMessage = Localization.get("sos_triggered", lang)
                                                Toast.makeText(context, sosMessage, Toast.LENGTH_LONG).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Localization.get("sos_emergency", lang), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Direct Chat trigger button
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectActiveChatLoadId(load.id)
                                        showChatDialogLoad = load
                                    },
                                    modifier = Modifier.testTag("open_chat_btn_${load.id}")
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chat")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Direct Chat overlay dialog
    if (showChatDialogLoad != null) {
        val load = showChatDialogLoad!!
        ChatDialog(
            viewModel = viewModel,
            load = load,
            onDismiss = {
                showChatDialogLoad = null
                viewModel.selectActiveChatLoadId(null)
            }
        )
    }
}

// -------------------------------------------------------------
// 4. Shipper Side Interface
// -------------------------------------------------------------
@Composable
fun ShipperHomeScreen(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    trackingProgress: Map<Int, Double>,
    onBlockCommission: (Int, Int, Double) -> Unit
) {
    var selectedTab by remember { mutableStateOf("post_load") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = when (selectedTab) {
                "post_load" -> 0
                "active_loads" -> 1
                "trips" -> 2
                "verification_jobs" -> 3
                else -> 0
            },
            modifier = Modifier.testTag("shipper_top_navigation")
        ) {
            Tab(
                selected = selectedTab == "post_load",
                onClick = { selectedTab = "post_load" },
                text = { Text(Localization.get("nav_loads", lang), fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "active_loads",
                onClick = { selectedTab = "active_loads" },
                text = { Text("Applications", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "trips",
                onClick = { selectedTab = "trips" },
                text = { Text(Localization.get("nav_trips", lang), fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "verification_jobs",
                onClick = { selectedTab = "verification_jobs" },
                text = { Text("Drivers & Jobs", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                "post_load" -> ShipperPostLoadTab(viewModel = viewModel, lang = lang)
                "active_loads" -> ShipperActiveLoadsTab(viewModel = viewModel, allLoads = allLoads, lang = lang, onBlockCommission = onBlockCommission)
                "trips" -> ShipperTripsTab(viewModel = viewModel, allLoads = allLoads, lang = lang, trackingProgress = trackingProgress)
                "verification_jobs" -> ShipperVerificationJobsTab(viewModel = viewModel, lang = lang)
            }
        }
    }
}

@Composable
fun LocationMapConfirmation(
    pickupCity: String,
    pickupPincode: String,
    dropCity: String,
    dropPincode: String,
    distanceKm: Double,
    estimatedFare: Double
) {
    if (pickupCity.isBlank() && dropCity.isBlank()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("location_map_confirmation"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // deep elegant slate dark background
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Live GPS Route Confirmation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0F766E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "READY TO ACCEPT",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Procedural Map drawing using Bezier math
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background grid lines to feel like a modern navigation map
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color.White.copy(alpha = 0.05f)
                    val gap = 20.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += gap
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += gap
                    }
                }

                // Route curve animation
                val infiniteTransition = rememberInfiniteTransition(label = "routeMap")
                val animProgress = infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "mapProgress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val startX = 60.dp.toPx()
                    val startY = size.height / 2
                    val endX = size.width - 60.dp.toPx()
                    val endY = size.height / 2

                    // Control point for a nice curve
                    val controlX = size.width / 2
                    val controlY = size.height / 2 - 40.dp.toPx()

                    // Draw dashed/solid background curve
                    val steps = 40
                    var prevX = startX
                    var prevY = startY
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val currX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX
                        val currY = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * controlY + t * t * endY
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(prevX, prevY),
                            end = Offset(currX, currY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        prevX = currX
                        prevY = currY
                    }

                    // Draw active progress glow segment
                    val progressT = animProgress.value
                    prevX = startX
                    prevY = startY
                    val progressSteps = (steps * progressT).toInt()
                    for (i in 1..progressSteps) {
                        val t = i.toFloat() / steps
                        val currX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX
                        val currY = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * controlY + t * t * endY

                        drawLine(
                            color = Color(0xFF38BDF8),
                            start = Offset(prevX, prevY),
                            end = Offset(currX, currY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        prevX = currX
                        prevY = currY
                    }

                    // Active moving truck/pulse coordinates
                    val pulseX = (1 - progressT) * (1 - progressT) * startX + 2 * (1 - progressT) * progressT * controlX + progressT * progressT * endX
                    val pulseY = (1 - progressT) * (1 - progressT) * startY + 2 * (1 - progressT) * progressT * controlY + progressT * progressT * endY

                    // Pulse glow
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 6.dp.toPx(),
                        center = Offset(pulseX, pulseY)
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.3f),
                        radius = 12.dp.toPx() * progressT,
                        center = Offset(pulseX, pulseY)
                    )

                    // Point A node (Green)
                    drawCircle(color = Color(0xFF10B981), radius = 8.dp.toPx(), center = Offset(startX, startY))
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(startX, startY))

                    // Point B node (Red)
                    drawCircle(color = Color(0xFFEF4444), radius = 8.dp.toPx(), center = Offset(endX, endY))
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(endX, endY))
                }

                // Text labels superimposed on map
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (pickupCity.isNotBlank()) pickupCity else "Pickup Point",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (pickupPincode.isNotBlank()) {
                            Text(text = "PIN: $pickupPincode", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (dropCity.isNotBlank()) dropCity else "Drop Point",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (dropPincode.isNotBlank()) {
                            Text(text = "PIN: $dropPincode", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parameter specifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Distance", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(
                        text = if (distanceKm > 0) "${"%,.0f".format(distanceKm)} km" else "--- km",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Estimated Trip Fare", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(
                        text = if (estimatedFare > 0) "₹${"%,.0f".format(estimatedFare)}" else "₹---",
                        color = Color(0xFFFBBF24), // Gold
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ShipperPostLoadTab(viewModel: MainViewModel, lang: String) {
    val context = LocalContext.current

    var pickupCity by remember { mutableStateOf("") }
    var pickupPincode by remember { mutableStateOf("") }
    var dropCity by remember { mutableStateOf("") }
    var dropPincode by remember { mutableStateOf("") }
    var loadType by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }

    // Fare calculator factors
    var truckSize by remember { mutableStateOf("14 Feet") }
    var rateKmText by remember { mutableStateOf("22") }
    var rateTonText by remember { mutableStateOf("100") }

    val truckSizes = listOf("14 Feet", "17 Feet", "19 Feet", "22 Feet", "24 Feet", "32 Feet")
    val defaultRates = mapOf(
        "14 Feet" to 22.0,
        "17 Feet" to 25.0,
        "19 Feet" to 28.0,
        "22 Feet" to 32.0,
        "24 Feet" to 36.0,
        "32 Feet" to 42.0
    )
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Computations
    val distance = distanceText.toDoubleOrNull() ?: 0.0
    val weight = weightText.toDoubleOrNull() ?: 0.0
    val rateKm = rateKmText.toDoubleOrNull() ?: 0.0
    val rateTon = rateTonText.toDoubleOrNull() ?: 0.0

    val distanceCost = distance * rateKm
    val weightCost = weight * rateTon
    val totalFare = distanceCost + weightCost

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Post New Cargo Load",
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = com.example.ui.theme.Color005AC1,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pickup details (City & Pincode side-by-side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = pickupCity,
                onValueChange = { pickupCity = it },
                label = { Text("Pickup City") },
                placeholder = { Text("e.g. Jaipur") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF10B981)) },
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = pickupPincode,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 6) pickupPincode = clean
                },
                label = { Text("Pincode") },
                placeholder = { Text("302001") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Drop details (City & Pincode side-by-side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = dropCity,
                onValueChange = { dropCity = it },
                label = { Text("Drop City") },
                placeholder = { Text("e.g. Delhi") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEF4444)) },
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = dropPincode,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 6) dropPincode = clean
                },
                label = { Text("Pincode") },
                placeholder = { Text("110001") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = loadType,
            onValueChange = { loadType = it },
            label = { Text(Localization.get("load_type_label", lang)) },
            placeholder = { Text("e.g. Steel, Cement, Parcels") },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Live visual Route Mapping
        LocationMapConfirmation(
            pickupCity = pickupCity,
            pickupPincode = pickupPincode,
            dropCity = dropCity,
            dropPincode = dropPincode,
            distanceKm = distance,
            estimatedFare = totalFare
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Embedded Fare Calculator Widget (Rounded card format)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fare_calculator_widget"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Localization.get("fare_calc_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = com.example.ui.theme.Color005AC1
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Truck size selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = truckSize,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(Localization.get("truck_size_label", lang)) },
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = true }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        truckSizes.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size) },
                                onClick = {
                                    truckSize = size
                                    rateKmText = defaultRates[size]?.toString() ?: "22"
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it },
                        label = { Text(Localization.get("dist_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(Localization.get("weight_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateKmText,
                        onValueChange = { rateKmText = it },
                        label = { Text(Localization.get("rate_km_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = rateTonText,
                        onValueChange = { rateTonText = it },
                        label = { Text(Localization.get("rate_ton_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calculations Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Localization.get("dist_cost", lang), fontSize = 13.sp)
                            Text("₹${"%,.2f".format(distanceCost)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Localization.get("weight_cost", lang), fontSize = 13.sp)
                            Text("₹${"%,.2f".format(weightCost)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = com.example.ui.theme.ColorC4C6CF)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(Localization.get("total_fare", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹${"%,.2f".format(totalFare)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = com.example.ui.theme.Color005AC1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reset Fare calculator
                TextButton(
                    onClick = {
                        distanceText = ""
                        weightText = ""
                        rateTonText = "100"
                        rateKmText = defaultRates[truckSize]?.toString() ?: "22"
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.get("reset_btn", lang), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Posting Load button
        Button(
            onClick = {
                val fullPickup = if (pickupPincode.isNotBlank()) "$pickupCity ($pickupPincode)" else pickupCity
                val fullDrop = if (dropPincode.isNotBlank()) "$dropCity ($dropPincode)" else dropCity

                viewModel.postLoad(
                    pickup = fullPickup,
                    drop = fullDrop,
                    loadType = loadType,
                    weight = weight,
                    truckSize = truckSize,
                    distance = distance,
                    rateKm = rateKm,
                    rateTon = rateTon
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        pickupCity = ""
                        pickupPincode = ""
                        dropCity = ""
                        dropPincode = ""
                        loadType = ""
                        weightText = ""
                        distanceText = ""
                    }
                }
            },
            enabled = pickupCity.isNotBlank() && dropCity.isNotBlank() && loadType.isNotBlank() && distance > 0 && weight > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("submit_post_load_btn"),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
        ) {
            Text(Localization.get("post_load_btn", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
    }
}

@Composable
fun ShipperActiveLoadsTab(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    onBlockCommission: (Int, Int, Double) -> Unit
) {
    val shipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val shipperLoads = allLoads.filter { it.shipperId == shipper.id && it.status == "POSTED" }

    var selectedDriversLoad by remember { mutableStateOf<Load?>(null) }
    var viewDocsDriver by remember { mutableStateOf<User?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Active Load Applications", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        if (shipperLoads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No active loads posted yet.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shipperLoads) { load ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Load #${load.id}: ${load.pickupLocation} ➔ ${load.dropLocation}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Cargo Type: ${load.loadType} | Budget: ₹${"%,.0f".format(load.totalFare)}")

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            Text(
                                text = "Interested Drivers (${load.getInterestedDriverIds().size})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            val driverIds = load.getInterestedDriverIds()
                            DriverApplicationsSection(
                                viewModel = viewModel,
                                driverIds = driverIds,
                                load = load,
                                lang = lang,
                                onReviewDocs = { viewDocsDriver = it },
                                onBlockCommission = onBlockCommission
                            )
                        }
                    }
                }
            }
        }
    }

    // Document review overlay dialog
    if (viewDocsDriver != null) {
        DriverDocsReviewDialog(
            driver = viewDocsDriver!!,
            onDismiss = { viewDocsDriver = null }
        )
    }
}

@Composable
fun DriverApplicationsSection(
    viewModel: MainViewModel,
    driverIds: List<Int>,
    load: Load,
    lang: String,
    onReviewDocs: (User) -> Unit,
    onBlockCommission: (Int, Int, Double) -> Unit
) {
    if (driverIds.isEmpty()) {
        Text(
            text = Localization.get("no_drivers_interested", lang),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp)
        )
    } else {
        val driversState = viewModel.getDriversByIds(driverIds).collectAsStateWithLifecycle(emptyList())
        val drivers = driversState.value
        val context = LocalContext.current

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            drivers.forEach { driver ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(driver.name, fontWeight = FontWeight.Bold)
                        Text("Truck: ${driver.truckNumber} (${driver.truckSize})", fontSize = 12.sp)
                    }

                    Row {
                        TextButton(onClick = { onReviewDocs(driver) }) {
                            Text("Review Docs", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.acceptDriverForLoad(
                                    loadId = load.id,
                                    driverId = driver.id,
                                    onBlock = { commissionAmount ->
                                        onBlockCommission(load.id, driver.id, commissionAmount)
                                    },
                                    onSuccess = {
                                        Toast.makeText(context, "Driver accepted!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverDocsReviewDialog(driver: User, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verify Driver Documents",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(driver.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Truck: ${driver.truckNumber}", fontSize = 13.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful representations of RC, DL, Aadhaar, Permit
                DocRowReview(label = "Registration Certificate (RC)", path = driver.rcPath)
                Spacer(modifier = Modifier.height(10.dp))
                DocRowReview(label = "Driving License (DL)", path = driver.dlPath)
                Spacer(modifier = Modifier.height(10.dp))
                DocRowReview(label = "Aadhaar Identity Card", path = driver.aadhaarPath)
                Spacer(modifier = Modifier.height(10.dp))
                DocRowReview(label = "Carrier Freight Permit", path = driver.permitPath)

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Verified & Okay", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DocRowReview(label: String, path: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(path, fontSize = 11.sp, color = Color(0xFF475569))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Ready to inspect • Click to download", fontSize = 9.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ShipperTripsTab(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    trackingProgress: Map<Int, Double>
) {
    val shipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val shipperTrips = allLoads.filter { it.shipperId == shipper.id && it.status != "POSTED" }

    var showChatDialogLoad by remember { mutableStateOf<Load?>(null) }
    var ratingDialogLoad by remember { mutableStateOf<Load?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Trip History & Live Tracking", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        if (shipperTrips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No active or completed trips found.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shipperTrips) { load ->
                    val progress = trackingProgress[load.id] ?: 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Status Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Trip #${load.id}: ${load.pickupLocation} ➔ ${load.dropLocation}", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Badge(
                                    containerColor = when (load.status) {
                                        "ACCEPTED" -> Color(0xFF0284C7)
                                        "ONGOING" -> Color(0xFF10B981)
                                        "COMPLETED" -> Color(0xFF6B7280)
                                        else -> Color.Gray
                                    }
                                ) {
                                    Text(
                                        text = Localization.get("status_" + load.status.lowercase(), lang),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cargo: ${load.loadType} (${load.weightTons} Tons) | Fare: ₹${"%,.0f".format(load.totalFare)}")

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // Contact info unlocked, hidden after 2 completed trips
                            val completedTripsCount = allLoads.count { it.shipperId == shipper.id && it.status == "COMPLETED" }
                            val hideDriverDetails = completedTripsCount >= 2

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hideDriverDetails) {
                                    Column {
                                        Text("Driver: Contact Hidden", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                        Text("Hidden under policy (First 2 trips completed)", fontSize = 12.sp, color = Color.Gray)
                                    }
                                } else {
                                    Column {
                                        Text("Driver: ${load.assignedDriverName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Phone: ${load.assignedDriverPhone}", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${load.assignedDriverPhone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.clip(CircleShape).background(Color(0xFFDCFCE7))
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Call driver", tint = Color(0xFF15803D))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // REAL TIME GPS TRACKING REPRESENTATION
                            if (load.status == "ONGOING") {
                                LiveTrackingRouteIndicator(progress = progress, totalDistance = load.distanceKm, lang = lang)
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Direct Chat button
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectActiveChatLoadId(load.id)
                                        showChatDialogLoad = load
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Direct Chat")
                                }

                                if (load.status == "COMPLETED") {
                                    Button(
                                        onClick = { ratingDialogLoad = load },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rate Driver")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Direct Chat overlay dialog
    if (showChatDialogLoad != null) {
        val load = showChatDialogLoad!!
        ChatDialog(
            viewModel = viewModel,
            load = load,
            onDismiss = {
                showChatDialogLoad = null
                viewModel.selectActiveChatLoadId(null)
            }
        )
    }

    // Rating/Review overlay dialog
    if (ratingDialogLoad != null) {
        RatingDialog(
            load = ratingDialogLoad!!,
            lang = lang,
            onDismiss = { ratingDialogLoad = null }
        )
    }
}

@Composable
fun LiveTrackingRouteIndicator(progress: Double, totalDistance: Double, lang: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Localization.get("gps_tracking_title", lang),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                fontSize = 13.sp
            )
            Text(
                text = "GPS Signal Active ✓",
                color = Color(0xFF10B981),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Linear Progress bar with truck icon animated
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF334155))
            )

            // Progress filler
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.toFloat())
                    .height(4.dp)
                    .background(Color(0xFF0284C7))
            )

            // Moving Truck icon
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.toFloat())
                    .offset(x = (-12).dp) // slightly center truck over the point
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF0F172A), CircleShape)
                        .padding(2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pickup Area", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text("Destination", color = Color(0xFF94A3B8), fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        val covered = progress * totalDistance
        Text(
            text = Localization.get("gps_distance_remaining", lang) + "${covered.toInt()} km / ${totalDistance.toInt()} km",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

// -------------------------------------------------------------
// 5. Direct In-App Chat Component
// -------------------------------------------------------------
@Composable
fun ChatDialog(viewModel: MainViewModel, load: Load, onDismiss: () -> Unit) {
    val messages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    var currentText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Localization.get("chat_title", viewModel.language),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Cargo: ${load.loadType} (#${load.id})",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close chat", tint = Color.White)
                    }
                }

                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isMe = msg.senderId == driver.id
                        val bg = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val align = if (isMe) Alignment.End else Alignment.Start

                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                            Text(
                                text = if (isMe) "You" else msg.senderName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = bg),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Message input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { currentText = it },
                        placeholder = { Text(Localization.get("send_msg_hint", viewModel.language)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input"),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendChatMessage(load.id, currentText)
                            currentText = ""
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("chat_send_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. Commission UPI Dialog (With Custom Procedural QR Code)
// -------------------------------------------------------------
@Composable
fun CommissionPaymentDialog(viewModel: MainViewModel, amount: Double, onDismiss: () -> Unit, onPaymentConfirmed: () -> Unit) {
    val lang = viewModel.language
    var upiId by remember { mutableStateOf(viewModel.adminUpiId) }
    var selectedWallet by remember { mutableStateOf("gpay") }
    var qrGenerated by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Localization.get("commission_warning", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Red
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = Localization.get("commission_desc", lang),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Commission display
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Commission to pay (0.8% with Tax):", fontSize = 12.sp)
                        Text("₹${"%,.2f".format(amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Editable admin UPI ID config
                OutlinedTextField(
                    value = upiId,
                    onValueChange = {
                        upiId = it
                        viewModel.adminUpiId = it
                    },
                    label = { Text("Configured UPI ID (Admin)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Payment modes selector
                Text("Select Wallet Provider:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WalletOption(label = "Google Pay", isSelected = selectedWallet == "gpay", onClick = { selectedWallet = "gpay"; qrGenerated = false })
                    WalletOption(label = "PhonePe", isSelected = selectedWallet == "phonepe", onClick = { selectedWallet = "phonepe"; qrGenerated = false })
                    WalletOption(label = "Paytm", isSelected = selectedWallet == "paytm", onClick = { selectedWallet = "paytm"; qrGenerated = false })
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Generate QR Code or direct deep link button
                if (!qrGenerated) {
                    Button(
                        onClick = { qrGenerated = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Live UPI QR Code", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Procedural drawing of QR Code
                    ProceduralUpiQrCard(amount = amount, upiId = upiId)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onPaymentConfirmed,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirm Commission Paid", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Cancel trip assignment", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun WalletOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProceduralUpiQrCard(amount: Double, upiId: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.2f)),
        modifier = Modifier.size(190.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("UPI QR SCANNER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))

            // Beautiful Procedural QR Code Drawing inside Canvas
            Canvas(modifier = Modifier.size(110.dp)) {
                // Outer framing
                drawRect(color = Color.Black, style = Stroke(width = 4f))

                // Standard QR alignment markers at top-left, top-right, bottom-left
                val markerSize = 24f
                val strokeW = 4f

                // Top-Left Marker
                drawRect(color = Color.Black, topLeft = Offset(4f, 4f), size = Size(markerSize, markerSize))
                drawRect(color = Color.White, topLeft = Offset(8f, 8f), size = Size(markerSize - 8f, markerSize - 8f))
                drawRect(color = Color.Black, topLeft = Offset(11f, 11f), size = Size(markerSize - 14f, markerSize - 14f))

                // Top-Right Marker
                drawRect(color = Color.Black, topLeft = Offset(size.width - markerSize - 4f, 4f), size = Size(markerSize, markerSize))
                drawRect(color = Color.White, topLeft = Offset(size.width - markerSize, 8f), size = Size(markerSize - 8f, markerSize - 8f))
                drawRect(color = Color.Black, topLeft = Offset(size.width - markerSize + 3f, 11f), size = Size(markerSize - 14f, markerSize - 14f))

                // Bottom-Left Marker
                drawRect(color = Color.Black, topLeft = Offset(4f, size.height - markerSize - 4f), size = Size(markerSize, markerSize))
                drawRect(color = Color.White, topLeft = Offset(8f, size.height - markerSize), size = Size(markerSize - 8f, markerSize - 8f))
                drawRect(color = Color.Black, topLeft = Offset(11f, size.height - markerSize + 3f), size = Size(markerSize - 14f, markerSize - 14f))

                // Procedural grid dots inside
                val gridCount = 11
                val cellSize = size.width / gridCount
                for (row in 0 until gridCount) {
                    for (col in 0 until gridCount) {
                        // Skip marker areas
                        if (row < 3 && col < 3) continue
                        if (row < 3 && col >= gridCount - 3) continue
                        if (row >= gridCount - 3 && col < 3) continue

                        // Procedural mock randomness
                        val seed = row * 31 + col * 17 + (amount * 100).toInt()
                        if (seed % 3 == 0 || seed % 5 == 0) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(col * cellSize + 2f, row * cellSize + 2f),
                                size = Size(cellSize - 4f, cellSize - 4f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(upiId, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("Amount: ₹${"%,.2f".format(amount)}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    }
}

// -------------------------------------------------------------
// 7. Post Trip Ratings Component
// -------------------------------------------------------------
@Composable
fun RatingDialog(load: Load, lang: String, onDismiss: () -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var feedback by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Localization.get("ratings_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("How was your trip with driver ${load.assignedDriverName}?", textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(16.dp))

                // 5 Star row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star $i",
                            tint = if (i <= rating) Color(0xFFF59E0B) else Color.Gray,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { rating = i }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("Write feedback (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Feedback Submitted! Thank you.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Localization.get("submit_rating", lang), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DriverJobsTab(viewModel: MainViewModel, lang: String) {
    val context = LocalContext.current
    val allJobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    var showApplyDialog by remember { mutableStateOf(false) }
    var selectedJobForApply by remember { mutableStateOf<com.example.data.JobProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AVAILABLE JOB PROFILES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = com.example.ui.theme.Color74777F,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (allJobs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No job profiles posted yet.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allJobs) { job ->
                    val applicantIds = job.getApplicantIds()
                    val hasApplied = applicantIds.contains(driver.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("driver_job_card_${job.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = job.workTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = com.example.ui.theme.Color1B1B1F
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = job.salaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Shipper: ${job.shipperName}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = com.example.ui.theme.Color74777F
                            )
                            Text(
                                text = "Location: ${job.location}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = com.example.ui.theme.Color005AC1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = job.description,
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    selectedJobForApply = job
                                    showApplyDialog = true
                                },
                                enabled = !hasApplied,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasApplied) Color.Gray else com.example.ui.theme.Color005AC1
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (hasApplied) "Applied ✓" else "APPLY FOR THIS JOB",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApplyDialog && selectedJobForApply != null) {
        val job = selectedJobForApply!!
        Dialog(onDismissRequest = { showApplyDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color.Black)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Apply for: ${job.workTitle}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Confirm Verifiable Documents",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var editDl by remember { mutableStateOf(driver.dlPath.ifBlank { "simulated_dl_doc.jpg" }) }
                    var editRc by remember { mutableStateOf(driver.rcPath.ifBlank { "simulated_rc_doc.jpg" }) }
                    var editAadhaar by remember { mutableStateOf(driver.aadhaarPath.ifBlank { "simulated_aadhaar_doc.jpg" }) }
                    var editPermit by remember { mutableStateOf(driver.permitPath.ifBlank { "simulated_permit_doc.jpg" }) }

                    OutlinedTextField(
                        value = editDl,
                        onValueChange = { editDl = it },
                        label = { Text("Driving License (DL) File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editRc,
                        onValueChange = { editRc = it },
                        label = { Text("Registration Certificate (RC) File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editAadhaar,
                        onValueChange = { editAadhaar = it },
                        label = { Text("Aadhaar Card File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editPermit,
                        onValueChange = { editPermit = it },
                        label = { Text("Vehicle Permit File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showApplyDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.Red, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.updateDriverDocs(editDl, editRc, editAadhaar, editPermit) {
                                    viewModel.applyForJob(job.id) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            showApplyDialog = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Submit Application", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShipperVerificationJobsTab(viewModel: MainViewModel, lang: String) {
    val context = LocalContext.current
    var subTab by remember { mutableStateOf("verify") } // "verify" or "jobs"
    val allDrivers by viewModel.allDrivers.collectAsStateWithLifecycle()
    val allJobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val currentShipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    // Job Posting Form State
    var workTitle by remember { mutableStateOf("") }
    var salaryText by remember { mutableStateOf("") }
    var jobLocation by remember { mutableStateOf("") }
    var jobDescription by remember { mutableStateOf("") }

    // Selected Driver for Dialog Document View
    var selectedDriverForDocView by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sub Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { subTab = "verify" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == "verify") com.example.ui.theme.Color005AC1 else com.example.ui.theme.ColorE0E2EC,
                    contentColor = if (subTab == "verify") Color.White else com.example.ui.theme.Color1B1B1F
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Verify Drivers", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { subTab = "jobs" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == "jobs") com.example.ui.theme.Color005AC1 else com.example.ui.theme.ColorE0E2EC,
                    contentColor = if (subTab == "jobs") Color.White else com.example.ui.theme.Color1B1B1F
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post & Manage Jobs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (subTab == "verify") {
            // VERIFY DRIVERS PANEL
            Text(
                text = "REGISTERED DRIVERS FOR VERIFICATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = com.example.ui.theme.Color74777F,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (allDrivers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No drivers registered yet.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(allDrivers) { driver ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("driver_verify_card_${driver.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = driver.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = com.example.ui.theme.Color1B1B1F
                                        )
                                        Text(
                                            text = "Phone: +91 ${driver.phone}",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = com.example.ui.theme.Color005AC1
                                        )
                                    }
                                    
                                    // Status Badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (driver.isApproved) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (driver.isApproved) "APPROVED ✓" else "PENDING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (driver.isApproved) Color(0xFF15803D) else Color(0xFFB91C1C)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Truck Number", fontSize = 10.sp, color = Color.Gray)
                                        Text(driver.truckNumber.ifBlank { "N/A" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Truck Size", fontSize = 10.sp, color = Color.Gray)
                                        Text(driver.truckSize.ifBlank { "N/A" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Documents", fontSize = 10.sp, color = Color.Gray)
                                        Text("4 Files Uploaded", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F766E))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { selectedDriverForDocView = driver },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color1B1B1F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View Documents & Verification Info", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // POST & MANAGE JOBS PANEL
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    // Create Job Form
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "POST A NEW JOB PROFILE",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = com.example.ui.theme.Color1B1B1F,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = workTitle,
                                onValueChange = { workTitle = it },
                                label = { Text("Work Title (e.g. Jaipur to Ahmedabad Driver)") },
                                singleLine = true,
                                colors = getHighContrastTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("job_title_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = salaryText,
                                    onValueChange = { salaryText = it },
                                    label = { Text("Salary (e.g. ₹28,000 / month)") },
                                    singleLine = true,
                                    colors = getHighContrastTextFieldColors(),
                                    modifier = Modifier.weight(1f).testTag("job_salary_input")
                                )
                                OutlinedTextField(
                                    value = jobLocation,
                                    onValueChange = { jobLocation = it },
                                    label = { Text("Work Location (City)") },
                                    singleLine = true,
                                    colors = getHighContrastTextFieldColors(),
                                    modifier = Modifier.weight(1f).testTag("job_location_input")
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = jobDescription,
                                onValueChange = { jobDescription = it },
                                label = { Text("Detailed Job Description") },
                                maxLines = 3,
                                colors = getHighContrastTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("job_desc_input")
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.postJob(
                                        workTitle = workTitle,
                                        salaryText = salaryText,
                                        location = jobLocation,
                                        description = jobDescription
                                    ) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            workTitle = ""
                                            salaryText = ""
                                            jobLocation = ""
                                            jobDescription = ""
                                        }
                                    }
                                },
                                enabled = workTitle.isNotBlank() && salaryText.isNotBlank() && jobLocation.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
                            ) {
                                Text("POST JOB PROFILE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "YOUR POSTED JOBS",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = com.example.ui.theme.Color74777F,
                        letterSpacing = 1.sp
                    )
                }

                val shipperJobs = allJobs.filter { it.shipperId == currentShipper.id }
                if (shipperJobs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No job profiles posted yet.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(shipperJobs) { job ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(job.workTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = com.example.ui.theme.Color1B1B1F)
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(job.salaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                    }
                                }
                                Text("Location: ${job.location}", fontSize = 13.sp, color = com.example.ui.theme.Color005AC1, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(job.description, fontSize = 13.sp, color = Color.DarkGray)

                                Divider(modifier = Modifier.padding(vertical = 12.dp))

                                val applicantIds = job.getApplicantIds()
                                Text(
                                    text = "APPLICANTS (${applicantIds.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = com.example.ui.theme.Color74777F,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (applicantIds.isEmpty()) {
                                    Text("No applications received yet.", fontSize = 12.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                } else {
                                    val applicants = allDrivers.filter { applicantIds.contains(it.id) }
                                    applicants.forEach { driver ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Phone: +91 ${driver.phone}", fontSize = 11.sp, color = com.example.ui.theme.Color005AC1)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { selectedDriverForDocView = driver },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Review Docs", fontSize = 10.sp, color = Color.White)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Elegant Driver Document Inspection & Approval Dialog
    selectedDriverForDocView?.let { driver ->
        AlertDialog(
            onDismissRequest = { selectedDriverForDocView = null },
            title = {
                Column {
                    Text("Driver Verification Detail", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Review registered documents carefully before action.", fontSize = 12.sp, color = Color.Gray)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("NAME: ${driver.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("MOBILE NO: +91 ${driver.phone}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = com.example.ui.theme.Color005AC1)
                            Text("TRUCK NO: ${driver.truckNumber}", fontSize = 13.sp)
                            Text("TRUCK SIZE: ${driver.truckSize}", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Verification Status: " + if (driver.isApproved) "Approved ✓" else "Pending Review",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (driver.isApproved) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text("1. DRIVING LICENSE (DL)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("Driving License File", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Text(driver.dlPath, fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    Text("2. REGISTRATION CERTIFICATE (RC)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("Vehicle RC File", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Text(driver.rcPath, fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    Text("3. AADHAAR CARD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("Aadhaar Card File", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Text(driver.aadhaarPath, fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    Text("4. VEHICLE PERMIT / PHOTO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("Permit / Photo File", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Text(driver.permitPath, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveDriver(driver.id) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                selectedDriverForDocView = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("APPROVE DRIVER")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        viewModel.rejectDriver(driver.id) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                selectedDriverForDocView = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("REJECT")
                }
            }
        )
    }
}
