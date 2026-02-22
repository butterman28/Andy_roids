package com.example.greetingcard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object DownloadNotificationManager {

    // Notification Channel IDs
    private const val CHANNEL_ID = "download_channel"
    private const val NOTIFICATION_ID = 101

    // --- 1. SETUP: Create the Notification Channel ---
    private fun createNotificationChannel(context: Context) {
        // Only necessary for Android 8.0 (Oreo) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Audio Downloads"
            val descriptionText = "Notifications for audio download and conversion status"
            val importance = NotificationManager.IMPORTANCE_LOW // Low priority for ongoing status
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // --- 2. NOTIFICATION MANAGER GETTER ---
    private fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // ===================================================================================
    // PUBLIC NOTIFICATION METHODS (The ones you call in DownloadManager)
    // ===================================================================================

    /**
     * Shows a non-progress notification indicating the download has started.
     */
    fun showDownloadStarted(context: Context, filename: String) {
        createNotificationChannel(context)
        val notificationManager = getNotificationManager(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Started")
            .setContentText("Downloading: $filename")
            .setSmallIcon(android.R.drawable.stat_sys_download) // Standard download icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Makes it non-dismissible while active
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Updates the notification with ongoing progress.
     */
    private fun updateProgress(context: Context, progress: Int) {
        val notificationManager = getNotificationManager(context)

        // Reuse the existing notification structure
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading Audio...")
            .setContentText("Progress: $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)  // Max 100, current progress, not indeterminate
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Simulates progress and calls a callback on completion.
     * NOTE: In a real app, this logic must be replaced with actual progress events
     * from your Python/network library.
     */
    suspend fun simulateProgressUpdates(context: Context, onComplete: () -> Unit) {
        withContext(Dispatchers.IO) {
            for (i in 0..100 step 10) {
                // Must switch to Main thread to update the UI (notification)
                withContext(Dispatchers.Main) {
                    updateProgress(context, i)
                }
                delay(500) // Wait half a second
            }
            onComplete()
        }
    }


    /**
     * Shows a status for the conversion stage.
     */
    fun showConversionStarted(context: Context) {
        val notificationManager = getNotificationManager(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Conversion In Progress")
            .setContentText("Optimizing audio to MP3...")
            .setSmallIcon(android.R.drawable.ic_popup_sync) // Sync icon for processing
            .setProgress(0, 0, true) // Indeterminate progress bar
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Shows a final, success notification.
     */
    fun showDownloadCompleted(context: Context, filename: String, filePath: String) {
        val notificationManager = getNotificationManager(context)

        // Remove the ongoing notification first
        notificationManager.cancel(NOTIFICATION_ID)

        val completedNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete 🎉")
            .setContentText("$filename saved!")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true) // Allow dismissal
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Use a unique ID for the completed notification so it doesn't conflict with the ongoing one
        notificationManager.notify(NOTIFICATION_ID + 1, completedNotification)
    }

    /**
     * Shows a final, failure notification.
     */
    fun showDownloadFailed(context: Context, reason: String) {
        val notificationManager = getNotificationManager(context)

        // Remove the ongoing notification first
        notificationManager.cancel(NOTIFICATION_ID)

        val failedNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed 🙁")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, failedNotification)
    }
}