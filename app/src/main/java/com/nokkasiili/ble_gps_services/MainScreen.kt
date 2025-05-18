package com.nokkasiili.ble_gps_services

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nokkasiili.ble_gps_services.Constants.BROADCAST_INTERVAL
import com.nokkasiili.ble_gps_services.Constants.NO_DATA_TIMEOUT_MS
import com.nokkasiili.ble_gps_services.Constants.updateBroadcastInterval
import com.nokkasiili.ble_gps_services.Constants.updateNoDataTimeout

// Define a dark theme color palette
private val DarkColors = darkColorScheme(
    primary = Color(0xFF00E676),        // Neon Green
    secondary = Color(0xFF00BCD4),      // Cyan
    tertiary = Color(0xFF9C27B0),       // Purple
    background = Color(0xFF121212),     // Dark Background
    surface = Color(0xFF1E1E1E),        // Surface Dark
    error = Color(0xFFFF5252),          // Error Red
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFEEEEEE),   // Light Gray for text
    onSurface = Color(0xFFEEEEEE)       // Light Gray for text
)

// Neon Blue for hacker aesthetic
private val NeonBlue = Color(0xFF00FFFF)

// Create custom gradients
private val darkGradientBroadcaster = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF002339),
        Color(0xFF001824)
    )
)

private val activeGradientBroadcaster = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF00324F),
        Color(0xFF002339)
    )
)

private val darkGradientReceiver = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1E2723),
        Color(0xFF0F1411)
    )
)

