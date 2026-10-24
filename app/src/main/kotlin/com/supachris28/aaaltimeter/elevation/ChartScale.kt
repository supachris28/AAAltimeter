package com.supachris28.aaaltimeter.elevation

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The axes of the elevation chart: which slice of time it shows and the altitude range, with
 * gridlines on round numbers. Plain Kotlin so it can be unit tested; drawing lives in the car
 * package.
 */
data class ChartScale(
    val startMillis: Long,
    val endMillis: Long,
    val minMeters: Double,
    val maxMeters: Double,
    val gridStepMeters: Double,
) {
    val spanMillis: Long get() = endMillis - startMillis

    /** Altitudes for the horizontal gridlines, bottom to top, including both edges. */
    val gridLines: List<Double>
        get() {
            val count = ((maxMeters - minMeters) / gridStepMeters).roundToInt()
            return (0..count).map { minMeters + it * gridStepMeters }
        }

    /** 0 at the left edge, 1 at the right ("now"). */
    fun x(timeMillis: Long): Double = (timeMillis - startMillis).toDouble() / spanMillis

    /** 0 at the bottom, 1 at the top. */
    fun y(meters: Double): Double = (meters - minMeters) / (maxMeters - minMeters)

    companion object {
        /**
         * Works out the axes for [history] (in time order). "Now" is the newest point, at the
         * right edge. The chart shows up to [windowMillis] back, but never less than
         * [minWindowMillis] so the first few minutes don't look zoomed in. The altitude range
         * is at least [minRangeMeters] so GPS jitter on flat ground doesn't look like hills.
         *
         * Returns null when there are fewer than two points to draw.
         */
        fun of(
            history: List<ElevationPoint>,
            windowMillis: Long = 30 * 60_000L,
            minWindowMillis: Long = 5 * 60_000L,
            minRangeMeters: Double = 20.0,
            targetGridLines: Int = 4,
        ): ChartScale? {
            val visible = visiblePoints(history, windowMillis)
            if (visible.size < 2) return null
            val end = visible.last().timeMillis
            val span = (end - visible.first().timeMillis).coerceIn(minWindowMillis, windowMillis)

            var low = visible.minOf { it.smoothedMeters }
            var high = visible.maxOf { it.smoothedMeters }
            if (high - low < minRangeMeters) {
                val middle = (low + high) / 2
                low = middle - minRangeMeters / 2
                high = middle + minRangeMeters / 2
            }
            val step = niceStep((high - low) / targetGridLines)
            return ChartScale(
                startMillis = end - span,
                endMillis = end,
                minMeters = floor(low / step) * step,
                maxMeters = ceil(high / step) * step,
                gridStepMeters = step,
            )
        }

        /** The points inside the last [windowMillis] of [history]. */
        fun visiblePoints(history: List<ElevationPoint>, windowMillis: Long): List<ElevationPoint> {
            val last = history.lastOrNull() ?: return emptyList()
            val start = last.timeMillis - windowMillis
            var first = history.size - 1
            while (first > 0 && history[first - 1].timeMillis >= start) first--
            return history.subList(first, history.size)
        }

        /** The smallest of 1, 2, 5, 10, 20, 50… that is at least [rough]. */
        fun niceStep(rough: Double): Double {
            if (rough <= 0) return 1.0
            val magnitude = 10.0.pow(floor(log10(rough)))
            return listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rough - 1e-9 }
        }
    }
}

/**
 * Thins [points] to at most [buckets] by averaging the points that fall in each equal slice of
 * the chart's time span. A 30 minute window holds about 1,800 GPS fixes; the chart is only a
 * few hundred pixels wide.
 */
fun downsample(points: List<ElevationPoint>, scale: ChartScale, buckets: Int): List<ElevationPoint> {
    if (points.size <= buckets) return points
    val result = ArrayList<ElevationPoint>(buckets)
    var bucket = -1
    var count = 0
    var time = 0L
    var raw = 0.0
    var smoothed = 0.0
    fun flush() {
        if (count > 0) result += ElevationPoint(time / count, raw / count, smoothed / count)
    }
    for (p in points) {
        val b = (scale.x(p.timeMillis) * buckets).toInt().coerceIn(0, buckets - 1)
        if (b != bucket) {
            flush()
            bucket = b
            count = 0
            time = 0L
            raw = 0.0
            smoothed = 0.0
        }
        count++
        time += p.timeMillis
        raw += p.rawMeters
        smoothed += p.smoothedMeters
    }
    flush()
    return result
}
