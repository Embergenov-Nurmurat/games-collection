package com.example.minigames.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Plays the synthesised background loop ([MusicSynth]) with a looping [MediaPlayer].
 *
 * Music plays only while the setting is on AND the activity is resumed. The WAV is generated once on a background
 * thread and cached in the cache directory (regenerated if the system cleared it). Every platform call is guarded:
 * if anything fails the app simply stays silent.
 */
class MusicPlayer(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var enabled = false
    private var resumed = false
    private var preparing = false
    private var released = false
    @Volatile private var generation = 0

    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (!on) dropPlayer()
        sync()
    }

    fun onResume() {
        resumed = true
        sync()
    }

    fun onPause() {
        resumed = false
        sync()
    }

    fun release() {
        released = true
        generation++
        dropPlayer()
    }

    private fun wanted() = enabled && resumed && !released

    private fun sync() {
        if (released) return
        try {
            if (!wanted()) {
                val p = player
                if (p != null && p.isPlaying) p.pause()
                return
            }
            val p = player
            if (p != null) {
                if (!p.isPlaying) p.start()
                return
            }
            val f = cacheFile()
            if (f.exists() && f.length() == expectedBytes()) startPlayer(f) else generate(f)
        } catch (e: Exception) {
            dropPlayer()
        }
    }

    private fun expectedBytes(): Long = 44L + 2L * MusicSynth.loopSamples

    private fun cacheFile() = File(context.cacheDir, FILE_NAME)

    /** Synthesises the loop off the main thread, then starts playing if it is still wanted. */
    private fun generate(target: File) {
        if (preparing) return
        preparing = true
        val gen = generation
        Thread {
            var ok = false
            try {
                val tmp = File(target.parentFile, FILE_NAME + ".tmp")
                tmp.writeBytes(MusicSynth.toWav(MusicSynth.render()))
                ok = tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))
            } catch (e: Throwable) {
                ok = false
            }
            main.post {
                preparing = false
                if (ok && gen == generation && wanted() && player == null) {
                    try {
                        startPlayer(target)
                    } catch (e: Exception) {
                        dropPlayer()
                    }
                }
            }
        }.start()
    }

    private fun startPlayer(f: File) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.isLooping = true
            mp.setVolume(VOLUME, VOLUME)
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) player = null
                try { mp.release() } catch (e: Exception) { /* ignore */ }
                true
            }
            mp.setOnPreparedListener { p ->
                try {
                    if (player === p && wanted()) p.start()
                } catch (e: Exception) {
                    dropPlayer()
                }
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            dropPlayer()
        }
    }

    private fun dropPlayer() {
        val p = player ?: return
        player = null
        try { p.release() } catch (e: Exception) { /* ignore */ }
    }

    companion object {
        private const val FILE_NAME = "music1.wav"
        private const val VOLUME = 0.35f
    }
}
