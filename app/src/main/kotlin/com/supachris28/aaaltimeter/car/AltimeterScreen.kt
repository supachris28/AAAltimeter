package com.supachris28.aaaltimeter.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.chart.ElevationChartRenderer
import com.supachris28.aaaltimeter.elevation.ElevationRepository
import com.supachris28.aaaltimeter.elevation.Trend
import com.supachris28.aaaltimeter.recording.ElevationRecordingService
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Car screen: the elevation-over-time chart with the big climbing / descending / level readout,
 * plus the same numbers as text rows. Hosts older than Car API level 4 can't show a pane image,
 * so they get the text rows only.
 */
class AltimeterScreen(carContext: CarContext) : Screen(carContext) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastRefreshMillis = 0L
    private var refreshPending = false
    private val onElevationChanged: () -> Unit = { scheduleRefresh() }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                ElevationRepository.addListener(onElevationChanged)
            }

            override fun onStop(owner: LifecycleOwner) {
                ElevationRepository.removeListener(onElevationChanged)
                handler.removeCallbacksAndMessages(null)
                refreshPending = false
            }
        })
    }

    override fun onGetTemplate(): Template {
        lastRefreshMillis = SystemClock.elapsedRealtime()
        val snapshot = ElevationRepository.snapshot
        val current = snapshot.current
        val hasPermission = ElevationRecordingService.hasLocationPermission(carContext)
        val recording = ElevationRepository.isRecording

        val elevationText = when {
            !hasPermission -> carContext.getString(R.string.permission_needed)
            current != null -> carContext.getString(R.string.meters, current.smoothedMeters.roundToInt())
            !recording -> carContext.getString(R.string.not_recording)
            else -> carContext.getString(R.string.no_data)
        }
        val trendText = when (snapshot.trend) {
            Trend.UNKNOWN -> carContext.getString(R.string.trend_unknown)
            Trend.LEVEL -> carContext.getString(R.string.trend_level)
            Trend.CLIMBING -> carContext.getString(R.string.trend_climbing, ratePerMinute(snapshot.metersPerMinute))
            Trend.DESCENDING -> carContext.getString(R.string.trend_descending, ratePerMinute(snapshot.metersPerMinute))
        }

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.elevation))
                    .addText(elevationText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.trend))
                    .addText(trendText)
                    .build()
            )
        if (hasPermission && current != null && carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_4) {
            val chart = ElevationChartRenderer(carContext, carContext.isDarkMode).render(snapshot)
            pane.setImage(CarIcon.Builder(IconCompat.createWithBitmap(chart)).build())
        }
        when {
            !hasPermission -> pane.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.grant_permission))
                    .setOnClickListener(::requestPermission)
                    .build()
            )
            !recording -> pane.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.start_recording))
                    .setOnClickListener {
                        ElevationRecordingService.start(carContext)
                        invalidate()
                    }
                    .build()
            )
        }

        return PaneTemplate.Builder(pane.build())
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun requestPermission() {
        // Shows the permission prompt on the phone.
        carContext.requestPermissions(ElevationRecordingService.LOCATION_PERMISSIONS) { _, _ ->
            ElevationRecordingService.start(carContext)
            invalidate()
        }
    }

    /** GPS fixes arrive about once a second; refreshing the car screen that often is wasteful. */
    private fun scheduleRefresh() {
        if (refreshPending) return
        val wait = MIN_REFRESH_INTERVAL_MILLIS - (SystemClock.elapsedRealtime() - lastRefreshMillis)
        if (wait <= 0) {
            invalidate()
        } else {
            refreshPending = true
            handler.postDelayed({
                refreshPending = false
                invalidate()
            }, wait)
        }
    }

    private fun ratePerMinute(metersPerMinute: Double?): Int = abs(metersPerMinute ?: 0.0).roundToInt()

    private companion object {
        const val MIN_REFRESH_INTERVAL_MILLIS = 2_000L
    }
}
