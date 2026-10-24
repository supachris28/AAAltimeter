package com.supachris28.aaaltimeter

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.supachris28.aaaltimeter.chart.ChartRangeSetting
import com.supachris28.aaaltimeter.chart.ElevationChartRenderer
import com.supachris28.aaaltimeter.chart.longLabel
import com.supachris28.aaaltimeter.elevation.ChartRange
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
    private lateinit var rangePicker: Spinner
    private lateinit var renderer: ElevationChartRenderer
    private var chartScale = 1f
    private val onElevationChanged: () -> Unit = { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 18f }
        // Light or dark follows the phone's system setting (the theme does the same).
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        renderer = ElevationChartRenderer(this, dark)
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
        // Dropdown of chart ranges. The choice is shared with the car screen.
        val ranges = ChartRange.entries
        rangePicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                ranges.map { it.longLabel(this@MainActivity) },
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(ranges.indexOf(ChartRangeSetting.get(this@MainActivity)))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    ChartRangeSetting.set(this@MainActivity, ranges[position])
                    updateStatus()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(this@MainActivity).apply {
                setText(R.string.app_name)
                textSize = 28f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, 0, 0, padding / 2)
            })
            addView(TextView(this@MainActivity).apply {
                setText(R.string.phone_message)
                textSize = 16f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 0, 0, padding / 2)
            })
            addView(status)
            addView(chart)
            addView(TextView(this@MainActivity).apply { setText(R.string.chart_range) })
            addView(rangePicker)
            addView(Button(this@MainActivity).apply {
                setText(R.string.start_recording)
                setOnClickListener { startRecording() }
            })
            addView(Button(this@MainActivity).apply {
                setText(R.string.stop_recording)
                setOnClickListener { ElevationRecordingService.stop(this@MainActivity) }
            })
            addView(TextView(this@MainActivity).apply { text = versionLabel() })
        }
        // The app draws edge to edge (enforced from Android 15), so keep content clear of the
        // status and navigation bars.
        content.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                padding + insets.systemWindowInsetLeft,
                padding + insets.systemWindowInsetTop,
                padding + insets.systemWindowInsetRight,
                padding + insets.systemWindowInsetBottom,
            )
            insets
        }
        setContentView(ScrollView(this).apply { addView(content) })
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

    /** Shows which build is installed, so you can tell an update actually took. */
    private fun versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return getString(R.string.version_label, info.versionName, code)
    }

    private fun updateStatus() {
        val snapshot = ElevationRepository.snapshot
        val range = ChartRangeSetting.get(this)
        // Pick up a change made in the car while this screen was open.
        if (rangePicker.selectedItemPosition != range.ordinal) rangePicker.setSelection(range.ordinal)
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
