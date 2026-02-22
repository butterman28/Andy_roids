package com.example.greetingcard.ui.player

import android.media.MediaPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class PlayerState(
    val currentFile: File? = null,
    val playlist: List<File> = emptyList(),
    val isPlaying: Boolean = false,
    val isMinimized: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val isShuffleOn: Boolean = false,
    val isRepeatOn: Boolean = false
)

class PlayerController {
    private var mediaPlayer: MediaPlayer? = null

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    // ✅ Add helper so AppNavigation can safely update state
    fun updateState(transform: (PlayerState) -> PlayerState) {
        _state.update(transform)
    }

    fun playFile(file: File, playlist: List<File>) {
        releasePlayer()

        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener {
                it.start()
                _state.update { st ->
                    st.copy(
                        isPlaying = true,
                        currentPosition = it.currentPosition,
                        duration = it.duration,
                        currentFile = file,
                        playlist = playlist,
                        isMinimized = false
                    )
                }
            }
            setOnCompletionListener {
                handleCompletion()
            }
            setDataSource(file.absolutePath)
            prepareAsync()
        }
    }

    private fun handleCompletion() {
        val st = _state.value
        when {
            st.isRepeatOn -> {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
            st.playlist.isNotEmpty() -> {
                playNext()
            }
            else -> {
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                mp.start()
                _state.update { it.copy(isPlaying = true) }
            }
        }
    }

    fun seekTo(posMs: Int) {
        mediaPlayer?.seekTo(posMs)
        _state.update { it.copy(currentPosition = posMs) }
    }

    fun playNext() {
        val st = _state.value
        val list = st.playlist
        val current = st.currentFile ?: return
        if (list.isEmpty()) return

        val index = list.indexOf(current)
        val nextIndex = if (st.isShuffleOn) list.indices.random() else (index + 1) % list.size
        playFile(list[nextIndex], list)
    }

    fun playPrevious() {
        val st = _state.value
        val list = st.playlist
        val current = st.currentFile ?: return
        if (list.isEmpty()) return

        val index = list.indexOf(current)
        val prevIndex = if (index > 0) index - 1 else list.size - 1
        playFile(list[prevIndex], list)
    }

    fun toggleShuffle() {
        _state.update { it.copy(isShuffleOn = !it.isShuffleOn) }
    }

    fun toggleRepeat() {
        _state.update { it.copy(isRepeatOn = !it.isRepeatOn) }
    }

    fun stopAndClear() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        releasePlayer()
        _state.value = PlayerState()
    }

    private fun releasePlayer() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    suspend fun monitorProgress() {
        while (true) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                _state.update { it.copy(currentPosition = mp.currentPosition, duration = mp.duration) }
            }
            delay(200L)
        }
    }
}
