package com.supachris28.aaaltimeter.elevation

/**
 * Keeps the recorded elevation history and the current trend. Plain Kotlin with no Android
 * dependencies, so it can be unit tested on the JVM. Not thread safe; see [ElevationRepository].
 */
class ElevationTracker(
    private val maxHistoryMillis: Long = 3 * 60 * 60 * 1000L,
    private val smoother: AltitudeSmoother = AltitudeSmoother(),
    private val trendDetector: TrendDetector = TrendDetector(),
) {
    private val points = ArrayDeque<ElevationPoint>()

    /** Adds a raw GPS reading. Returns false if it was rejected as noise. */
    fun add(timeMillis: Long, altitudeMeters: Double): Boolean {
        val smoothed = smoother.add(timeMillis, altitudeMeters) ?: return false
        points.addLast(ElevationPoint(timeMillis, altitudeMeters, smoothed))
        while (timeMillis - points.first().timeMillis > maxHistoryMillis) points.removeFirst()
        trendDetector.update(points)
        return true
    }

    fun snapshot(): ElevationSnapshot = ElevationSnapshot(
        history = points.toList(),
        trend = trendDetector.trend,
        metersPerMinute = trendDetector.rateMetersPerSecond?.times(60),
    )

    fun clear() {
        points.clear()
        smoother.reset()
        trendDetector.reset()
    }
}
