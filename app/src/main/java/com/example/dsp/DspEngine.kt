package com.example.dsp

import com.example.model.EqBand
import com.example.model.FilterType
import com.example.model.ReferenceTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * High-Pass DC Blocking Filter (10 Hz cutoff).
 * Strips out sub-audible DC offset and low-frequency hum/buzzing before DSP filtering.
 */
class DcBlocker {
    private var x1L = 0.0; private var y1L = 0.0
    private var x1R = 0.0; private var y1R = 0.0
    private val R = 0.995 // ~10 Hz cutoff at standard sample rates (44.1k - 48k)

    fun resetState() {
        x1L = 0.0; y1L = 0.0
        x1R = 0.0; y1R = 0.0
    }

    fun processL(x: Double): Double {
        val y = x - x1L + R * y1L
        x1L = x
        y1L = if (abs(y) < 1e-15) 0.0 else y
        return y1L
    }

    fun processR(x: Double): Double {
        val y = x - x1R + R * y1R
        x1R = x
        y1R = if (abs(y) < 1e-15) 0.0 else y
        return y1R
    }
}

/**
 * High-Precision Biquad Filter using Direct Form II Transposed structure.
 * Features 64-bit floating point precision, anti-denormal flushing, and state zeroing.
 */
class BiquadFilter {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
    var a1 = 0.0; var a2 = 0.0

    // State registers for Left & Right channels (Zero-initialized)
    private var s1L = 0.0; private var s2L = 0.0
    private var s1R = 0.0; private var s2R = 0.0

    fun resetState() {
        s1L = 0.0; s2L = 0.0
        s1R = 0.0; s2R = 0.0
    }

    fun updateCoefficients(band: EqBand, sampleRate: Double = 48000.0) {
        if (!band.enabled) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val f0 = band.frequencyHz.toDouble().coerceIn(10.0, 22000.0)
        val gainDb = band.gainDb.toDouble()
        val q = band.qFactor.toDouble().coerceAtLeast(0.05)

        val w0 = 2.0 * PI * f0 / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val A = 10.0.pow(gainDb / 40.0)

        var rawB0 = 1.0; var rawB1 = 0.0; var rawB2 = 0.0
        var rawA0 = 1.0; var rawA1 = 0.0; var rawA2 = 0.0

        when (band.filterType) {
            FilterType.PEAKING -> {
                rawB0 = 1.0 + alpha * A
                rawB1 = -2.0 * cosW0
                rawB2 = 1.0 - alpha * A
                rawA0 = 1.0 + alpha / A
                rawA1 = -2.0 * cosW0
                rawA2 = 1.0 - alpha / A
            }
            FilterType.LOW_SHELF -> {
                val sqrtA = sqrt(A)
                rawB0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                rawB1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0)
                rawB2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                rawA0 = (A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                rawA1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0)
                rawA2 = (A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(A)
                rawB0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                rawB1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0)
                rawB2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                rawA0 = (A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                rawA1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0)
                rawA2 = (A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.LOW_PASS -> {
                rawB0 = (1.0 - cosW0) / 2.0
                rawB1 = 1.0 - cosW0
                rawB2 = (1.0 - cosW0) / 2.0
                rawA0 = 1.0 + alpha
                rawA1 = -2.0 * cosW0
                rawA2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                rawB0 = (1.0 + cosW0) / 2.0
                rawB1 = -(1.0 + cosW0)
                rawB2 = (1.0 + cosW0) / 2.0
                rawA0 = 1.0 + alpha
                rawA1 = -2.0 * cosW0
                rawA2 = 1.0 - alpha
            }
            FilterType.NOTCH -> {
                rawB0 = 1.0
                rawB1 = -2.0 * cosW0
                rawB2 = 1.0
                rawA0 = 1.0 + alpha
                rawA1 = -2.0 * cosW0
                rawA2 = 1.0 - alpha
            }
        }

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun processL(x: Double): Double {
        // Micro anti-denormal offset to prevent FPU underflow buzzing
        val inVal = x + 1e-18
        val y = b0 * inVal + s1L
        s1L = b1 * inVal - a1 * y + s2L
        s2L = b2 * inVal - a2 * y

        if (abs(s1L) < 1e-15) s1L = 0.0
        if (abs(s2L) < 1e-15) s2L = 0.0
        val outVal = y - 1e-18
        return if (abs(outVal) < 1e-15) 0.0 else outVal
    }

    fun processR(x: Double): Double {
        val inVal = x + 1e-18
        val y = b0 * inVal + s1R
        s1R = b1 * inVal - a1 * y + s2R
        s2R = b2 * inVal - a2 * y

        if (abs(s1R) < 1e-15) s1R = 0.0
        if (abs(s2R) < 1e-15) s2R = 0.0
        val outVal = y - 1e-18
        return if (abs(outVal) < 1e-15) 0.0 else outVal
    }
}

/**
 * DSP Mathematics & Frequency Response Computation Engine.
 * Evaluates exact biquad digital filter transfer functions across 20 Hz - 20000 Hz.
 */
object DspEngine {

    const val DEFAULT_SAMPLE_RATE = 48000.0f
    const val NUM_FREQ_POINTS = 140

    // Logarithmic frequency array from 20 Hz to 20,000 Hz
    val frequenciesHz: FloatArray by lazy {
        val array = FloatArray(NUM_FREQ_POINTS)
        val minLog = log10(20.0)
        val maxLog = log10(20000.0)
        val step = (maxLog - minLog) / (NUM_FREQ_POINTS - 1)
        for (i in 0 until NUM_FREQ_POINTS) {
            array[i] = 10.0.pow(minLog + i * step).toFloat()
        }
        array
    }

    /**
     * Compute magnitude response in dB for a single band at frequency fHz.
     */
    fun computeBandGainDb(band: EqBand, fHz: Float, sampleRate: Float = DEFAULT_SAMPLE_RATE, qScale: Float = 1.0f): Float {
        if (!band.enabled) return 0.0f

        val fs = sampleRate.toDouble()
        val f0 = band.frequencyHz.toDouble().coerceIn(10.0, 22000.0)
        val gainDb = band.gainDb.toDouble()
        val q = (band.qFactor * qScale).toDouble().coerceAtLeast(0.05)

        val w0 = 2.0 * PI * f0 / fs
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val A = 10.0.pow(gainDb / 40.0)

        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a0 = 1.0; var a1 = 0.0; var a2 = 0.0

        when (band.filterType) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * A
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / A
            }
            FilterType.LOW_SHELF -> {
                val sqrtA = sqrt(A)
                b0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0)
                b2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0)
                a2 = (A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(A)
                b0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0)
                b2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0)
                a2 = (A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.LOW_PASS -> {
                b0 = (1.0 - cosW0) / 2.0
                b1 = 1.0 - cosW0
                b2 = (1.0 - cosW0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1.0 + cosW0) / 2.0
                b1 = -(1.0 + cosW0)
                b2 = (1.0 + cosW0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cosW0
                b2 = 1.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
        }

        val nb0 = b0 / a0; val nb1 = b1 / a0; val nb2 = b2 / a0
        val na1 = a1 / a0; val na2 = a2 / a0

        val w = 2.0 * PI * fHz.toDouble() / fs
        val cosW = cos(w); val sinW = sin(w)
        val cos2W = cos(2.0 * w); val sin2W = sin(2.0 * w)

        val numReal = nb0 + nb1 * cosW + nb2 * cos2W
        val numImag = -(nb1 * sinW + nb2 * sin2W)

        val denReal = 1.0 + na1 * cosW + na2 * cos2W
        val denImag = -(na1 * sinW + na2 * sin2W)

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq < 1e-12) return 0.0f

        val magSq = numMagSq / denMagSq
        val gainDbResult = 10.0 * log10(max(1e-12, magSq))

        return gainDbResult.toFloat().coerceIn(-48.0f, 30.0f)
    }

