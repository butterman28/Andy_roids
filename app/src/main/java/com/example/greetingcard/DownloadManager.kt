package com.example.greetingcard

import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.arthenica.ffmpegkit.FFmpegKit
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object DownloadManager {
    fun downloadAudio(context: android.content.Context, url: String) {
        val py = Python.getInstance()
        val pyObj = py.getModule("downloader")

        //val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        //if (!downloadsDir.exists()) {
        //    downloadsDir.mkdirs()
        //}
        val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val outputPath = downloadsDir.absolutePath
        val filename = extractFilenameFromUrl(url)

        DownloadNotificationManager.showDownloadStarted(context, filename)

        val progressJob = CoroutineScope(Dispatchers.IO).launch {
            DownloadNotificationManager.simulateProgressUpdates(context) {}
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = pyObj.callAttr("download_audio", url, outputPath).toString()

                progressJob.cancel()

                if (result.startsWith("ERROR::")) {
                    handleDownloadError(context, result)
                } else {
                    processDownloadedFile(context, result, downloadsDir)
                }
            } catch (e: Exception) {
                progressJob.cancel()
                handleDownloadException(context, e)
            }
        }
    }

    private suspend fun handleDownloadError(context: android.content.Context, result: String) {
        Log.e("Downloader", result)
        val errorMessage = result.removePrefix("ERROR::")

        DownloadNotificationManager.showDownloadFailed(context, errorMessage)

        withContext(Dispatchers.Main) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    // 🔹 UPDATED: handle structured JSON result and pass metadata to converter
    private suspend fun processDownloadedFile(context: android.content.Context, result: String, downloadsDir: File) {
        try {
            val json = JSONObject(result)
            val audioPath = json.getString("audio")
            val metadata = json.getJSONObject("info_json")
            val thumbnail = json.optString("thumbnail", "")

            val inputFile = File(audioPath)
            if (!inputFile.exists()) {
                val errorMessage = "Downloaded file not found: ${inputFile.name}"
                DownloadNotificationManager.showDownloadFailed(context, errorMessage)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
                return
            }

            val title = metadata.optString("fulltitle", inputFile.nameWithoutExtension)
            val artistArray = metadata.optJSONArray("artists")
            val artist = artistArray?.join(", ") ?: metadata.optString("artist", "Unknown Artist")
            val album = metadata.optString("album", "")
            val year = metadata.optString("release_year", "")
            val description = metadata.optString("description", "")

            convertToMp3(
                context,
                inputFile,
                downloadsDir,
                title,
                artist,
                album,
                year,
                description,
                thumbnail
            )

        } catch (e: Exception) {
            Log.e("Downloader", "JSON parse error: ${e.message}")
            val errorMessage = "Invalid metadata returned from Python"
            DownloadNotificationManager.showDownloadFailed(context, errorMessage)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 🔹 UPDATED: embed metadata + album art into final MP3
    private suspend fun convertToMp3(
        context: android.content.Context,
        inputFile: File,
        downloadsDir: File,
        title: String,
        artist: String,
        album: String,
        year: String,
        description: String,
        thumbnailPath: String
    ) {
        withContext(Dispatchers.Main) {
            DownloadNotificationManager.showConversionStarted(context)
        }

        val outputFile = File(downloadsDir, inputFile.nameWithoutExtension + ".mp3")

        fun esc(s: String) = s.replace("\"", "\\\"")

        val cmdBuilder = StringBuilder()
        cmdBuilder.append("-i \"${inputFile.absolutePath}\" ")

        // Optional thumbnail (embed as album art if exists)
        val hasThumbnail = thumbnailPath.isNotEmpty() && File(thumbnailPath).exists()
        if (hasThumbnail) {
            cmdBuilder.append("-i \"${thumbnailPath}\" ") // Add second input
            cmdBuilder.append("-map 0:a -map 1 ") // map audio and image
            cmdBuilder.append("-c:a libmp3lame -c:v mjpeg ") // encode image as MJPEG
        } else {
            cmdBuilder.append("-vn ") // no video stream
            cmdBuilder.append("-c:a libmp3lame ")
        }
        cmdBuilder.append("-ar 44100 -ac 2 -b:a 192k ")
        cmdBuilder.append("-metadata title=\"${esc(title)}\" ")
        cmdBuilder.append("-metadata artist=\"${esc(artist)}\" ")
        cmdBuilder.append("-metadata album=\"${esc(album)}\" ")
        if (year.isNotEmpty()) cmdBuilder.append("-metadata date=\"${esc(year)}\" ")
        cmdBuilder.append("-metadata comment=\"${esc(description)}\" ")
        cmdBuilder.append("\"${outputFile.absolutePath}\"")

        val cmd = cmdBuilder.toString()
        Log.d("FFmpeg", "Running command: $cmd")

        val session = FFmpegKit.execute(cmd)

        if (session.returnCode.isValueSuccess) {
            Log.d("Downloader", "Converted to ${outputFile.absolutePath}")
            if (inputFile.exists()) inputFile.delete()

            withContext(Dispatchers.Main) {
                DownloadNotificationManager.showDownloadCompleted(
                    context,
                    outputFile.name,
                    outputFile.absolutePath
                )
                Toast.makeText(context, "Saved: ${outputFile.name}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e("FFmpeg", "Conversion failed")
            val errorMessage = "FFmpeg conversion failed"
            DownloadNotificationManager.showDownloadFailed(context, errorMessage)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun handleDownloadException(context: android.content.Context, e: Exception) {
        Log.e("Downloader", "Error: ${e.message}", e)
        val errorMessage = "Error: ${e.message}"

        DownloadNotificationManager.showDownloadFailed(context, errorMessage)

        withContext(Dispatchers.Main) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val path = uri.path
            if (path.isNotEmpty() && path.contains("/")) {
                path.substringAfterLast("/").ifEmpty { "audio_file" }
            } else {
                "audio_file"
            }
        } catch (e: Exception) {
            "audio_file"
        }
    }
}
