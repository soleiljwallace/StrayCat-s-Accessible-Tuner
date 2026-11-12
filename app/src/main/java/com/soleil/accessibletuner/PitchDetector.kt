package com.soleil.accessibletuner

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.max

/**
 * Reads the microphone on a background thread and reports a frequency (Hz, or -1 for no pitch)
 * roughly 20 times per second. The callback runs on the audio thread.
 */
class PitchDetector(private val context: Context, private val onPitch: (Float) -> Unit) {
    private val sampleRate = 44100
    private val bufferSize = 4096   // analysis window (~93 ms)
    private val hop = 2048          // new samples per analysis (~46 ms)

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Caller must already hold RECORD_AUDIO. Returns false if the mic could not be opened. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        // Don't use VOICE_RECOGNITION: TalkBack goes quiet (no live-region or announcement speech)
        // while a voice-recognition recording is active. UNPROCESSED is raw audio, ideal for tuning,
        // but not every phone supports it, so fall back to the ordinary MIC source.
        val audioManager = context.getSystemService(AudioManager::class.java)
        val sources = buildList {
            if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.MIC)
        }
        var record: AudioRecord? = null
        for (source in sources) {
            val candidate = try {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuf, bufferSize * 2 * 2)
                )
            } catch (e: Exception) {
                null
            }
            if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                record = candidate
                break
            }
            candidate?.release()
        }
        if (record == null) return false

        // Always use the phone's own microphone, even when a wired or Bluetooth headset is connected.
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let { record.setPreferredDevice(it) }

        running = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val window = FloatArray(bufferSize)
            val readBuf = ShortArray(hop)
            val yin = Yin(sampleRate, bufferSize)
            try {
                record.startRecording()
                while (running) {
                    val n = record.read(readBuf, 0, hop)
                    if (n <= 0) continue
                    // Slide the window left and append the new samples
                    System.arraycopy(window, n, window, 0, bufferSize - n)
                    for (i in 0 until n) window[bufferSize - n + i] = readBuf[i] / 32768f
                    onPitch(yin.detect(window))
                }
            } finally {
                try { record.stop() } catch (_: Exception) {}
                record.release()
            }
        }, "PitchDetector")
        thread?.start()
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
