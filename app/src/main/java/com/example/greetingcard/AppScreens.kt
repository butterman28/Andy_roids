package com.example.greetingcard.ui.screens

import android.Manifest
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.greetingcard.ui.DownloaderUI
import com.example.greetingcard.ui.FloatingButtonToggle
import java.io.File
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat


@Composable
fun HomeScreen(
    floatingButtonEnabled: Boolean,
    onFloatingButtonToggle: (Boolean) -> Unit,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    //var screepadding = 10.dp
    Column(
        modifier = modifier.padding(0.5.dp)
    ) {
        // App title
        Text(
            text = "YouTube Audio Downloader",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 0.dp)
        )

        // Floating button toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Floating Button",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Enable floating button for quick downloads from other apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FloatingButtonToggle(
                    isEnabled = floatingButtonEnabled,
                    onToggle = onFloatingButtonToggle
                )
            }
        }

        // Downloader UI
        DownloaderUI(
            onDownload = onDownload
        )
    }
}

@Composable
fun DownloadsScreen(
    onPlayFile: (File, List<File>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var downloadedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) } // 👈 loading state

    Column(
        modifier = modifier.padding(0.5.dp)
    ) {
        // Request permission at composition time
        RequestAudioPermission {
            isLoading = true
            downloadedFiles = getDownloadedAudioFiles(context)
            isLoading = false
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Downloads",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            isLoading -> {
                // 🔹 Loading spinner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            downloadedFiles.isEmpty() -> {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = "No files",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No downloaded files yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Downloaded audio files will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            else -> {
                Text(
                    text = "${downloadedFiles.size} files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(downloadedFiles) { file ->
                        DownloadedFileItem(
                            file = file,
                            onDelete = {
                                isLoading = true
                                downloadedFiles = getDownloadedAudioFiles(context)
                                isLoading = false
                            },
                            onClick = { onPlayFile(file, downloadedFiles) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedFileItem(
    file: File,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() } // 👈 item is clickable
            .padding(2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = "Audio file",
                modifier = Modifier.padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatFileSize(file.length())} • ${file.extension.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}



@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(0.5.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Settings sections
        SettingsSection(
            title = "Download Settings",
            items = listOf(
                SettingsItem("Audio Quality", "192kbps", {}),
                SettingsItem("Output Format", "MP3", {}),
                SettingsItem("Download Location", "Downloads folder", {})
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(
            title = "App Settings",
            items = listOf(
                SettingsItem("Auto-download shared links", "Enabled", {}),
                SettingsItem("Show notifications", "Enabled", {}),
                SettingsItem("Delete original file", "After conversion", {})
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(
            title = "About",
            items = listOf(
                SettingsItem("App Version", "1.0.0", {}),
                SettingsItem("Developer", "Your Name", {}),
                SettingsItem("Licenses", "View open source licenses", {})
            )
        )
    }
}


@Composable
private fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SettingsItemRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = item.onClick
                    )
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsItemRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoIndication { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Data classes and helper functions
data class SettingsItem(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)


// Replace old function with this
private fun getDownloadedAudioFiles(context: Context): List<File> {
    val audioFiles = mutableListOf<File>()

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATA, // deprecated on API 29+, but still works for file path
        MediaStore.Audio.Media.DATE_ADDED
    )

    // Only query Downloads directory
    val selection: String? = null
    val selectionArgs: Array<String>? = null

    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    // Query external
    val externalCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    context.contentResolver.query(
        externalCollection,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val filePath = cursor.getString(dataColumn)
            val file = File(filePath)
            if (file.exists()) {
                audioFiles.add(file)
            }
        }
    }

    // Query internal
    val internalCollection = MediaStore.Audio.Media.INTERNAL_CONTENT_URI

    context.contentResolver.query(
        internalCollection,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val filePath = cursor.getString(dataColumn)
            val file = File(filePath)
            if (file.exists()) {
                audioFiles.add(file)
            }
        }
    }

    return audioFiles
}



private fun isAudioFile(file: File): Boolean {
    val audioExtensions = setOf(
        // Common audio formats
        "mp3", "m4a", "wav", "flac", "aac", "ogg", "wma", "aiff", "ape",
        "opus", "m4p", "3gp", "amr", "awb", "dss", "dvf", "gsm", "iklax",
        "ivs", "m4r", "mmf", "mpc", "msv", "nmf", "oga", "ra", "raw", "rf64",
        "sln", "tta", "voc", "vox", "webm", "wv", "8svx", "cda", "au", "ac3",
        "dts", "mp2", "mp1", "mka", "tak", "spx", "xa"
    )

    return audioExtensions.contains(file.extension.lowercase())
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

// Extension function for clickable without indication
@Composable
fun Modifier.clickableNoIndication(onClick: () -> Unit) = this.then(
    Modifier.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
)
@Composable
fun RequestAudioPermission(onGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) onGranted()
        }
    )

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(permission)
        } else {
            onGranted()
        }
    }
}
