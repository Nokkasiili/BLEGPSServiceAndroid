package com.nokkasiili.ble_gps_services

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nokkasiili.ble_gps_services.PermissionType.*
import com.nokkasiili.ble_gps_services.ServiceStateReceiver.*
import com.nokkasiili.ble_gps_services.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val TAG = "MainActivity"

    // Service state receiver for updating UI when service states change
    private lateinit var serviceStateReceiver: ServiceStateReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize service state receiver
        serviceStateReceiver = ServiceStateReceiver { isRunning, serviceType ->
            when (serviceType) {
                Companion.SERVICE_TYPE_RECEIVER -> viewModel.updateReceiverState(isRunning)
                Companion.SERVICE_TYPE_BROADCAST -> viewModel.updateBroadcastState(isRunning)
            }
        }


        // Register the receiver for service state changes
        registerReceiver(
            serviceStateReceiver,
            IntentFilter().apply {
                addAction(Companion.ACTION_SERVICE_STOPPED)
                addAction(Companion.ACTION_SERVICE_STARTED)
            },
            RECEIVER_NOT_EXPORTED
        )

        // Handle notification intent
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                // 1️⃣ Observe UI state
                val uiState by viewModel.uiState.collectAsState()

                // 2️⃣ Permission launchers
                val locationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    viewModel.onPermissionResult(LOCATION, granted)
                }
                val bluetoothLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    // granted only if all required bluetooth perms are true
                    val ok = results.entries.all { it.value }
                    viewModel.onPermissionResult(BLUETOOTH, ok)
                }
                val notifyLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        viewModel.onPermissionResult(NOTIFICATION, granted)
                    }

                // 3️⃣ Launch Settings when needed
                LaunchedEffect(uiState.showSettingsDialog) {
                    if (uiState.showSettingsDialog) {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                        viewModel.handleEvent(MainUiEvent.DismissSettingsDialog)
                    }
                }

                // 5️⃣ Effect to handle notification openings from initial intent
                LaunchedEffect(Unit) {
                    if (viewModel.pendingNotificationAction) {
                        // Navigate to the appropriate screen or tab
                        viewModel.handleEvent(MainUiEvent.NavigateToServiceStatus)
                        viewModel.pendingNotificationAction = false
                    }
                }

                // 4️⃣ Render your composable
                MainScreen(
                    uiState = uiState,
                    onEvent = { event ->
                        when (event) {
                            is MainUiEvent.RequestPermission -> when (event.type) {
                                LOCATION -> locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                BLUETOOTH -> bluetoothLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_ADVERTISE,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                )
                                NOTIFICATION -> notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            else -> viewModel.handleEvent(event)
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver when the activity is destroyed
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering service state receiver", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_FROM_NOTIFICATION") {
            Log.d(TAG, "App opened from notification")

            // Get any extras if needed
            val serviceType = intent.getStringExtra("SERVICE_TYPE")
            Log.d(TAG, "Service type: $serviceType")

            // Set flag in viewModel to handle in Composable
            viewModel.pendingNotificationAction = true

            // You can also directly navigate via viewModel
            viewModel.handleEvent(MainUiEvent.NavigateToServiceStatus)
        }
    }
}