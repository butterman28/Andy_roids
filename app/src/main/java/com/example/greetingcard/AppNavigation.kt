package com.example.greetingcard.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.greetingcard.player.MusicService
import com.example.greetingcard.ui.AppNavigationBar
import com.example.greetingcard.ui.screens.*

object AppRoutes { const val HOME = "home"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PLAY = "play"
    const val PLAYLIST = "playlist"
}
@OptIn(UnstableApi::class)
@Composable
fun AppNavigation(
    floatingButtonEnabled: Boolean,
    onFloatingButtonToggle: (Boolean) -> Unit,
    onDownload: (String) -> Unit,
    appContext: Context,
    mediaController: MediaController?
) {
    var selectedRoute by remember { mutableStateOf(AppRoutes.HOME)}

    // ✅ Observe playback state from MediaController
    var isPlaying by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Unknown") }
    var artist by remember { mutableStateOf("Unknown Artist") }

    LaunchedEffect(mediaController) {
        mediaController?.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                title = player.mediaMetadata.title?.toString() ?: "Unknown"
                artist = player.mediaMetadata.artist?.toString() ?: "Unknown Artist"
            }
        })
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing, // 👈 important
        topBar = {
            Column ( modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing) ){
                if (selectedRoute != AppRoutes.PLAY) {
                    AppNavigationBar(
                        selectedRoute = selectedRoute,
                        onNavigate = { route ->
                            selectedRoute = route
                        }
                    )
                }
            }
        },
        bottomBar = {
            Column( modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing) ) {
                
                // ✅ Only show MiniPlayer if NOT in PlayScreen
                if (selectedRoute != AppRoutes.PLAY) {
                    AnimatedVisibility(
                        visible = title != "Unknown" || isPlaying,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300))
                    ) {
                        MiniPlayer(
                            title = title,
                            artist = artist,
                            isPlaying = isPlaying,
                            onExpand = { selectedRoute = AppRoutes.PLAY },
                            onTogglePlayPause = {
                                if (isPlaying) mediaController?.pause() else mediaController?.play()
                            },
                            onClose = {
                                mediaController?.stop()
                            }
                        )
                    }

                }
            }
        }

    ) { innerPadding ->
        //Box(modifier = Modifier.fillMaxSize()) {
            when (selectedRoute) {
                AppRoutes.HOME -> HomeScreen(
                    floatingButtonEnabled = floatingButtonEnabled,
                    onFloatingButtonToggle = onFloatingButtonToggle,
                    onDownload = onDownload,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding).offset(y = (-20).dp)
                )

                AppRoutes.DOWNLOADS -> DownloadsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding).offset(y = (-20).dp),
                    onPlayFile = { file, fileList ->
                        val startIndex = fileList.indexOf(file)

                        val intent = Intent(appContext, MusicService::class.java).apply {
                            putExtra("playlist", fileList.map { it.absolutePath }.toTypedArray())
                            putExtra("start_index", startIndex)
                        }
                        ContextCompat.startForegroundService(appContext, intent)

                        // Just navigate to PlayScreen — MediaController will already be in sync
                        selectedRoute = AppRoutes.PLAY
                    }


                )

                AppRoutes.SETTINGS -> SettingsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding).offset(y = (-20).dp)
                )

                AppRoutes.PLAY -> PlayScreen(
                    mediaController = mediaController!!, // 👈 pass the controller
                    onBack = { selectedRoute = AppRoutes.DOWNLOADS },
                    onMinimize = { selectedRoute = AppRoutes.DOWNLOADS },
                    //modifier = Modifier.padding(innerPadding).offset(y = (-20).dp)
                )
                AppRoutes.PLAYLIST -> PlaylistScreen(
                    mediaController = mediaController!!, // 👈 pass the controller
                    onBack = { selectedRoute = AppRoutes.DOWNLOADS },
                    onSongSelected = { selectedRoute = AppRoutes.PLAY },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding).offset(y = (-20).dp),
                    //onMinimize = { selectedRoute = AppRoutes.DOWNLOADS },
                    //modifier = Modifier.padding(innerPadding)
                )
            }
        }

}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onExpand() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { onTogglePlayPause() }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = { onClose() }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Preview(showSystemUi = true)
@Composable
fun PreviewAppNavigation() {
    val context = LocalContext.current
    MaterialTheme {
        AppNavigation(
            floatingButtonEnabled = false,
            onFloatingButtonToggle = {},
            onDownload = {},
            appContext = context,
            mediaController = null // Mocked; preview doesn't need playback
        )
    }
}