    /**
     * Compute total EQ response curve array across all frequencies for given bands + preamp.
     */
    fun computeTotalResponseCurve(bands: List<EqBand>, preampDb: Float, sampleRate: Float = DEFAULT_SAMPLE_RATE, qScale: Float = 1.0f): FloatArray {
        val total = FloatArray(NUM_FREQ_POINTS)
        val freqs = frequenciesHz

        for (i in 0 until NUM_FREQ_POINTS) {
            var sum = preampDb
            val f = freqs[i]
            for (band in bands) {
                if (band.enabled) {
                    sum += computeBandGainDb(band, f, sampleRate, qScale)
                }
            }
            total[i] = sum.coerceIn(-30.0f, 30.0f)
        }
        return total
    }

    /**
     * Interpolate target curve points onto standard frequencies array.
     */
    fun computeTargetCurveArray(target: ReferenceTarget?): FloatArray? {
        if (target == null || target.points.isEmpty()) return null
        val total = FloatArray(NUM_FREQ_POINTS)
        val freqs = frequenciesHz
        val sortedPoints = target.points.sortedBy { it.first }

        for (i in 0 until NUM_FREQ_POINTS) {
            val f = freqs[i]
            total[i] = interpolateGainForFreq(f, sortedPoints)
        }
        return total
    }

    private fun interpolateGainForFreq(freq: Float, sortedPoints: List<Pair<Float, Float>>): Float {
        if (freq <= sortedPoints.first().first) return sortedPoints.first().second
        if (freq >= sortedPoints.last().first) return sortedPoints.last().second

        for (j in 0 until sortedPoints.size - 1) {
            val p1 = sortedPoints[j]
            val p2 = sortedPoints[j + 1]
            if (freq >= p1.first && freq <= p2.first) {
                val logF = log10(freq.toDouble())
                val logP1 = log10(p1.first.toDouble())
                val logP2 = log10(p2.first.toDouble())
                val t = (logF - logP1) / (logP2 - logP1)
                return (p1.second + t * (p2.second - p1.second)).toFloat()
            }
        }
        return 0.0f
    }

    /**
     * Find max peak gain in dB across active EQ curve to detect digital clipping.
     */
    fun computePeakOutputGainDb(curve: FloatArray): Float {
        var peak = -99.0f
        for (v in curve) {
            if (v > peak) peak = v
        }
        return peak
    }
}
