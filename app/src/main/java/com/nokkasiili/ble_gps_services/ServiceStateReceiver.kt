package com.nokkasiili.ble_gps_services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that listens for service state changes to update the UI
 */
class ServiceStateReceiver(private val onServiceStateChanged: (isRunning: Boolean, serviceType: String) -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when(intent.action) {
            ACTION_SERVICE_STOPPED -> {
                val serviceType = intent.getStringExtra(EXTRA_SERVICE_TYPE) ?: ""
                Log.d(TAG, "Service stopped: $serviceType")
                onServiceStateChanged(false, serviceType)
            }
            ACTION_SERVICE_STARTED -> {
                val serviceType = intent.getStringExtra(EXTRA_SERVICE_TYPE) ?: ""
                Log.d(TAG, "Service started: $serviceType")
                onServiceStateChanged(true, serviceType)
            }
        }
    }

    companion object {
        private const val TAG = "ServiceStateReceiver"
        // These constants must match what's used in your services
        const val ACTION_SERVICE_STARTED = "com.example.myapplication.SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "com.example.myapplication.SERVICE_STOPPED"
        const val EXTRA_SERVICE_TYPE = "service_type"
        const val SERVICE_TYPE_RECEIVER = "receiver"
        const val SERVICE_TYPE_BROADCAST = "broadcast"
    }
}