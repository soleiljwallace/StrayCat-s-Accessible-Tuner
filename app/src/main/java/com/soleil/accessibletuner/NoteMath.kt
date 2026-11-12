package com.soleil.accessibletuner

import kotlin.math.abs
import kotlin.math.ln

object NoteMath {
    /** Reference pitch for A4. Change this if you tune to 432, 442, etc. */
    const val A4_HZ = 440f

    /** Readings within this many cents are spoken as "in tune". */
    const val IN_TUNE_CENTS = 2

    // Spelled out so TalkBack and text-to-speech read them naturally ("C sharp", not "C number sign").
    private val NAMES = arrayOf(
        "C", "C sharp", "D", "D sharp", "E", "F",
        "F sharp", "G", "G sharp", "A", "A sharp", "B"
    )

    /** Fractional MIDI note number (A4 = 69). */
    fun midiFromFrequency(hz: Float): Float =
        69f + 12f * (ln(hz / A4_HZ) / ln(2f))

    fun noteName(midi: Int): String = NAMES[((midi % 12) + 12) % 12]

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** e.g. "A 4, 12 cents flat", "E 2, in tune", "G 3, 1 cent sharp". */
    fun describe(midi: Int, cents: Int): String {
        val note = "${noteName(midi)} ${octave(midi)}"
        val offset = when {
            abs(cents) <= IN_TUNE_CENTS -> "in tune"
            cents < 0 -> "${-cents} ${if (cents == -1) "cent" else "cents"} flat"
            else -> "$cents ${if (cents == 1) "cent" else "cents"} sharp"
        }
        return "$note, $offset"
    }
}
