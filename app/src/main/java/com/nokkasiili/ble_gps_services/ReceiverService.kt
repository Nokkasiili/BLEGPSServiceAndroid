package com.nokkasiili.ble_gps_services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nokkasiili.ble_gps_services.Constants.ACTION_STOP_RECEIVER_SERVICE
import com.nokkasiili.ble_gps_services.Constants.BLE_SCAN_CYCLE_MS
import com.nokkasiili.ble_gps_services.Constants.MAX_DELAY_SAMPLES
import com.nokkasiili.ble_gps_services.Constants.NO_DATA_TIMEOUT_MS
import com.nokkasiili.ble_gps_services.ServiceStateReceiver.Companion.EXTRA_SERVICE_TYPE
import com.nokkasiili.ble_gps_services.ServiceStateReceiver.Companion.SERVICE_TYPE_RECEIVER
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReceiverService : LifecycleService() {
    private var isRunning = false
    private var lastReceivedLocation: Location? = null
    private var scanJob: Job? = null
    private var noDataTimeoutJob: Job? = null
    val maxAllowedLagMs = 1000L
    val MAX_ACCEPTABLE_DELAY_MS = Constants.BROADCAST_INTERVAL + maxAllowedLagMs
    private val scanDelays = ArrayDeque<Long>() // Holds the last N scan ages


    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var locationManager: LocationManager? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanAgeMs = (SystemClock.elapsedRealtimeNanos() - result.timestampNanos) / 1_000_000

            // Update rolling average
            if (scanDelays.size >= MAX_DELAY_SAMPLES) scanDelays.removeFirst()
            scanDelays.addLast(scanAgeMs)

            val averageDelay = scanDelays.average().toLong()

            Log.d(TAG, "Scan age: ${scanAgeMs}ms, Rolling average: ${averageDelay}ms")

            // Check if scan is too old
            if (scanAgeMs > MAX_ACCEPTABLE_DELAY_MS) {
                Log.w(TAG, "Ignoring stale result ($scanAgeMs ms old)")
                return
            }

            val scanRecord = result.scanRecord ?: return
            val manufacturerData = scanRecord.getManufacturerSpecificData(Constants.MANUFACTURER_ID)
            manufacturerData?.let {
                LocationUtils.byteArrayToLocation(it)?.also { loc ->
                    lastReceivedLocation = loc
                    resetNoDataTimeout()
                    updateNotification(loc)
                    setMockLocation(loc)
                    Log.d(TAG, "Received location: ${loc.latitude}, ${loc.longitude}")
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            if (errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {
                stopScanning()
                startScanning()
            }
        }
    }


    private val stopServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "stopServiceReceiver onReceive CALLED. Action: ${intent.action}")
            if (intent.action == ACTION_STOP_RECEIVER_SERVICE) {
                Log.d(TAG, "Received stop service request from notification")

                stopSelf()
            } else {
                Log.w(TAG, "stopServiceReceiver received unexpected action: ${intent.action}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createNotificationChannels(this)
        registerReceiver(stopServiceReceiver, IntentFilter(ACTION_STOP_RECEIVER_SERVICE), RECEIVER_NOT_EXPORTED)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!isRunning) {
            startForeground(Constants.RECEIVER_NOTIFICATION_ID,
                NotificationUtils.createReceiverNotification(this,MainActivity::class.java))

            isRunning = true
            sendServiceStateBroadcast(true)


            setupBleScanning()


        }
        return START_STICKY
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy. Current isRunning state: $isRunning")
        // ... your cleanup logic ...
        if (isRunning) {
            isRunning = false
            Log.d(TAG, "ReceiverService transitioning to stopped state.")
            sendServiceStateBroadcast(false)
        } else {
            Log.d(TAG, "ReceiverService onDestroy called, but was already considered not running.")
        }

        stopScanning()
        scanJob?.cancel() // Also cancel the scanJob
        noDataTimeoutJob?.cancel()
        try {
            unregisterReceiver(stopServiceReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering stopServiceReceiver", e)
        }
        Log.d(TAG, "ReceiverService fully destroyed.")
        super.onDestroy() // Call super last
    }

    private fun sendServiceStateBroadcast(isStarting: Boolean) {
        val intent = Intent().apply {
            // Use the actions from Constants.kt that your ViewModel is expecting
            action = if (isStarting) Constants.ACTION_RECEIVER_SERVICE_STARTED else Constants.ACTION_RECEIVER_SERVICE_STOPPED
            // This part for differentiating service types in the ViewModel seems fine IF
            // your ViewModel actually uses EXTRA_SERVICE_TYPE to distinguish.
            // If not, it might be simpler to have completely distinct action strings
            // (e.g., ACTION_RECEIVER_STARTED, ACTION_BROADCAST_STARTED) and remove the extra.
            putExtra(EXTRA_SERVICE_TYPE, SERVICE_TYPE_RECEIVER)
            `package` = packageName // Good practice to add this!
        }
        sendBroadcast(intent)
        Log.d(TAG, "ReceiverService: Sending broadcast: ${intent.action} with package ${intent.`package`}")
    }


    private fun setupBleScanning() {
        if (bluetoothAdapter?.bluetoothLeScanner == null) {
            Log.e(TAG, "BLE not supported")
            stopSelf()
            return
        }
        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        startScanning()
        resetNoDataTimeout()
        scanJob = lifecycleScope.launch {
            while (isActive && isRunning) {
                delay(BLE_SCAN_CYCLE_MS)
                Log.d(TAG, "Restarting BLE scan")
                stopScanning()
                delay(500)
                startScanning()
                resetNoDataTimeout() // Reset timeout after restarting scan
            }
        }
    }

    private fun startScanning() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLE scan permission missing")
            return
        }
        try {
            val filter = ScanFilter.Builder()
                .setManufacturerData(Constants.MANUFACTURER_ID, ByteArray(0))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
            bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "BLE scan started")
        } catch (e: Exception) {
            Log.e(TAG, "startScan error", e)
        }
    }

    private fun stopScanning() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) return
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "BLE scan stopped")
        } catch (e: Exception) {
            Log.e(TAG, "stopScan error", e)
        }
    }

    private fun setMockLocation(location: Location) {
        val gpsProvider = LocationManager.GPS_PROVIDER
        try {
            // Remove any existing test provider
            if (locationManager!!.allProviders.contains(gpsProvider)) {
                try { locationManager!!.removeTestProvider(gpsProvider) } catch (_: Exception) {}
            }

            // Add & enable fresh test provider
            locationManager!!.addTestProvider(
                gpsProvider,
                false, false, false, false,
                true, true, true,
                ProviderProperties.POWER_USAGE_HIGH,
                ProviderProperties.ACCURACY_FINE
            )
            locationManager!!.setTestProviderEnabled(gpsProvider, true)


            // Build mock location
            val mockLoc = Location(gpsProvider).apply {
                latitude = location.latitude
                longitude = location.longitude
                altitude = if (location.hasAltitude()) location.altitude else 0.0
                accuracy = if (location.hasAccuracy()) location.accuracy else 5f
                bearing = if (location.hasBearing()) location.bearing else 0f
                speed = if (location.hasSpeed()) location.speed else 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }

            // Push location & status
            locationManager!!.setTestProviderLocation(gpsProvider, mockLoc)


            Log.d(TAG, "Mock location set: ${mockLoc.latitude}, ${mockLoc.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setMockLocation()", e)
        }
    }

    private fun updateNotification(location: Location) {
        val notify = NotificationUtils.updateReceiverNotification(
            this, MainActivity::class.java,location.latitude, location.longitude
        )
        val nm = getSystemService(NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        nm.notify(Constants.RECEIVER_NOTIFICATION_ID, notify)
    }

    private fun resetNoDataTimeout() {
        noDataTimeoutJob?.cancel()
        noDataTimeoutJob = lifecycleScope.launch {
            delay(NO_DATA_TIMEOUT_MS)
            if (isRunning && lastReceivedLocation == null) {
                Log.w(TAG, "No data received within timeout period. Stopping service.")
                stopSelf()
            } else if (isRunning) {
                // If we received data, reset the lastReceivedLocation for the next timeout check
                // This ensures that if data stops flowing again, we will detect it.
                // Alternatively, only reset if a new scan cycle starts.
                // For now, assume any data reception resets the timer entirely.
                lastReceivedLocation = null // Or handle this based on desired logic
            }
        }
    }

    companion object {
        private const val TAG = "ReceiverService"
    }
}