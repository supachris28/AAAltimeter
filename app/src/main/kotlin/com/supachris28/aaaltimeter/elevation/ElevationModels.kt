package com.supachris28.aaaltimeter.elevation

/**
 * One recorded altitude reading.
 *
 * [timeMillis] is a monotonic clock (elapsed realtime), not wall-clock time, so it never jumps
 * when the phone's clock is adjusted. Use differences between points, not absolute values.
 */
data class ElevationPoint(
    val timeMillis: Long,
    val rawMeters: Double,
    val smoothedMeters: Double,
)

enum class Trend { CLIMBING, DESCENDING, LEVEL, UNKNOWN }

/** Everything the car screen needs: the history for the chart plus the current reading. */
data class ElevationSnapshot(
    val history: List<ElevationPoint>,
    val trend: Trend,
    /** Rate of climb over the trend window, in metres per minute (negative when descending). */
    val metersPerMinute: Double?,
) {
    val current: ElevationPoint? get() = history.lastOrNull()

    companion object {
        val EMPTY = ElevationSnapshot(emptyList(), Trend.UNKNOWN, null)
    }
}
