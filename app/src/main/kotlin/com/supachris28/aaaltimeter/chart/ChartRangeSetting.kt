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

    fun set(context: Context, range: ChartRange) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RANGE, range.name).apply()
    }
}

/** Short label such as "15 min" or "5 mi". */
fun ChartRange.label(context: Context): String = when (axis) {
    ChartAxis.TIME -> context.getString(R.string.range_minutes, amount)
    ChartAxis.DISTANCE -> context.getString(R.string.range_miles, amount)
}

/** Label for pickers, such as "Last 15 minutes" or "Last 1 mile". */
fun ChartRange.longLabel(context: Context): String = when (axis) {
    ChartAxis.TIME -> context.resources.getQuantityString(R.plurals.range_last_minutes, amount, amount)
    ChartAxis.DISTANCE -> context.resources.getQuantityString(R.plurals.range_last_miles, amount, amount)
}
