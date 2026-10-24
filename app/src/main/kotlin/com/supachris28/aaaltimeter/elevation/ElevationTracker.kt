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
    private var distanceMeters = 0.0

    /**
     * Adds a raw GPS reading, having moved [distanceDeltaMeters] since the previous fix.
     * Returns false if the altitude was rejected as noise; the distance still counts.
     */
    fun add(timeMillis: Long, altitudeMeters: Double, distanceDeltaMeters: Double = 0.0): Boolean {
        addDistance(distanceDeltaMeters)
        val smoothed = smoother.add(timeMillis, altitudeMeters) ?: return false
        points.addLast(ElevationPoint(timeMillis, altitudeMeters, smoothed, distanceMeters))
        while (timeMillis - points.first().timeMillis > maxHistoryMillis) points.removeFirst()
        trendDetector.update(points)
        return true
    }

    /** For fixes with a position but no usable altitude. */
    fun addDistance(meters: Double) {
        distanceMeters += meters
    }

    fun snapshot(): ElevationSnapshot = ElevationSnapshot(
        history = points.toList(),
        trend = trendDetector.trend,
        metersPerMinute = trendDetector.rateMetersPerSecond?.times(60),
    )

    fun clear() {
        points.clear()
        distanceMeters = 0.0
        smoother.reset()
        trendDetector.reset()
    }
}
