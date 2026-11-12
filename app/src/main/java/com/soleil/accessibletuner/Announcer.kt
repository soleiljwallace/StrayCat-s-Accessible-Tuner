package com.soleil.accessibletuner

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

/**
 * Speaks tuner readings.
 *
 * - If a screen reader (TalkBack) is running, the reading goes through TalkBack by updating
 *   [liveRegion], a TextView marked as an accessibility live region. TalkBack speaks it in your
 *   own voice and speech rate settings.
 * - Otherwise it falls back to Android text-to-speech.
 *
 * [speakingUntil] tells the tuner when speech is probably still coming out of the speaker, so it
 * can ignore the microphone and not try to tune to its own voice.
 */
class Announcer(context: Context, private val liveRegion: TextView) {
    private val a11y = context.getSystemService(AccessibilityManager::class.java)
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    /** SystemClock.elapsedRealtime() value until which speech is assumed to be playing. */
    @Volatile var speakingUntil = 0L
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        speakingUntil = SystemClock.elapsedRealtime() + AFTER_SPEECH_MS
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        speakingUntil = 0L
                    }
                })
                ttsReady = true
            }
        }
    }

    /** True when TalkBack (or another touch-exploration screen reader) is on. */
    fun screenReaderOn(): Boolean = a11y.isEnabled && a11y.isTouchExplorationEnabled

    /**
     * false (default): clear the readout, then redraw it a moment later, so TalkBack sees a fresh
     *   live-region change every time.
     * true: move TalkBack focus onto the reading every time it is redrawn (focus is cleared first,
     *   so TalkBack reads it again even if it was already there). The live region is turned off
     *   so the reading isn't spoken twice.
     */
    var focusMode = false
        set(value) {
            field = value
            liveRegion.accessibilityLiveRegion =
                if (value) View.ACCESSIBILITY_LIVE_REGION_NONE else View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }

    private var pendingRedraw: Runnable? = null

    fun announce(text: String) {
        val now = SystemClock.elapsedRealtime()
        if (screenReaderOn()) {
            tts?.stop()
            pendingRedraw?.let { liveRegion.removeCallbacks(it) }
            if (focusMode) {
                liveRegion.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null
                )
            }
            // Android merges accessibility events on the same view that arrive within ~100 ms,
            // so wait longer than that before drawing the new reading.
            liveRegion.text = ""
            val redraw = Runnable {
                liveRegion.text = text
                if (focusMode) {
                    liveRegion.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null
                    )
                }
            }
            pendingRedraw = redraw
            liveRegion.postDelayed(redraw, REDRAW_DELAY_MS)
            speakingUntil = now + REDRAW_DELAY_MS + estimateSpeechMs(text)
        } else {
            liveRegion.text = text
            if (ttsReady) {
                // Upper-bound estimate; onDone() shortens it as soon as speech actually ends.
                speakingUntil = now + estimateSpeechMs(text) + 1000
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tuner")
            }
        }
    }

    /** Rough guess at how long TalkBack takes to say [text]. Lower MS_PER_CHAR if you use fast speech. */
    private fun estimateSpeechMs(text: String): Long = 250L + text.length * MS_PER_CHAR

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val MS_PER_CHAR = 45L
        const val AFTER_SPEECH_MS = 200L
        const val REDRAW_DELAY_MS = 150L
    }
}
