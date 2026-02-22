package com.example.greetingcard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: ImageView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info) // your icon
            // Updated button click handler
            // Updated button click handler
            setOnClickListener {
                // Check if accessibility service is connected
                if (YoutubeAccessibilityService.instance == null) {
                    Toast.makeText(applicationContext, "Accessibility service not connected", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val pkg = YoutubeAccessibilityService.getCurrentPackage()
                Log.d("FloatingButton", "Current package: $pkg")

                when (pkg) {
                    "com.android.chrome", "org.mozilla.firefox" -> {
                        Toast.makeText(applicationContext, "Fetching browser URL...", Toast.LENGTH_SHORT).show()
                        YoutubeAccessibilityService.fetchBrowserUrl()
                        // ✅ Log clickables for Chrome/Firefox


                        // Give it a moment to fetch, then display result
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val url = YoutubeAccessibilityService.currentUrl
                            Toast.makeText(applicationContext, url ?: "No YouTube link detected", Toast.LENGTH_LONG).show()
                        }, 1000)
                    }
                    "com.google.android.apps.youtube.music", "com.google.android.youtube" -> {
                        Toast.makeText(applicationContext, "Triggering share flow...", Toast.LENGTH_SHORT).show()
                        // ✅ Log clickables for YouTube
                        ClickableLogger.logAllClickables(
                            YoutubeAccessibilityService.instance?.rootInActiveWindow,
                            pkg
                        )
                        YoutubeAccessibilityService.triggerShareFlow()
                        // URL will arrive via clipboard listener
                    }
                    null -> {
                        Toast.makeText(applicationContext, "Could not detect current app", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(applicationContext, "Unsupported app: $pkg", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingButton, params)

        // Make draggable + clickable
        floatingButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private val clickThreshold = 10 // px threshold for detecting click vs drag

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButton, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (abs(deltaX) < clickThreshold && abs(deltaY) < clickThreshold) {
                            // It's a click, not a drag
                            v?.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) windowManager.removeView(floatingButton)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "floating_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Floating Widget Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Floating Button Active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            startForeground(9999, notification)
        }
        return START_STICKY
    }
}
