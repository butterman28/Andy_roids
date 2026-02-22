package com.example.greetingcard.ui.screens

import android.content.Context
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.greetingcard.PlaylistManager
import com.example.greetingcard.ui.screens.PlaylistPickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlayScreen(
    mediaController: MediaController,
    onBack: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // 🎵 Playback state - Initialize from MediaController
    var isPlaying by remember { mutableStateOf(mediaController.isPlaying) }
    var title by remember { mutableStateOf(mediaController.mediaMetadata.title?.toString() ?: "Unknown") }
    var artist by remember { mutableStateOf(mediaController.mediaMetadata.artist?.toString() ?: "Unknown Artist") }
    var duration by remember { mutableStateOf(mediaController.duration.coerceAtLeast(0).toInt()) }
    var position by remember { mutableStateOf(mediaController.currentPosition.toInt()) }

    // 🔗 Listener for controller events with immediate state sync
    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                title = mediaMetadata.title?.toString() ?: "Unknown"
                artist = mediaMetadata.artist?.toString() ?: "Unknown Artist"
            }

            override fun onEvents(player: Player, events: Player.Events) {
                // Always sync all states on any event
                isPlaying = player.isPlaying
                title = player.mediaMetadata.title?.toString() ?: "Unknown"
                artist = player.mediaMetadata.artist?.toString() ?: "Unknown Artist"
                duration = player.duration.coerceAtLeast(0).toInt()
                position = player.currentPosition.toInt()
            }
        }
        mediaController.addListener(listener)

        // Immediate sync when listener is attached
        isPlaying = mediaController.isPlaying
        title = mediaController.mediaMetadata.title?.toString() ?: "Unknown"
        artist = mediaController.mediaMetadata.artist?.toString() ?: "Unknown Artist"
        duration = mediaController.duration.coerceAtLeast(0).toInt()
        position = mediaController.currentPosition.toInt()

        onDispose { mediaController.removeListener(listener) }
    }

    // ⏳ Progress updater loop with state sync
    LaunchedEffect(mediaController) {
        while (true) {
            // Always sync the playing state, not just position
            isPlaying = mediaController.isPlaying
            if (mediaController.isPlaying) {
                position = mediaController.currentPosition.toInt()
                duration = mediaController.duration.coerceAtLeast(0).toInt()
            }
            delay(500L)
        }
    }

    // 🔄 Additional effect to handle screen resume/focus changes
    LaunchedEffect(Unit) {
        // Sync state immediately when screen comes back
        isPlaying = mediaController.isPlaying
        position = mediaController.currentPosition.toInt()
        duration = mediaController.duration.coerceAtLeast(0).toInt()
    }

    // 💿 Rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "diskRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diskRotationAnim"
    )

    BackHandler { onMinimize() }
    //Scaffold(

    //){paddingValues ->
    Column(
        modifier = modifier
            //.padding(paddingValues)
            //.padding(WindowInsets.navigationBars.asPaddingValues())
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(16.dp)

    ) {
        // 🔹 Header
        SnackbarHost(hostState = snackbarHostState)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            var showPlaylistDialog by remember { mutableStateOf(false) }

            IconButton(onClick = { showPlaylistDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add to Playlist")
            }

            if (showPlaylistDialog) {
                PlaylistPickerDialog(
                    context = context,
                    onDismiss = { showPlaylistDialog = false },
                    onPlaylistSelected = { playlistId,playlistName ->
                        // add current song to this playlist
                        val currentItem = mediaController.currentMediaItem
                        val currentUri = currentItem?.localConfiguration?.uri
                        if (currentUri != null) {
                            // same query logic as before
                            val projection = arrayOf(MediaStore.Audio.Media._ID)
                            val selection = "${MediaStore.Audio.Media.DATA} = ?"
                            val selectionArgs = arrayOf(currentUri.path)
                            context.contentResolver.query(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                projection,
                                selection,
                                selectionArgs,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val audioId =
                                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                                    PlaylistManager.addSongToPlaylist(context, playlistId, audioId)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Added to $playlistName ✅")
                                    }
                                }
                            }
                        }
                    },
                    onCreateNew = { name ->
                        val uri = PlaylistManager.createPlaylist(context, name)
                        val playlistId = uri?.lastPathSegment?.toLongOrNull()
                        if (playlistId != null) {
                            val currentItem = mediaController.currentMediaItem
                            val currentUri = currentItem?.localConfiguration?.uri
                            if (currentUri != null) {
                                val projection = arrayOf(MediaStore.Audio.Media._ID)
                                val selection = "${MediaStore.Audio.Media.DATA} = ?"
                                val selectionArgs = arrayOf(currentUri.path)
                                context.contentResolver.query(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    projection,
                                    selection,
                                    selectionArgs,
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val audioId =
                                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                                        PlaylistManager.addSongToPlaylist(
                                            context,
                                            playlistId,
                                            audioId
                                        )
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Created \"$name\" and added song ✅")
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }


            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Row {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
                }
                IconButton(onClick = { /* TODO: like later */ }) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Like")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 🔹 Song info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Progress slider
        Column {
            Slider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { value ->
                    val newPos = (value * duration).roundToInt()
                    mediaController.seekTo(newPos.toLong())
                    position = newPos
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 🔹 Rotating disk
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 🔹 Control buttons with improved click handling
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
            }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (mediaController.shuffleModeEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { mediaController.seekToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }
            FloatingActionButton(onClick = {
                // Force immediate state sync before toggling
                val currentlyPlaying = mediaController.isPlaying
                if (currentlyPlaying) {
                    mediaController.pause()
                } else {
                    mediaController.play()
                }
                // Update local state immediately for responsive UI
                isPlaying = !currentlyPlaying
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = { mediaController.seekToNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
            val repeatMode = mediaController.repeatMode
            IconButton(onClick = {
                mediaController.repeatMode =
                    if (mediaController.repeatMode == Player.REPEAT_MODE_OFF)
                        Player.REPEAT_MODE_ONE
                    else Player.REPEAT_MODE_OFF
            }) {
                if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icon(
                        Icons.Default.RepeatOne,
                        contentDescription = "Repeat One",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Repeat Off",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }

        Spacer(modifier = Modifier.height(24.dp))

        // 🔹 Extra actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = { /* TODO: share */ }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share")
            }
        }
     }
}
@Composable
fun PlaylistPickerDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long, String) -> Unit, // <-- also pass name
    onCreateNew: (String) -> Unit
) {
    var playlists by remember { mutableStateOf(emptyList<Pair<Long, String>>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        playlists = PlaylistManager.getAllPlaylists(context)
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateNew(newName)
                    showCreateDialog = false
                    onDismiss()
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text("➕ Create New Playlist")
                    }
                    playlists.forEach { (id, name) ->
                        TextButton(onClick = {
                            onPlaylistSelected(id, name) // ✅ pass both
                            onDismiss()
                        }) {
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
    }
}


// ⏱ Format time helper
private fun formatTime(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}