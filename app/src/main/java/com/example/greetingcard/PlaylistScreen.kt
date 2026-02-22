// com.example.greetingcard.ui.screens/PlaylistScreen.kt

package com.example.greetingcard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.example.greetingcard.PlaylistManager
import com.example.greetingcard.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    mediaController: MediaController,
    onBack: () -> Unit,
    onSongSelected: () -> Unit, // 👈 navigate to PlayScreen
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(emptyList<Pair<Long, String>>()) }
    var selectedPlaylist by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }

    LaunchedEffect(Unit) {
        playlists = PlaylistManager.getAllPlaylists(context)
    }

    Column(
        modifier = modifier.padding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedPlaylist == null) {
                    onBack()
                } else {
                    selectedPlaylist = null
                    songs = emptyList()
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(if (selectedPlaylist == null) "Playlists" else selectedPlaylist!!.second,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
        }

        if (selectedPlaylist == null) {
            // Show all playlists
            LazyColumn(
            ) {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.second) },
                        modifier = Modifier
                            .clickable {
                                selectedPlaylist = playlist
                                songs = PlaylistManager.getSongsFromPlaylist(context, playlist.first)
                            }
                    )
                    Divider()
                }
            }
        } else {
            // Show songs inside playlist
            LazyColumn(
                modifier = Modifier
                    .padding(2.dp)
            ) {
                items(songs) { song ->
                    ListItem(
                        headlineContent = { Text(song.title) },
                        supportingContent = { Text(song.artist) },
                        modifier = Modifier
                            .clickable {
                                val mediaItem = MediaItem.Builder()
                                    .setUri(song.uri)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(song.title)
                                            .setArtist(song.artist)
                                            .build()
                                    )
                                    .build()

                                mediaController.setMediaItem(mediaItem)
                                mediaController.prepare()
                                mediaController.play()

                                onSongSelected() // navigate to PlayScreen
                            }
                    )
                    Divider()
                }
            }
        }
    }
}
