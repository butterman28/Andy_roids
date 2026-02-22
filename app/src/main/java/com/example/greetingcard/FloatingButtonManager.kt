package com.example.greetingcard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * Manages the floating button service state and provides toggle functionality
 */
object FloatingButtonManager {

    private const val PREFS_NAME = "floating_button_prefs"
    private const val KEY_FLOATING_BUTTON_ENABLED = "floating_button_enabled"
    private const val TAG = "FloatingButtonManager"

    private lateinit var sharedPrefs: SharedPreferences

    /**
     * Initialize the manager with application context
     */
    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if floating button is currently enabled
     */
    fun isFloatingButtonEnabled(): Boolean {
        return if (::sharedPrefs.isInitialized) {
            sharedPrefs.getBoolean(KEY_FLOATING_BUTTON_ENABLED, true) // Default to enabled
        } else {
            true
        }
    }

    /**
     * Toggle floating button on/off
     */
    fun toggleFloatingButton(context: Context): Boolean {
        val currentState = isFloatingButtonEnabled()
        val newState = !currentState

        // Save new state
        sharedPrefs.edit()
            .putBoolean(KEY_FLOATING_BUTTON_ENABLED, newState)
            .apply()

        // Start or stop the service based on new state
        if (newState) {
            startFloatingService(context)
            Log.d(TAG, "Floating button enabled")
        } else {
            stopFloatingService(context)
            Log.d(TAG, "Floating button disabled")
        }

        return newState
    }

    /**
     * Enable floating button
     */
    fun enableFloatingButton(context: Context) {
        if (!isFloatingButtonEnabled()) {
            sharedPrefs.edit()
                .putBoolean(KEY_FLOATING_BUTTON_ENABLED, true)
                .apply()

            startFloatingService(context)
            Log.d(TAG, "Floating button enabled")
        }
    }

    /**
     * Disable floating button
     */
    fun disableFloatingButton(context: Context) {
        if (isFloatingButtonEnabled()) {
            sharedPrefs.edit()
                .putBoolean(KEY_FLOATING_BUTTON_ENABLED, false)
                .apply()

            stopFloatingService(context)
            Log.d(TAG, "Floating button disabled")
        }
    }

    /**
     * Start the floating service if it should be running
     */
    fun startFloatingServiceIfEnabled(context: Context) {
        if (isFloatingButtonEnabled()) {
            startFloatingService(context)
        }
    }

    /**
     * Start the floating widget service
     */
    private fun startFloatingService(context: Context) {
        try {
            val intent = Intent(context, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start floating service", e)
        }
    }

    /**
     * Stop the floating widget service
     */
    private fun stopFloatingService(context: Context) {
        try {
            val intent = Intent(context, FloatingWidgetService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop floating service", e)
        }
    }

    /**
     * Get status text for UI display
     */
    fun getStatusText(): String {
        return if (isFloatingButtonEnabled()) {
            "Floating Button: ON"
        } else {
            "Floating Button: OFF"
        }
    }
}