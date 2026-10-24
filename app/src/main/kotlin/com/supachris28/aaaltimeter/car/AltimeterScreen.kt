package com.supachris28.aaaltimeter.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.supachris28.aaaltimeter.R

/**
 * Placeholder car screen. GPS recording and the elevation chart arrive in later steps.
 */
class AltimeterScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val noData = carContext.getString(R.string.no_data)
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.elevation))
                    .addText(noData)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.trend))
                    .addText(noData)
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
