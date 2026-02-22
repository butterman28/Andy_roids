package com.example.greetingcard

import android.content.ComponentName
import android.Manifest // Required for Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager // Required for PackageManager.PERMISSION_GRANTED
// ... other imports ...
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arthenica.ffmpegkit.FFmpegKit
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.greetingcard.player.MusicService
import com.example.greetingcard.ui.navigation.AppNavigation
import com.example.greetingcard.ui.theme.GreetingCardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1000
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001 // <-- ADD THIS
        private const val TAG = "MainActivity"

    }

// ... other imports

// Inside the MainActivity class:

// Companion object definitions remain the same, including NOTIFICATION_PERMISSION_REQUEST_CODE

    private fun requestNotificationPermission() {
        // Only necessary for Android 13 (TIRAMISU) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is NOT granted, request it from the user.
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
        // If the permission is already granted or the device is older, do nothing.
    }

    // The MediaController that talks to MusicService (Media3)
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize services, python, floating button, etc.
        initializeApp()

        // Enable edge-to-edge now. We will set Compose content once MediaController is ready
        enableEdgeToEdge()
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()

        // Build MediaController asynchronously and only set UI when ready
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()

                // Now that mediaController is ready, set Compose UI and pass controller down
                setContent {
                    GreetingCardTheme {
                        // AppContent requires a non-null MediaController
                        AppContent(mediaController = mediaController!!)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to get MediaController", t)
                // Fallback UI: show app without mediaController (optional)
                setContent {
                    GreetingCardTheme {
                        // If you want a fallback UI without controller, call AppNavigation with null
                        // but the code below expects non-null, so we just log the error.
                        AppContentFallback()
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        // Release MediaController when activity stops
        mediaController?.release()
        mediaController = null
        super.onStop()
    }

    private fun initializeApp() {
        // Initialize FloatingButtonManager
        FloatingButtonManager.initialize(this)

        requestNotificationPermission()
        // Start Python runtime if not already started
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Handle shared content (if the Activity was started with an intent)
        handleSharedIntent(intent)

        // Request overlay permission and start floating service if enabled
        requestOverlayPermissionIfNeeded()
    }

    // --- UI composables used by setContent above ----------------

    // AppContent expects a non-null mediaController (the main flow)
    @Composable
    private fun AppContent(mediaController: MediaController) {
        var floatingButtonEnabled by remember {
            mutableStateOf(FloatingButtonManager.isFloatingButtonEnabled())
        }

        val context = LocalContext.current

        AppNavigation(
            floatingButtonEnabled = floatingButtonEnabled,
            onFloatingButtonToggle = { newState ->
                handleFloatingButtonToggle(newState) { updatedState ->
                    floatingButtonEnabled = updatedState
                }
            },
            onDownload = { url ->DownloadManager.downloadAudio(this, url)},
                //downloadAudio(url) },
            appContext = context,
            mediaController = mediaController // pass the controller down
        )
    }

    // Minimal fallback UI if controller initialization fails unexpectedly
    @Composable
    private fun AppContentFallback() {
        // keep simple so app doesn't crash if controller fails
        MaterialTheme {
            AppNavigation(
                floatingButtonEnabled = false,
                onFloatingButtonToggle = {},
                onDownload = {},
                appContext = this@MainActivity,
                mediaController = null // If you want AppNavigation to accept null, update its signature
            )
        }
    }

    // ----------------------------------------------------------------

    private fun handleFloatingButtonToggle(
        newState: Boolean,
        onStateUpdated: (Boolean) -> Unit
    ) {
        Log.d(TAG, "Toggle clicked, changing to $newState")

        if (newState) {
            FloatingButtonManager.enableFloatingButton(this)
        } else {
            FloatingButtonManager.disableFloatingButton(this)
        }

        onStateUpdated(newState)

        Log.d(TAG, "State updated to: $newState")

        Toast.makeText(
            this,
            if (newState) "Floating button enabled" else "Floating button disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (Settings.canDrawOverlays(this)) {
            FloatingButtonManager.startFloatingServiceIfEnabled(this)
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                handleTextShare(intent)
            }
            intent?.action == Intent.ACTION_SEND -> {
                handleOtherShare(intent)
            }
        }
    }

    private fun handleTextShare(intent: Intent) {
        val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedUrl != null && isYouTubeUrl(sharedUrl)) {
            Log.d(TAG, "Received YouTube URL via share: $sharedUrl")
            Toast.makeText(
                this,
                "YouTube URL received: ${sharedUrl.take(50)}...",
                Toast.LENGTH_LONG
            ).show()

            YoutubeAccessibilityService.currentUrl = sharedUrl
            DownloadManager.downloadAudio(this, sharedUrl)
            //downloadAudio(sharedUrl)
            finish()
        }
    }

    private fun handleOtherShare(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        Log.d(TAG, "Received shared content: $sharedText")

        sharedText?.let { text ->
            if (text.startsWith("youtube_music_search:")) {
                val songInfo = text.removePrefix("youtube_music_search:")
                Toast.makeText(this, "Song info received: $songInfo", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Received song info: $songInfo")

                searchAndDownloadSong(songInfo)
                finish()
            }
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    private fun searchAndDownloadSong(songInfo: String) {
        Toast.makeText(this, "Would search for: $songInfo", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Song to search: $songInfo")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE && Settings.canDrawOverlays(this)) {
            FloatingButtonManager.startFloatingServiceIfEnabled(this)
        }
    }}

