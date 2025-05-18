package com.nokkasiili.ble_gps_services

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Enum to represent different permission types for better organization
enum class PermissionType {
    LOCATION,
    BLUETOOTH,
    NOTIFICATION
}

sealed class MainUiEvent {
    object StartReceiver : MainUiEvent()
    object StopReceiver : MainUiEvent()
    object StartBroadcast : MainUiEvent()
    object StopBroadcast : MainUiEvent()
    data class RequestPermission(val type: PermissionType) : MainUiEvent()
    object DismissSettingsDialog : MainUiEvent()
    // Settings dialog events
    object ShowCustomSettingsDialog : MainUiEvent()
    object DismissCustomSettingsDialog : MainUiEvent()
    // Navigation event
    object NavigateToServiceStatus : MainUiEvent()
}

data class MainUiState(
    val isReceiverRunning: Boolean = false,
    val isBroadcastRunning: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val areBluetoothPermissionsGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showCustomSettingsDialog: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    // Cache application context for repeated use
    private val appContext: Context = getApplication<Application>().applicationContext

    // Flag to track if notification action is pending
    var pendingNotificationAction: Boolean = false
    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_RECEIVER_SERVICE_STARTED -> {
                    Log.d("MainViewModel", "Received ACTION_RECEIVER_SERVICE_STARTED")
                    updateReceiverStateInternal(true)
                }
                Constants.ACTION_RECEIVER_SERVICE_STOPPED -> {
                    Log.d("MainViewModel", "Received ACTION_RECEIVER_SERVICE_STOPPED")
                    updateReceiverStateInternal(false)
                }
                Constants.ACTION_BROADCAST_SERVICE_STARTED -> {
                    Log.d("MainViewModel", "Received ACTION_BROADCAST_SERVICE_STARTED")
                    updateBroadcastStateInternal(true)
                }
                Constants.ACTION_BROADCAST_SERVICE_STOPPED -> {
                    Log.d("MainViewModel", "Received ACTION_BROADCAST_SERVICE_STOPPED")
                    updateBroadcastStateInternal(false)
                }
            }
        }
    }

    init {
        // Scan for permissions on ViewModel initialization
        viewModelScope.launch {
            checkInitialPermissions()
        }
        // Initialize notification channels - this is still appropriate in the ViewModel
        // as it's a one-time setup that should happen early in the app lifecycle
        NotificationUtils.createNotificationChannels(appContext)
        val intentFilter = IntentFilter().apply {
            addAction(Constants.ACTION_RECEIVER_SERVICE_STARTED)
            addAction(Constants.ACTION_RECEIVER_SERVICE_STOPPED)
            addAction(Constants.ACTION_BROADCAST_SERVICE_STARTED)
            addAction(Constants.ACTION_BROADCAST_SERVICE_STOPPED)
        }
        // Use appContext to register the receiver, ensuring it lives with the ViewModel scope
        // For Android versions that require specifying receiver export status:
        appContext.registerReceiver(serviceStateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

        Log.d("MainViewModel", "ServiceStateReceiver registered.")


    }
    private fun updateReceiverStateInternal(isRunning: Boolean) {
        Log.d("MainViewModel", "Updating internal receiver state: $isRunning")
        if (_uiState.value.isReceiverRunning != isRunning) { // Update only if changed
            _uiState.update { it.copy(isReceiverRunning = isRunning) }
        }
    }

    private fun updateBroadcastStateInternal(isRunning: Boolean) {
        Log.d("MainViewModel", "Updating internal broadcast state: $isRunning")
        if (_uiState.value.isBroadcastRunning != isRunning) { // Update only if changed
            _uiState.update { it.copy(isBroadcastRunning = isRunning) }
        }
    }
    fun handleEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.RequestPermission -> {
                // Permissions are handled by the launcher in MainActivity
            }
            MainUiEvent.StartReceiver -> startReceiverService()
            MainUiEvent.StopReceiver -> stopReceiverService()
            MainUiEvent.StartBroadcast -> startBroadcastService()
            MainUiEvent.StopBroadcast -> stopBroadcastService()
            MainUiEvent.DismissSettingsDialog -> _uiState.update {
                it.copy(showSettingsDialog = false)
            }
            MainUiEvent.ShowCustomSettingsDialog -> _uiState.update {
                it.copy(showCustomSettingsDialog = true)
            }
            MainUiEvent.DismissCustomSettingsDialog -> _uiState.update {
                it.copy(showCustomSettingsDialog = false)
            }
            MainUiEvent.NavigateToServiceStatus -> {
                // This is handled in the UI by the LaunchedEffect in MainActivity
                Log.d("MainViewModel", "Navigating to service status")
            }
        }
    }

    fun updateReceiverState(isRunning: Boolean) {
        Log.d("MainViewModel", "Updating receiver state: $isRunning")
        _uiState.update { it.copy(isReceiverRunning = isRunning) }
    }

    fun updateBroadcastState(isRunning: Boolean) {
        Log.d("MainViewModel", "Updating broadcast state: $isRunning")
        _uiState.update { it.copy(isBroadcastRunning = isRunning) }
    }

    fun onPermissionResult(permissionType: PermissionType, granted: Boolean) {
        _uiState.update { currentState ->
            // Create base updated state
            when (permissionType) {
                PermissionType.LOCATION -> currentState.copy(isLocationPermissionGranted = granted)
                PermissionType.BLUETOOTH -> currentState.copy(areBluetoothPermissionsGranted = granted)
                PermissionType.NOTIFICATION -> currentState.copy(isNotificationPermissionGranted = granted)
            }
        }

        if (!granted) {
            // If permission was denied, show the settings dialog
            _uiState.update { it.copy(showSettingsDialog = true) }
        }
    }

    private fun checkInitialPermissions() {
        // Manually check permissions using Android's `checkSelfPermission` for initial state
        _uiState.update {
            it.copy(
                isLocationPermissionGranted = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED,
                areBluetoothPermissionsGranted = appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED &&
                        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED,
                isNotificationPermissionGranted = isNotificationPermissionGranted()
            )
        }
    }

    private fun areEssentialPermissionsGranted(): Boolean {
        return uiState.value.isLocationPermissionGranted && uiState.value.areBluetoothPermissionsGranted
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED

    }

    private fun startReceiverService() = viewModelScope.launch {
        if (!areEssentialPermissionsGranted()) {
            _uiState.update { it.copy(showSettingsDialog = true) }
            return@launch
        }

        // Check notification permission for foreground service since Android 13+
        if (!isNotificationPermissionGranted()) {
            // If we need notification permissions but don't have them
            _uiState.update { it.copy(showSettingsDialog = true) }
            return@launch
        }

        stopService(BroadcastService::class.java) // Stop the other service if running
        startForegroundServiceCompat(ReceiverService::class.java)
//        _uiState.update {
//            it.copy(isReceiverRunning = true, isBroadcastRunning = false)
//        }
        Log.d("MainViewModel", "StartReceiverService called, waiting for broadcast to update UI.")
    }

    private fun stopReceiverService() = viewModelScope.launch {
        stopService(ReceiverService::class.java)
        _uiState.update { it.copy(isReceiverRunning = false) }
    }

    private fun startBroadcastService() = viewModelScope.launch {
        if (!areEssentialPermissionsGranted()) {
            _uiState.update { it.copy(showSettingsDialog = true) }
            return@launch
        }

        // Check notification permission for foreground service since Android 13+
        if ( !isNotificationPermissionGranted()) {
            // If we need notification permissions but don't have them
            _uiState.update { it.copy(showSettingsDialog = true) }
            return@launch
        }

        stopService(ReceiverService::class.java) // Stop the other service if running
        startForegroundServiceCompat(BroadcastService::class.java)
        _uiState.update {
            it.copy(isBroadcastRunning = true, isReceiverRunning = false)
        }
    }

    private fun stopBroadcastService() = viewModelScope.launch {
        stopService(BroadcastService::class.java)
        _uiState.update { it.copy(isBroadcastRunning = false) }
    }

    // Generic helper to start a foreground service compatibly
    private fun <T : Any> startForegroundServiceCompat(serviceClass: Class<T>) {
        val intent = Intent(appContext, serviceClass)
        appContext.startForegroundService(intent)
    }

    // Generic helper to stop a service
    private fun <T : Any> stopService(serviceClass: Class<T>) {
        try {
            val intent = Intent(appContext, serviceClass)
            appContext.stopService(intent)
        } catch (e: Exception) {
            // Log the error or handle it as appropriate
            Log.e("ServiceUtils", "Failed to stop service ${serviceClass.simpleName}", e)
        }
    }
}