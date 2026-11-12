package com.soleil.accessibletuner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var readout: TextView
    private lateinit var toggle: Button
    private lateinit var headphones: Switch
    private lateinit var direct: Switch
    private lateinit var announcer: Announcer

    private val handler = Handler(Looper.getMainLooper())
    private var detector: PitchDetector? = null

    private val recent = ArrayDeque<Float>()   // recent fractional MIDI readings
    private var silentFrames = 0
    private var lastText: String? = null
    private var lastNote = Int.MIN_VALUE
    private var lastCents = 0
    private var lastAnnounceAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        readout = findViewById(R.id.readout)
        toggle = findViewById(R.id.toggle)
        headphones = findViewById(R.id.headphones)
        direct = findViewById(R.id.direct)
        announcer = Announcer(this, readout)

        // Remember both switches between launches
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        headphones.isChecked = prefs.getBoolean("headphones", false)
        direct.isChecked = prefs.getBoolean("direct", false)
        announcer.directAnnouncements = direct.isChecked
        headphones.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("headphones", on).apply() }
        direct.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("direct", on).apply()
            announcer.directAnnouncements = on
        }

        toggle.setOnClickListener {
            if (detector == null) startTuning() else stopTuning(announce = true)
        }
    }

    private fun startTuning() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        val d = PitchDetector(this) { hz -> handler.post { onPitch(hz) } }
        if (!d.start()) {
            announcer.announce(getString(R.string.mic_error))
            return
        }
        detector = d
        resetReadingState()
        toggle.text = getString(R.string.stop)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        announcer.announce(getString(R.string.listening))
    }

    private fun stopTuning(announce: Boolean) {
        detector?.stop()
        detector = null
        handler.removeCallbacksAndMessages(null)
        toggle.text = getString(R.string.start)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (announce) announcer.announce(getString(R.string.stopped))
    }

    private fun resetReadingState() {
        recent.clear()
        silentFrames = 0
        lastText = null
        lastNote = Int.MIN_VALUE
    }

    private fun onPitch(hz: Float) {
        if (detector == null) return
        val now = SystemClock.elapsedRealtime()

        // Don't listen to our own voice coming out of the speaker.
        if (!headphones.isChecked && now < announcer.speakingUntil) {
            recent.clear()
            return
        }

        if (hz <= 0f) {
            silentFrames++
            if (silentFrames >= 3) recent.clear()
            // After ~half a second of silence, the next note gets announced even if it's the same.
            if (silentFrames >= SILENCE_RESET_FRAMES) { lastText = null; lastNote = Int.MIN_VALUE }
            return
        }
        silentFrames = 0

        recent.addLast(NoteMath.midiFromFrequency(hz))
        while (recent.size > 5) recent.removeFirst()
        if (recent.size < STABLE_FRAMES) return

        // Require the last few frames to agree on the note (filters out octave jumps and pluck noise).
        val last = recent.toList().takeLast(STABLE_FRAMES)
        val note = last.last().roundToInt()
        if (last.any { it.roundToInt() != note }) return
        val cents = last.map { (it - note) * 100f }.sorted()[STABLE_FRAMES / 2].roundToInt()

        if (now - lastAnnounceAt < MIN_INTERVAL_MS) return
        val changed = note != lastNote || abs(cents - lastCents) >= CENTS_CHANGE
        val text = NoteMath.describe(note, cents)
        if (!changed || text == lastText) return

        lastNote = note
        lastCents = cents
        lastText = text
        lastAnnounceAt = now
        announcer.announce(text)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startTuning()
        else announcer.announce(getString(R.string.mic_needed))
    }

    override fun onStop() {
        super.onStop()
        if (detector != null) stopTuning(announce = false)
    }

    override fun onDestroy() {
        announcer.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_MIC = 1
        private const val STABLE_FRAMES = 3       // ~140 ms of agreement before speaking
        private const val MIN_INTERVAL_MS = 800L  // minimum gap between announcements
        private const val CENTS_CHANGE = 2        // re-announce when cents move this much
        private const val SILENCE_RESET_FRAMES = 10
    }
}
