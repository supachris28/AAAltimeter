package com.supachris28.aaaltimeter.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.chart.ChartRangeSetting
import com.supachris28.aaaltimeter.chart.longLabel
import com.supachris28.aaaltimeter.elevation.ChartAxis
import com.supachris28.aaaltimeter.elevation.ChartRange

/**
 * Lets the driver pick the chart range from a list. Tapping an option saves it and goes back.
 *
 * Cars cap how many list rows they show (often 6 while driving), and there are 7 ranges. When
 * the cap is too low, this first asks "Time or distance?" and then shows just those options.
 * [axis] is set on that second screen.
 */
class RangePickerScreen(
    carContext: CarContext,
    private val axis: ChartAxis? = null,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val options = ChartRange.entries.filter { axis == null || it.axis == axis }
        val list = if (axis == null && options.size > listLimit()) axisList() else rangeList(options)
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.chart_range))
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }

    /** One selectable row per range, with the current one ticked. */
    private fun rangeList(options: List<ChartRange>): ItemList {
        val builder = ItemList.Builder()
        options.forEach { builder.addItem(Row.Builder().setTitle(it.longLabel(carContext)).build()) }
        val current = options.indexOf(ChartRangeSetting.get(carContext))
        if (current >= 0) builder.setSelectedIndex(current)
        builder.setOnSelectedListener { index ->
            ChartRangeSetting.set(carContext, options[index])
            // Back to the chart, past the "time or distance" screen if there was one.
            screenManager.popToRoot()
        }
        return builder.build()
    }

    private fun axisList(): ItemList {
        val builder = ItemList.Builder()
        for ((axis, title) in listOf(ChartAxis.TIME to R.string.range_time, ChartAxis.DISTANCE to R.string.range_distance)) {
            builder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(title))
                    .setOnClickListener { screenManager.push(RangePickerScreen(carContext, axis)) }
                    .build()
            )
        }
        return builder.build()
    }

    /** How many rows the car will show in a list. Car API level 1 hosts can't say, so assume 6. */
    private fun listLimit(): Int {
        if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_2) return 6
        return carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }
}
