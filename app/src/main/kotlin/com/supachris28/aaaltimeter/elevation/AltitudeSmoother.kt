package com.supachris28.aaaltimeter.elevation

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Smooths noisy GPS altitude.
 *
 * GPS altitude typically wobbles by 5 to 20 m between fixes. This applies two steps:
 *  1. **Spike rejection**: a reading that implies an impossible vertical speed is dropped. If
 *     several in a row disagree with us, we assume the GPS was right (e.g. after losing the fix
 *     in a tunnel) and jump to the new value.
 *  2. **Exponential moving average** weighted by the time between readings, so irregular fix
 *     intervals behave sensibly. [timeConstantMillis] is roughly how far the output lags.
 */
class AltitudeSmoother(
    private val timeConstantMillis: Long = 15_000,
    private val maxVerticalSpeedMetersPerSecond: Double = 20.0,
    private val maxConsecutiveRejections: Int = 5,
) {
    private var value: Double? = null
    private var lastTimeMillis = 0L
    private var rejectedInARow = 0

    /** Returns the smoothed altitude, or null if this reading was rejected. */
    fun add(timeMillis: Long, altitudeMeters: Double): Double? {
        val previous = value
        if (previous == null) return restartAt(timeMillis, altitudeMeters)
        if (timeMillis < lastTimeMillis) return null // out of order

        val dtMillis = timeMillis - lastTimeMillis
        // Treat very close readings as one second apart so a tiny dt can't blow up the speed.
        val speed = abs(altitudeMeters - previous) / max(dtMillis / 1000.0, 1.0)
        if (speed > maxVerticalSpeedMetersPerSecond) {
            if (rejectedInARow < maxConsecutiveRejections) {
                rejectedInARow++
                return null
            }
            return restartAt(timeMillis, altitudeMeters)
        }

        rejectedInARow = 0
        val alpha = 1 - exp(-dtMillis.toDouble() / timeConstantMillis)
        val smoothed = previous + alpha * (altitudeMeters - previous)
        value = smoothed
        lastTimeMillis = timeMillis
        return smoothed
    }

    fun reset() {
        value = null
        rejectedInARow = 0
    }

    private fun restartAt(timeMillis: Long, altitudeMeters: Double): Double {
        value = altitudeMeters
        lastTimeMillis = timeMillis
        rejectedInARow = 0
        return altitudeMeters
    }
}
