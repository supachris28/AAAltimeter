package com.supachris28.aaaltimeter.chart

import android.content.Context
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.elevation.ChartAxis
import com.supachris28.aaaltimeter.elevation.ChartRange

/** The chart range the user last picked, shared by the car and phone screens. */
object ChartRangeSetting {
    private const val PREFS = "chart"
    private const val KEY_RANGE = "range"

    fun get(context: Context): ChartRange = ChartRange.fromName(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RANGE, null)
    )

    /** Moves to the next range and returns it. */
    fun cycle(context: Context): ChartRange {
        val next = get(context).next()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RANGE, next.name).apply()
        return next
    }
}

/** Short label such as "15 min" or "5 mi". */
fun ChartRange.label(context: Context): String = when (axis) {
    ChartAxis.TIME -> context.getString(R.string.range_minutes, amount)
    ChartAxis.DISTANCE -> context.getString(R.string.range_miles, amount)
}
