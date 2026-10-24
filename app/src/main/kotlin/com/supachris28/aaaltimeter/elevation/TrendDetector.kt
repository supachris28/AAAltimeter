package com.supachris28.aaaltimeter.elevation

import kotlin.math.abs

/**
 * Decides whether we're climbing, descending or level.
 *
 * Fits a straight line (least squares) through the smoothed altitude over the last
 * [windowMillis] and looks at its slope. Hysteresis stops the indicator flickering: you need
 * [enterRateMetersPerSecond] to start climbing or descending, but only have to stay above
 * [exitRateMetersPerSecond] to keep it.
 *
 * The defaults (0.15 m/s to enter, about 9 m/min) roughly match a 1% gradient at 50 km/h.
 */
class TrendDetector(
    private val windowMillis: Long = 60_000,
    private val minSpanMillis: Long = 20_000,
    private val enterRateMetersPerSecond: Double = 0.15,
    private val exitRateMetersPerSecond: Double = 0.075,
) {
    var trend = Trend.UNKNOWN
        private set

    /** Slope over the last window, in metres per second, or null if there isn't enough data. */
    var rateMetersPerSecond: Double? = null
        private set

    /** [history] must be in time order; only the tail inside the window is used. */
    fun update(history: List<ElevationPoint>): Trend {
        val slope = slope(history)
        rateMetersPerSecond = slope
        trend = when {
            slope == null -> Trend.UNKNOWN
            trend == Trend.CLIMBING && slope > exitRateMetersPerSecond -> Trend.CLIMBING
            trend == Trend.DESCENDING && slope < -exitRateMetersPerSecond -> Trend.DESCENDING
            slope >= enterRateMetersPerSecond -> Trend.CLIMBING
            slope <= -enterRateMetersPerSecond -> Trend.DESCENDING
            else -> Trend.LEVEL
        }
        return trend
    }

    fun reset() {
        trend = Trend.UNKNOWN
        rateMetersPerSecond = null
    }

    private fun slope(history: List<ElevationPoint>): Double? {
        val last = history.lastOrNull() ?: return null
        val start = last.timeMillis - windowMillis
        var firstIndex = history.size - 1
        while (firstIndex > 0 && history[firstIndex - 1].timeMillis >= start) firstIndex--
        val window = history.subList(firstIndex, history.size)
        if (window.size < 3 || last.timeMillis - window.first().timeMillis < minSpanMillis) return null

        // Times in seconds relative to the first point keep the numbers small and precise.
        val t0 = window.first().timeMillis
        val n = window.size
        val meanT = window.sumOf { (it.timeMillis - t0) / 1000.0 } / n
        val meanA = window.sumOf { it.smoothedMeters } / n
        var num = 0.0
        var den = 0.0
        for (p in window) {
            val dt = (p.timeMillis - t0) / 1000.0 - meanT
            num += dt * (p.smoothedMeters - meanA)
            den += dt * dt
        }
        if (abs(den) < 1e-9) return null
        return num / den
    }
}
