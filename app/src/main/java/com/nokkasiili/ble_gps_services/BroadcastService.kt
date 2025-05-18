package com.nokkasiili.ble_gps_services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nokkasiili.ble_gps_services.Constants.ACTION_STOP_BROADCAST_SERVICE
import com.nokkasiili.ble_gps_services.ServiceStateReceiver.Companion.EXTRA_SERVICE_TYPE
import com.nokkasiili.ble_gps_services.ServiceStateReceiver.Companion.SERVICE_TYPE_BROADCAST
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BroadcastService : LifecycleService() {
    private companion object {
        const val TAG = "BroadcastService"
    }
    private var lastAdUpdateTime = 0L


    private var isRunning = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                currentLocation = location
                updateLocationAdvertisement(location)
                updateNotification(location)
                Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE Advertising started successfully")
            // Note: Service 'isRunning' and START broadcast are handled in onStartCommand
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed to start with error: $errorCode")
            // If advertising fails, the service should stop itself.
            // onDestroy will handle sending the ACTION_BROADCAST_SERVICE_STOPPED broadcast.
            if (isRunning) { // Only stop if we thought we were running
                Log.d(TAG, "Stopping service due to advertising start failure.")
                stopSelf()
            }
        }
    }
    private fun hasRequiredPermissions(): Boolean {
        val fineLocationGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val btAdvertiseGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED

        // Add BLUETOOTH_CONNECT if needed for specific operations, though advertise primarily needs ADVERTISE
        // val btConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //     ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        // } else true

        if (!fineLocationGranted) Log.w(TAG, "Missing ACCESS_FINE_LOCATION permission")
        if (!btAdvertiseGranted) Log.w(TAG, "Missing BLUETOOTH_ADVERTISE permission")

        return fineLocationGranted && btAdvertiseGranted
    }

    private fun sendServiceStateBroadcast(isStarting: Boolean) {
        val intent = Intent().apply {
            action = if (isStarting) Constants.ACTION_BROADCAST_SERVICE_STARTED else Constants.ACTION_BROADCAST_SERVICE_STOPPED
            putExtra(EXTRA_SERVICE_TYPE, SERVICE_TYPE_BROADCAST) // This extra is fine if your ViewModel uses it to differentiate
            `package` = packageName // Add this!
        }
        Log.d(TAG, "Sending broadcast: ${intent.action} with package ${intent.`package`}")
        sendBroadcast(intent)
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        NotificationUtils.createNotificationChannels(this)
        registerReceiver(stopServiceReceiver, IntentFilter(ACTION_STOP_BROADCAST_SERVICE), RECEIVER_NOT_EXPORTED)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }


    private val stopServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "stopServiceReceiver onReceive CALLED. Action: ${intent.action}")
            if (intent.action == ACTION_STOP_BROADCAST_SERVICE) {
                Log.d(TAG, "Received stop service request from notification")

                sendServiceStateBroadcast(false)
                stopSelf()
            } else {
                Log.w(TAG, "stopServiceReceiver received unexpected action: ${intent.action}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand, isRunning: $isRunning")

        synchronized(this) {
            if (!isRunning) { // Check if already running to prevent multiple initializations
                // Check for necessary permissions before starting
                if (!hasRequiredPermissions()) {
                    Log.e(TAG, "Required permissions not granted. Stopping service.")
                    // No need to set isRunning = true or send STARTED broadcast
                    stopSelf() // onDestroy will send STOPPED if it was somehow running, or just clean up
                    return START_NOT_STICKY // Or START_STICKY if you want it to retry later (after permissions are granted)
                }

                if (bluetoothAdapter == null || bluetoothAdapter?.bluetoothLeAdvertiser == null) {
                    Log.e(
                        TAG,
                        "Bluetooth adapter or BLE Advertiser not available. Stopping service."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }


                val notification = NotificationUtils.createBroadcastNotification(
                    this, MainActivity::class.java
                )
                startForeground(Constants.BROADCAST_NOTIFICATION_ID, notification)

                isRunning = true // Set state AFTER successful foreground start
                Log.d(TAG, "BroadcastService transitioning to running state.")
                sendServiceStateBroadcast(true) // <<< SEND BROADCAST (STARTED)

                startLocationUpdates()
                setupBleAdvertising() // This will internally use advertiseCallback
            } else {
                Log.d(TAG, "BroadcastService already running.")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        sendServiceStateBroadcast(false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopLocationUpdates()
        stopBleAdvertising()

        try {
            unregisterReceiver(stopServiceReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startLocationUpdates() {
        // Permission check is now in hasRequiredPermissions() called in onStartCommand
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, Constants.BROADCAST_INTERVAL
        ).build()

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // This should ideally not be reached if hasRequiredPermissions() is checked first
                Log.e(TAG, "Location permission check failed unexpectedly in startLocationUpdates.")
                stopSelf()
                return
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
            Log.d(TAG, "Location updates started.")
        } catch (e: SecurityException) { // Catch SecurityException specifically
            Log.e(TAG, "SecurityException while requesting location updates. Missing permission?", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Could not request location updates", e)
            stopSelf()
        }
    }


    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }


    private fun setupBleAdvertising() {
        // Permission and adapter checks are now in onStartCommand / hasRequiredPermissions
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        // Start with initial empty advertisement or wait for first location
        // For simplicity, let's assume we start advertising immediately if possible,
        // or it will be updated once location is available.
        currentLocation?.let {
            updateLocationAdvertisement(it)
        } ?: startAdvertising(ByteArray(0)) // Start with empty data if no location yet


        // Periodically update advertisement in case location updates are slow or stop
        lifecycleScope.launch {
            while (isRunning) { // Use the service's isRunning flag
                currentLocation?.let {
                    Log.d(TAG, "Periodic BLE advertisement update with current location.")
                    updateLocationAdvertisement(it)
                }
                delay(Constants.BROADCAST_INTERVAL) // Or a different interval for periodic refresh
            }
            Log.d(TAG, "Periodic BLE update coroutine finishing as service is no longer running.")
        }
    }


    private fun updateLocationAdvertisement(location: Location) {
        if (!isRunning) return // Don't try to advertise if service is not running

        val now = System.currentTimeMillis()
        if (now - lastAdUpdateTime < Constants.BROADCAST_INTERVAL) return
        lastAdUpdateTime = now

        val locationBytes = LocationUtils.locationToByteArray(location)
        startAdvertising(locationBytes)
    }

    private fun startAdvertising(data: ByteArray) {
        if (bluetoothLeAdvertiser == null || !isRunning) {
            Log.w(TAG, "startAdvertising: BLE advertiser is null or service not running. Advertiser: $bluetoothLeAdvertiser, isRunning: $isRunning")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startAdvertising: Missing BLUETOOTH_ADVERTISE permission. This should have been caught earlier.")
            stopSelf() // Stop service if permissions are suddenly missing
            return
        }

        try {
            // Stop any existing advertisement first to ensure clean start
            // Call the internal stop to avoid re-checking permissions if we are already stopping
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) // Use the same callback instance

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(Constants.MANUFACTURER_ID, data)
                .build()

            Log.d(TAG, "Attempting to start BLE advertising with data length: ${data.size}")
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while starting advertising. Missing permission?", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
            // Consider if stopSelf() is appropriate here or just log
        }
    }

    private fun stopBleAdvertisingInternal() {
        if (bluetoothLeAdvertiser == null) {
            Log.d(TAG, "stopBleAdvertisingInternal: BluetoothLeAdvertiser is null, nothing to stop.")
            return
        }
        try {
            // No need to check BLUETOOTH_ADVERTISE permission here if we're just stopping internally
            // However, system might still require it for stopAdvertising on some versions.
            // For safety, keep the check or ensure this is only called when appropriate.
            if (ActivityCompat.checkSelfPermission( this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                Log.d(TAG, "BLE advertising stopped via internal call.")
            } else {
                Log.w(TAG, "stopBleAdvertisingInternal: Missing BLUETOOTH_ADVERTISE permission, cannot stop advertising gracefully.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while stopping advertising. Missing permission?", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE advertising internally", e)
        }
    }
    private fun stopBleAdvertising() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "stopBleAdvertising: Missing BLUETOOTH_ADVERTISE permission.")
            return
        }
        stopBleAdvertisingInternal()
    }

    private fun updateNotification(location: Location) {
        if (!isRunning) return // Don't update notification if service isn't running
        val notification = NotificationUtils.updateBroadcastNotification(
            this, MainActivity::class.java, location.latitude, location.longitude
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        try {
            notificationManager.notify(Constants.BROADCAST_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
}