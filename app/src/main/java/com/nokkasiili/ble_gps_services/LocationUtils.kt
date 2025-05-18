package com.nokkasiili.ble_gps_services

import android.location.Location
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LocationUtils {
    private const val TAG = "LocationUtils"
    // 6 bytes APP_ID + 13 bytes data = 19 total
    private const val DATA_SIZE = 4 + 4 + 2 + 1 + 1 + 1
    private val TOTAL_SIZE = Constants.APP_IDENTIFIER.size + DATA_SIZE

    fun locationToByteArray(location: Location): ByteArray {
        return try {
            val buf = ByteBuffer.allocate(TOTAL_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)

            // 1) APP_ID
            buf.put(Constants.APP_IDENTIFIER)

            // 2) Latitude & Longitude
            buf.putInt((location.latitude * 1_000_000).toInt())
            buf.putInt((location.longitude * 1_000_000).toInt())

            // 3) Altitude (offset to unsigned range)
            val alt = if (location.hasAltitude()) location.altitude.toInt() else 0
            buf.putShort((alt + 32767).toShort())

            // 4) Accuracy (m)
            val acc = if (location.hasAccuracy()) location.accuracy.toInt() else 0
            buf.put(acc.coerceIn(0, 255).toByte())

            // 5) Speed (km/h)
            val kmh = if (location.hasSpeed()) (location.speed * 3.6f).toInt() else 0
            buf.put(kmh.coerceIn(0, 255).toByte())

            // 6) Bearing (0–255 ≈ 0°–360°)
            val brg = if (location.hasBearing())
                ((location.bearing / 360f) * 256f).toInt()
            else 0
            buf.put(brg.coerceIn(0, 255).toByte())

            buf.array()
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding location", e)
            ByteArray(0)
        }
    }

    fun byteArrayToLocation(bytes: ByteArray): Location? {
        return try {
            if (bytes.size < TOTAL_SIZE) {
                Log.e(TAG, "Byte array too short: ${bytes.size} < $TOTAL_SIZE")
                return null
            }

            // Validate APP_ID
            for (i in Constants.APP_IDENTIFIER.indices) {
                if (bytes[i] != Constants.APP_IDENTIFIER[i]) {
                    Log.e(TAG, "Invalid APP_ID at index $i")
                    return null
                }
            }

            val buf = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply { position(Constants.APP_IDENTIFIER.size) }

            // 1) Lat & Lng
            val lat = buf.getInt() / 1_000_000.0
            val lng = buf.getInt() / 1_000_000.0

            // 2) Altitude
            val alt = buf.getShort().toInt() - 32767

            // 3) Accuracy
            val acc = buf.get().toInt() and 0xFF

            // 4) Speed (km/h → m/s)
            val spd = (buf.get().toInt() and 0xFF) / 3.6f

            // 5) Bearing
            val brg = (buf.get().toInt() and 0xFF) * (360f / 256f)

            return Location("BLE").apply {
                latitude  = lat
                longitude = lng
                altitude  = alt.toDouble()
                accuracy  = acc.toFloat()
                speed     = spd
                bearing   = brg
                time      = System.currentTimeMillis()
                elapsedRealtimeNanos = System.nanoTime()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding location", e)
            null
        }
    }
}
