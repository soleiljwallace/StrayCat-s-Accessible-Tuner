package com.soleil.accessibletuner

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002).
 * Returns the detected fundamental frequency in Hz, or -1 if there is no clear pitch.
 */
class Yin(
    private val sampleRate: Int,
    bufferSize: Int,
    minFreq: Float = 35f,
    maxFreq: Float = 2000f,
    private val threshold: Float = 0.12f,
    /** Signals quieter than this (RMS, full scale = 1.0) are treated as silence. */
    private val silenceRms: Float = 0.01f,
) {
    private val half = bufferSize / 2
    private val tauMax = min(half - 1, (sampleRate / minFreq).toInt())
    private val tauMin = max(2, (sampleRate / maxFreq).toInt())
    private val d = FloatArray(tauMax + 1)

    fun detect(x: FloatArray): Float {
        var sumSq = 0.0
        for (v in x) sumSq += v * v
        if (sqrt(sumSq / x.size) < silenceRms) return -1f

        // Difference function
        for (tau in 1..tauMax) {
            var s = 0f
            for (j in 0 until half) {
                val diff = x[j] - x[j + tau]
                s += diff * diff
            }
            d[tau] = s
        }

        // Cumulative mean normalized difference
        d[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += d[tau]
            d[tau] = if (running == 0f) 1f else d[tau] * tau / running
        }

        // First dip below threshold, then walk to its local minimum
        var tauEst = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (d[tau] < threshold) {
                while (tau + 1 <= tauMax && d[tau + 1] < d[tau]) tau++
                tauEst = tau
                break
            }
            tau++
        }
        if (tauEst == -1) return -1f

        // Parabolic interpolation for sub-sample accuracy
        val refined = if (tauEst in 1 until tauMax) {
            val s0 = d[tauEst - 1]
            val s1 = d[tauEst]
            val s2 = d[tauEst + 1]
            val denom = s0 + s2 - 2 * s1
            if (denom != 0f) tauEst + (s0 - s2) / (2 * denom) else tauEst.toFloat()
        } else {
            tauEst.toFloat()
        }
        return sampleRate / refined
    }
}
