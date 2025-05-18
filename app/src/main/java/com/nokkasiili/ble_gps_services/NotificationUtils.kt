package com.nokkasiili.ble_gps_services
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nokkasiili.ble_gps_services.Constants.ACTION_STOP_BROADCAST_SERVICE
import com.nokkasiili.ble_gps_services.Constants.ACTION_STOP_RECEIVER_SERVICE
import com.nokkasiili.ble_gps_services.Constants.BROADCAST_CHANNEL_ID
import com.nokkasiili.ble_gps_services.Constants.RECEIVER_CHANNEL_ID

object NotificationUtils {

    private object NotificationConstants {
        val BROADCAST_CHANNEL_NAME_RES_ID = R.string.broadcast_channel_name // Example
        val BROADCAST_CHANNEL_DESC_RES_ID = R.string.broadcast_channel_description // Example
        val RECEIVER_CHANNEL_NAME_RES_ID = R.string.receiver_channel_name // Example
        val RECEIVER_CHANNEL_DESC_RES_ID = R.string.receiver_channel_description // Example

        val BROADCAST_NOTIFY_TITLE_RES_ID = R.string.broadcast_notification_title
        val BROADCAST_NOTIFY_TEXT_INITIAL_RES_ID = R.string.broadcast_notification_text_initial
        val BROADCAST_NOTIFY_TEXT_UPDATED_RES_ID = R.string.broadcast_notification_text_updated

        val RECEIVER_NOTIFY_TITLE_RES_ID = R.string.receiver_notification_title
        val RECEIVER_NOTIFY_TEXT_INITIAL_RES_ID = R.string.receiver_notification_text_initial
        val RECEIVER_NOTIFY_TEXT_UPDATED_RES_ID = R.string.receiver_notification_text_updated

        // Button text resources
        val STOP_SERVICE_BUTTON_TEXT_RES_ID = R.string.stop_service_button_text

        // Use a proper small icon for notifications, not the launcher background
        val NOTIFICATION_ICON_RES_ID = R.drawable.ic_skull
    }

    /**
     * Creates the notification channels required for the application.
     * Should be called early in the application lifecycle, e.g., in Application.onCreate().
     * Safe to call multiple times.
     */
    fun createNotificationChannels(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createActualChannels(context, notificationManager)

    }

    private fun createActualChannels(context: Context, notificationManager: NotificationManager) {
        // Broadcast channel
        val broadcastChannel = NotificationChannel(
            BROADCAST_CHANNEL_ID,
            context.getString(NotificationConstants.BROADCAST_CHANNEL_NAME_RES_ID),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(NotificationConstants.BROADCAST_CHANNEL_DESC_RES_ID)
            setShowBadge(false) // Optional: depends on your needs
        }

        // Receiver channel
        val receiverChannel = NotificationChannel(
            RECEIVER_CHANNEL_ID,
            context.getString(NotificationConstants.RECEIVER_CHANNEL_NAME_RES_ID),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(NotificationConstants.RECEIVER_CHANNEL_DESC_RES_ID)
            setShowBadge(false) // Optional: depends on your needs
        }

        notificationManager.createNotificationChannels(listOf(broadcastChannel, receiverChannel))
    }

    /**
     * Helper function to build a base notification.
     *
     * @param context The application context.
     * @param channelId The ID of the notification channel to use (ignored on pre-Oreo).
     * @param title The title of the notification.
     * @param text The main content text of the notification.
     * @param mainActivityClass The main activity class to open when notification is tapped.
     * @param stopServiceAction The action intent to stop the service.
     * @return A configured NotificationCompat.Builder.
     */
    private fun getBaseNotificationBuilder(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        mainActivityClass: Class<*>,
        stopServiceAction: String
    ): NotificationCompat.Builder {


        val openAppIntent = Intent(context, mainActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add action to identify this is coming from notification
            action = "OPEN_FROM_NOTIFICATION"
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a PendingIntent for the stop service button

        // In NotificationUtils
        val stopServiceIntent = Intent(stopServiceAction).apply {
            `package` = context.packageName // Explicitly target your app
        }
        val stopServicePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(NotificationConstants.NOTIFICATION_ICON_RES_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW) // For ongoing background services
            .setOngoing(true) // Makes the notification non-dismissible
            .setContentIntent(openAppPendingIntent) // Open app on tap
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(NotificationConstants.STOP_SERVICE_BUTTON_TEXT_RES_ID),
                stopServicePendingIntent
            )
    }

    fun createBroadcastNotification(context: Context, mainActivityClass: Class<*>): Notification {
        return getBaseNotificationBuilder(
            context,
            BROADCAST_CHANNEL_ID,
            context.getString(NotificationConstants.BROADCAST_NOTIFY_TITLE_RES_ID),
            context.getString(NotificationConstants.BROADCAST_NOTIFY_TEXT_INITIAL_RES_ID),
            mainActivityClass,
            ACTION_STOP_BROADCAST_SERVICE
        ).build()
    }

    fun createReceiverNotification(context: Context, mainActivityClass: Class<*>): Notification {
        return getBaseNotificationBuilder(
            context,
            RECEIVER_CHANNEL_ID,
            context.getString(NotificationConstants.RECEIVER_NOTIFY_TITLE_RES_ID),
            context.getString(NotificationConstants.RECEIVER_NOTIFY_TEXT_INITIAL_RES_ID),
            mainActivityClass,
            ACTION_STOP_RECEIVER_SERVICE
        ).build()
    }

    fun updateBroadcastNotification(
        context: Context,
        mainActivityClass: Class<*>,
        latitude: Double,
        longitude: Double
    ): Notification {
        val contentText = context.getString(
            NotificationConstants.BROADCAST_NOTIFY_TEXT_UPDATED_RES_ID,
            latitude,
            longitude
        )
        return getBaseNotificationBuilder(
            context,
            BROADCAST_CHANNEL_ID,
            context.getString(NotificationConstants.BROADCAST_NOTIFY_TITLE_RES_ID),
            contentText,
            mainActivityClass,
            ACTION_STOP_BROADCAST_SERVICE
        ).build()
    }

    fun updateReceiverNotification(
        context: Context,
        mainActivityClass: Class<*>,
        latitude: Double,
        longitude: Double
    ): Notification {
        val contentText = context.getString(
            NotificationConstants.RECEIVER_NOTIFY_TEXT_UPDATED_RES_ID,
            latitude,
            longitude
        )
        return getBaseNotificationBuilder(
            context,
            RECEIVER_CHANNEL_ID,
            context.getString(NotificationConstants.RECEIVER_NOTIFY_TITLE_RES_ID),
            contentText,
            mainActivityClass,
            ACTION_STOP_RECEIVER_SERVICE
        ).build()
    }
}