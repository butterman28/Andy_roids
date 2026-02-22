package com.example.greetingcard.ui
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloaderUI(
    modifier: Modifier = Modifier,
    onDownload: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "YouTube Audio Downloader",
            fontSize = 22.sp,
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Enter YouTube URL") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://youtube.com/...") }
        )

        Button(
            onClick = {
                if (url.isNotBlank()) {
                    onDownload(url)
                    url = "" // Clear URL after download
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank()
        ) {
            Text("Download Audio")
        }

        // Status info
        Text(
            text = "Downloads will be saved to your Downloads folder",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DownloaderUIPreview() {
    //GreetingCardTheme {
        
        DownloaderUI(
            onDownload = {},
            //onToggleFloatingButton = {},
            //floatingButtonEnabled = true
        )
    //}
}