private val activeGradientReceiver = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF002B14),
        Color(0xFF001409)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onEvent: (MainUiEvent) -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors
    ) {
        Scaffold(
            containerColor = Color(0xFF000000),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "BLE_GPS_SERVICES_",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { onEvent(MainUiEvent.ShowCustomSettingsDialog) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0A0A0A),
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
            ) {
                // Split screen into two halves
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Half - Receiver
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        // Background card with gradient
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (uiState.isReceiverRunning) {
                                        onEvent(MainUiEvent.StopReceiver)
                                    } else {
                                        onEvent(MainUiEvent.StartReceiver)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (uiState.isReceiverRunning)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color(0xFF333333)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (uiState.isReceiverRunning)
                                            activeGradientReceiver
                                        else
                                            darkGradientReceiver
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        modifier = Modifier.size(48.dp),
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Receiver",
                                        tint = if (uiState.isReceiverRunning)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            Color(0xFF4D4D4D)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "RECEIVER",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = if (uiState.isReceiverRunning)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            Color(0xFF4D4D4D)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (uiState.isReceiverRunning)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else
                                                Color(0xFF1A1A1A)
                                        )
                                    ) {
                                        Text(
                                            text = if (uiState.isReceiverRunning) "[ ACTIVE ]" else "[ INACTIVE ]",
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            color = if (uiState.isReceiverRunning)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                Color(0xFF4D4D4D)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Half - Broadcaster
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        // Background card with gradient
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (uiState.isBroadcastRunning) {
                                        onEvent(MainUiEvent.StopBroadcast)
                                    } else {
                                        onEvent(MainUiEvent.StartBroadcast)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (uiState.isBroadcastRunning)
                                    NeonBlue
                                else
                                    Color(0xFF333333)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (uiState.isBroadcastRunning)
                                            activeGradientBroadcaster
                                        else
                                            darkGradientBroadcaster
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        modifier = Modifier.size(48.dp),
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = "Broadcaster",
                                        tint = if (uiState.isBroadcastRunning)
                                            NeonBlue
                                        else
                                            Color(0xFF4D4D4D)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "BROADCASTER",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = if (uiState.isBroadcastRunning)
                                            NeonBlue
                                        else
                                            Color(0xFF4D4D4D)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (uiState.isBroadcastRunning)
                                                NeonBlue.copy(alpha = 0.1f)
                                            else
                                                Color(0xFF1A1A1A)
                                        )
                                    ) {
                                        Text(
                                            text = if (uiState.isBroadcastRunning) "[ ACTIVE ]" else "[ INACTIVE ]",
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            color = if (uiState.isBroadcastRunning)
                                                NeonBlue
                                            else
                                                Color(0xFF4D4D4D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Permission warnings overlay if needed
                if (!uiState.isLocationPermissionGranted ||
                    !uiState.areBluetoothPermissionsGranted ||
                    !uiState.isNotificationPermissionGranted) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(16.dp)
                                .widthIn(max = 320.dp),
                            elevation = CardDefaults.cardElevation(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1A1A1A)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SYSTEM ACCESS REQUIRED",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                // Location permission
                                if (!uiState.isLocationPermissionGranted) {
                                    PermissionItem(
                                        title = "Location",
                                        onRequestClick = { onEvent(MainUiEvent.RequestPermission(PermissionType.LOCATION)) }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Bluetooth permissions
                                if (!uiState.areBluetoothPermissionsGranted) {
                                    PermissionItem(
                                        title = "Bluetooth",
                                        onRequestClick = { onEvent(MainUiEvent.RequestPermission(PermissionType.BLUETOOTH)) }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Notification permission (Android 13+)
                                if (!uiState.isNotificationPermissionGranted) {
                                    PermissionItem(
                                        title = "Notifications",
                                        onRequestClick = { onEvent(MainUiEvent.RequestPermission(PermissionType.NOTIFICATION)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Custom Settings Dialog
                if (uiState.showCustomSettingsDialog) {
                    var broadcastIntervalInput by remember { mutableStateOf(BROADCAST_INTERVAL.toString()) }
                    var noDataTimeoutInput by remember { mutableStateOf(NO_DATA_TIMEOUT_MS.toString()) }

                    AlertDialog(
                        onDismissRequest = { onEvent(MainUiEvent.DismissCustomSettingsDialog) },
                        containerColor = Color(0xFF141414),
                        title = {
                            Text(
                                "SYSTEM CONFIGURATION",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    "Broadcast Interval (ms):",
                                    color = Color(0xFFCCCCCC)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = broadcastIntervalInput,
                                    onValueChange = { broadcastIntervalInput = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color(0xFF444444),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFCCCCCC),
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedContainerColor = Color(0xFF1A1A1A),
                                        unfocusedContainerColor = Color(0xFF1A1A1A)
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Data Timeout (ms):",
                                    color = Color(0xFFCCCCCC)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = noDataTimeoutInput,
                                    onValueChange = { noDataTimeoutInput = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color(0xFF444444),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFCCCCCC),
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedContainerColor = Color(0xFF1A1A1A),
                                        unfocusedContainerColor = Color(0xFF1A1A1A)
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            }
                        },
                        confirmButton = {
                            ElevatedButton(
                                onClick = {
                                    val newInterval = broadcastIntervalInput.toLongOrNull()
                                    val newTimeout = noDataTimeoutInput.toLongOrNull()

                                    if (newInterval != null) {
                                        updateBroadcastInterval(newInterval)
                                    }
                                    if (newTimeout != null) {
                                        updateNoDataTimeout(newTimeout)
                                    }
                                    onEvent(MainUiEvent.DismissCustomSettingsDialog)
                                },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("APPLY")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { onEvent(MainUiEvent.DismissCustomSettingsDialog) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text("CANCEL")
                            }
                        }
                    )
                }

                // Settings dialog for permissions
                if (uiState.showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { onEvent(MainUiEvent.DismissSettingsDialog) },
                        containerColor = Color(0xFF141414),
                        title = {
                            Text(
                                "ACCESS DENIED",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                "Required system permissions must be granted via Settings.",
                                color = Color(0xFFCCCCCC)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { onEvent(MainUiEvent.DismissSettingsDialog) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("CONFIRM")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    onRequestClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "$title:",
            modifier = Modifier.weight(1f),
            color = Color(0xFFCCCCCC)
        )
        Button(
            onClick = onRequestClick,
            modifier = Modifier.padding(start = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                "GRANT",
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }
    }
}