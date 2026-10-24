package com.supachris28.aaaltimeter.elevation

/** What the chart's horizontal axis measures. */
enum class ChartAxis { TIME, DISTANCE }

/**
 * How far back the chart looks: a span of time or of distance travelled, ending "now" at the
 * right edge. The order here is the order the range button cycles through.
 */
enum class ChartRange(val axis: ChartAxis, val amount: Int, val span: Double) {
    MINUTES_30(ChartAxis.TIME, 30, 30 * MILLIS_PER_MINUTE),
    MINUTES_15(ChartAxis.TIME, 15, 15 * MILLIS_PER_MINUTE),
    MINUTES_5(ChartAxis.TIME, 5, 5 * MILLIS_PER_MINUTE),
    MILES_30(ChartAxis.DISTANCE, 30, 30 * METERS_PER_MILE),
    MILES_15(ChartAxis.DISTANCE, 15, 15 * METERS_PER_MILE),
    MILES_5(ChartAxis.DISTANCE, 5, 5 * METERS_PER_MILE),
    MILES_1(ChartAxis.DISTANCE, 1, 1 * METERS_PER_MILE);

    /** The point's position along this range's axis: milliseconds or metres. */
    fun xOf(point: ElevationPoint): Double = when (axis) {
        ChartAxis.TIME -> point.timeMillis.toDouble()
        ChartAxis.DISTANCE -> point.distanceMeters
    }

    fun next(): ChartRange = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = MINUTES_30

        /** Looks up a stored [name], falling back to [DEFAULT] for anything unrecognised. */
        fun fromName(name: String?): ChartRange = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private const val MILLIS_PER_MINUTE = 60_000.0
private const val METERS_PER_MILE = 1609.344
