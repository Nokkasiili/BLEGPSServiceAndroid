package com.nokkasiili.ble_gps_services

object Constants {
    const val MANUFACTURER_ID =  1001
    const val RECEIVER_NOTIFICATION_ID = 1002
    const val BROADCAST_NOTIFICATION_ID = 1003
    const val RECEIVER_SERVICE_NOTIFICATION_ID = 1004
    const val BROADCAST_SERVICE_NOTIFICATION_ID = 1005
    const val BROADCAST_CHANNEL_ID = "gps_broadcast_service_channel"
    const val RECEIVER_CHANNEL_ID = "gps_receiver_service_channel"

    // Action for stopping services
    const val ACTION_STOP_BROADCAST_SERVICE = "com.example.myapplication.STOP_BROADCAST_SERVICE"
    const val ACTION_STOP_RECEIVER_SERVICE = "com.example.myapplication.STOP_RECEIVER_SERVICE"
    const val ACTION_RECEIVER_SERVICE_STARTED = "com.example.myapplication.ACTION_RECEIVER_SERVICE_STARTED"
    const val ACTION_RECEIVER_SERVICE_STOPPED = "com.example.myapplication.ACTION_RECEIVER_SERVICE_STOPPED"

    const val ACTION_BROADCAST_SERVICE_STARTED = "com.example.myapplication.ACTION_BROADCAST_SERVICE_STARTED"
    const val ACTION_BROADCAST_SERVICE_STOPPED = "com.example.myapplication.ACTION_BROADCAST_SERVICE_STOPPED"
    const val BLE_SCAN_CYCLE_MS = 30_000L
    const val MAX_DELAY_SAMPLES = 10



    // Default values - these will be updated from SharedPreferences if set by the user
    var BROADCAST_INTERVAL: Long = 2000L
        private set // Make setter private so it can only be changed within this object (e.g., from a settings loader)
    var NO_DATA_TIMEOUT_MS: Long = 5 * 60 * 1000L // 5 minutes in milliseconds

        private set

    fun updateBroadcastInterval(newInterval: Long) { BROADCAST_INTERVAL = newInterval }
    fun updateNoDataTimeout(newTimeout: Long) { NO_DATA_TIMEOUT_MS = newTimeout }

// App identifier in BLE advertisement
    val APP_IDENTIFIER = byteArrayOf(0x42, 0x4C, 0x45, 0x47, 0x50, 0x53) // "BLEGPS" in hex
}