package com.supachris28.aaaltimeter

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.supachris28.aaaltimeter.chart.ChartRangeSetting
import com.supachris28.aaaltimeter.chart.ElevationChartRenderer
import com.supachris28.aaaltimeter.chart.label
import com.supachris28.aaaltimeter.elevation.ElevationRepository
import com.supachris28.aaaltimeter.recording.ElevationRecordingService
import kotlin.math.roundToInt

/**
 * Phone-side screen. The main UI lives on the car screen; this is for granting permissions,
 * starting recording without a car, and seeing the same chart the car shows.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var chart: ImageView
    private lateinit var rangeButton: Button
    private lateinit var renderer: ElevationChartRenderer
    private var chartScale = 1f
    private val onElevationChanged: () -> Unit = { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 18f }
        renderer = ElevationChartRenderer(this, dark = false)
        // Draw at the size the image will actually occupy so it isn't upscaled and blurry: the
        // full width in portrait, but no more than half the screen height in landscape.
        val metrics = resources.displayMetrics
        chartScale = minOf(
            (metrics.widthPixels - 2 * padding).toFloat() / ElevationChartRenderer.WIDTH,
            metrics.heightPixels / 2f / ElevationChartRenderer.HEIGHT,
        )
        chart = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, padding / 2, 0, padding / 2)
        }
        // Steps through 30/15/5 min, then 30/15/5/1 mi. Shared with the car screen.
        rangeButton = Button(this).apply {
            setOnClickListener {
                ChartRangeSetting.cycle(this@MainActivity)
                updateStatus()
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
                addView(TextView(this@MainActivity).apply {
                    setText(R.string.phone_message)
                    textSize = 18f
                })
                addView(status)
                addView(chart)
                addView(rangeButton)
                addView(Button(this@MainActivity).apply {
                    setText(R.string.start_recording)
                    setOnClickListener { startRecording() }
                })
                addView(Button(this@MainActivity).apply {
                    setText(R.string.stop_recording)
                    setOnClickListener { ElevationRecordingService.stop(this@MainActivity) }
                })
            }
        )
    }

    override fun onStart() {
        super.onStart()
        ElevationRepository.addListener(onElevationChanged)
    }

    override fun onStop() {
        ElevationRepository.removeListener(onElevationChanged)
        super.onStop()
    }

    private fun startRecording() {
        val missing = buildList {
            addAll(ElevationRecordingService.LOCATION_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }

        if (missing.isEmpty()) {
            ElevationRecordingService.start(this)
        } else {
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            // Notifications are optional; only location is needed to record.
            ElevationRecordingService.start(this)
            updateStatus()
        }
    }

    private fun updateStatus() {
        val snapshot = ElevationRepository.snapshot
        val range = ChartRangeSetting.get(this)
        rangeButton.text = getString(R.string.range_button, range.label(this))
        chart.setImageBitmap(renderer.render(snapshot, range, chartScale))
        val current = snapshot.current
        status.text = when {
            !ElevationRepository.isRecording -> getString(R.string.not_recording)
            current == null -> getString(R.string.no_data)
            else -> getString(
                R.string.phone_status,
                current.smoothedMeters.roundToInt(),
                snapshot.trend.name.lowercase(),
                snapshot.history.size,
            )
        }
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
    }
}
