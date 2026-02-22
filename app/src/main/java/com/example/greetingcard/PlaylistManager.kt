package com.example.greetingcard

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)
object PlaylistManager {

    fun createPlaylist(context: Context, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Playlists.NAME, name)
            put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        return context.contentResolver.insert(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            values
        )
    }

    fun addSongToPlaylist(context: Context, playlistId: Long, audioId: Long) {
        val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, 1)
            put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId)
        }
        context.contentResolver.insert(uri, values)
    }


    fun getAllPlaylists(context: Context): List<Pair<Long, String>> {
        val playlists = mutableListOf<Pair<Long, String>>()
        val projection = arrayOf(
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME
        )

        context.contentResolver.query(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Audio.Playlists.NAME + " ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while (cursor.moveToNext()) {
                playlists.add(cursor.getLong(idCol) to cursor.getString(nameCol))
            }
        }
        return playlists
    }

    fun getSongsFromPlaylist(context: Context, playlistId: Long): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.TITLE,
            MediaStore.Audio.Playlists.Members.ARTIST,
            MediaStore.Audio.Playlists.Members.DATA
        )

        val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)

        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ARTIST)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val data = cursor.getString(dataCol)
                val songUri = Uri.parse(data)

                songs.add(Song(id, title, artist, songUri))
            }
        }

        return songs
    }
}