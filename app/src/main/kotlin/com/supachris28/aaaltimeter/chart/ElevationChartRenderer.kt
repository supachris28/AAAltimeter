package com.supachris28.aaaltimeter.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.elevation.ChartAxis
import com.supachris28.aaaltimeter.elevation.ChartRange
import com.supachris28.aaaltimeter.elevation.ChartScale
import com.supachris28.aaaltimeter.elevation.ElevationPoint
import com.supachris28.aaaltimeter.elevation.ElevationSnapshot
import com.supachris28.aaaltimeter.elevation.Trend
import com.supachris28.aaaltimeter.elevation.downsample
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws the elevation chart as a bitmap, for the car screen and the phone app.
 *
 * POI apps can't draw on the car display directly, so the chart goes into the template as an
 * image. The phone app shows the same image, drawn at its screen resolution. The top strip
 * carries the big readouts: current elevation on the left, and the climbing / descending /
 * level indicator on the right (an arrow plus words, so it never relies on colour alone).
 * Below it is smoothed altitude over the chosen [ChartRange] of time or distance.
 *
 * Layout is in a fixed 640×400 coordinate space. The bitmap is opaque RGB_565 to halve its
 * size: it is sent to the car host over IPC on every refresh, and very large images can be
 * rejected.
 */
class ElevationChartRenderer(private val context: Context, private val dark: Boolean) {

    private val palette = ChartPalette.of(dark)
    private val surface = palette.surface
    private val textPrimary = palette.textPrimary
    private val textSecondary = palette.textSecondary
    private val grid = palette.grid
    private val line = palette.accent
    private val climbing = palette.climbing
    private val descending = palette.descending

    // Dither so the fill gradient doesn't band in the RGB_565 bitmap.
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Returns a [WIDTH]×[HEIGHT] bitmap, or that size times [pixelScale]. */
    fun render(snapshot: ElevationSnapshot, range: ChartRange, pixelScale: Float = 1f): Bitmap {
        val width = WIDTH
        val height = HEIGHT
        val bitmap = Bitmap.createBitmap(
            (width * pixelScale).roundToInt(),
            (height * pixelScale).roundToInt(),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(pixelScale, pixelScale)
        canvas.drawColor(surface)
        drawHeader(canvas, snapshot, width.toFloat())
        val plot = RectF(0f, HEADER_HEIGHT + 24f, width - PADDING, height - 40f)
        val scale = ChartScale.of(snapshot.history, range)
        if (scale == null) {
            text.textStyle(textSecondary, 26f, bold = false, align = Paint.Align.CENTER)
            canvas.drawText(context.getString(R.string.no_data), width / 2f, plot.centerY(), text)
        } else {
            drawChart(canvas, snapshot, scale, plot)
        }
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, snapshot: ElevationSnapshot, width: Float) {
        val current = snapshot.current
        val elevation = current?.let { context.getString(R.string.meters, it.smoothedMeters.roundToInt()) } ?: "–"
        text.textStyle(textPrimary, 64f, bold = true, align = Paint.Align.LEFT)
        text.letterSpacing = -0.02f
        canvas.drawText(elevation, PADDING, 72f, text)
        text.textStyle(textSecondary, 20f, bold = false, align = Paint.Align.LEFT)
        text.letterSpacing = 0.12f
        canvas.drawText(context.getString(R.string.elevation).uppercase(), PADDING, 104f, text)

        val rate = abs(snapshot.metersPerMinute ?: 0.0).roundToInt()
        val (headline, caption, colour) = when (snapshot.trend) {
            Trend.CLIMBING -> Triple(
                context.getString(R.string.rate_up, rate), context.getString(R.string.climbing), climbing,
            )
            Trend.DESCENDING -> Triple(
                context.getString(R.string.rate_down, rate), context.getString(R.string.descending), descending,
            )
            Trend.LEVEL -> Triple(context.getString(R.string.trend_level), "", textSecondary)
            Trend.UNKNOWN -> Triple("", context.getString(R.string.trend_unknown), textSecondary)
        }
        val right = width - PADDING
        text.textStyle(textPrimary, 52f, bold = true, align = Paint.Align.RIGHT)
        text.letterSpacing = -0.02f
        canvas.drawText(headline, right, 68f, text)
        val arrowRight = right - text.measureText(headline) - 16f
        drawTrendMark(canvas, snapshot.trend, arrowRight, 50f, colour)
        text.textStyle(textSecondary, 20f, bold = false, align = Paint.Align.RIGHT)
        text.letterSpacing = 0.12f
        canvas.drawText(caption.uppercase(), right, 104f, text)
    }

    /** A filled triangle for climbing/descending, a bar for level, centred vertically on [cy]. */
    private fun drawTrendMark(canvas: Canvas, trend: Trend, right: Float, cy: Float, colour: Int) {
        val size = 44f
        val left = right - size
        fill.color = colour
        when (trend) {
            Trend.CLIMBING, Trend.DESCENDING -> {
                val up = trend == Trend.CLIMBING
                val tip = if (up) cy - size / 2 else cy + size / 2
                val base = if (up) cy + size / 2 else cy - size / 2
                canvas.drawPath(
                    Path().apply {
                        moveTo(left + size / 2, tip)
                        lineTo(right, base)
                        lineTo(left, base)
                        close()
                    },
                    fill,
                )
            }
            Trend.LEVEL -> canvas.drawRoundRect(RectF(left, cy - 5f, right, cy + 5f), 5f, 5f, fill)
            Trend.UNKNOWN -> Unit
        }
    }

    private fun drawChart(canvas: Canvas, snapshot: ElevationSnapshot, scale: ChartScale, area: RectF) {
        // Gridlines with altitude labels on the left; the plot starts after the widest label.
        text.textStyle(textSecondary, 22f, bold = false, align = Paint.Align.RIGHT)
        val labels = scale.gridLines.map { it to context.getString(R.string.meters, it.roundToInt()) }
        val labelWidth = labels.maxOf { text.measureText(it.second) }
        val plot = RectF(PADDING + labelWidth + 12f, area.top, area.right, area.bottom)
        fun px(point: ElevationPoint) = plot.left + (scale.x(point) * plot.width()).toFloat()
        fun py(meters: Double) = plot.bottom - (scale.y(meters) * plot.height()).toFloat()

        stroke.color = grid
        stroke.strokeWidth = 1.5f
        for ((meters, label) in labels) {
            val y = py(meters)
            canvas.drawLine(plot.left, y, plot.right, y, stroke)
            canvas.drawText(label, plot.left - 12f, y + 8f, text)
        }

        // Labels along the bottom: how far back the left edge is, and "now".
        text.textStyle(textSecondary, 22f, bold = false, align = Paint.Align.LEFT)
        val ago = context.getString(R.string.range_ago, scale.range.label(context))
        canvas.drawText(ago, plot.left, plot.bottom + 32f, text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(context.getString(R.string.now), plot.right, plot.bottom + 32f, text)

        val points = downsample(
            ChartScale.visiblePoints(snapshot.history, scale.range),
            scale,
            plot.width().toInt() / 2,
        )
        // On a time axis, split the line wherever GPS dropped out rather than drawing a misleading
        // straight join. On a distance axis a long gap in time is usually just being parked.
        val segments = mutableListOf(mutableListOf(points.first()))
        for ((previous, point) in points.zipWithNext()) {
            val gap = scale.range.axis == ChartAxis.TIME && point.timeMillis - previous.timeMillis > GAP_MILLIS
            if (gap) segments += mutableListOf(point) else segments.last() += point
        }

        canvas.save()
        canvas.clipRect(plot)
        for (segment in segments) {
            if (segment.size < 2) continue
            val path = Path()
            segment.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(px(p), py(p.smoothedMeters))
                else path.lineTo(px(p), py(p.smoothedMeters))
            }
            val under = Path(path).apply {
                lineTo(px(segment.last()), plot.bottom)
                lineTo(px(segment.first()), plot.bottom)
                close()
            }
            // A soft wash of the accent that fades towards the bottom of the plot.
            fill.shader = LinearGradient(
                0f, plot.top, 0f, plot.bottom,
                Color.argb(72, Color.red(line), Color.green(line), Color.blue(line)),
                Color.argb(0, Color.red(line), Color.green(line), Color.blue(line)),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(under, fill)
            fill.shader = null
            stroke.color = line
            stroke.strokeWidth = 3.5f
            canvas.drawPath(path, stroke)
        }
        canvas.restore()

        // Current position: a dot with a surface-coloured ring so it stands off the line.
        val last = points.last()
        val x = px(last)
        val y = py(last.smoothedMeters)
        fill.color = surface
        canvas.drawCircle(x, y, 11f, fill)
        fill.color = line
        canvas.drawCircle(x, y, 8f, fill)
    }

    /** Medium weight for the big numbers rather than heavy bold; regular for labels. */
    private fun Paint.textStyle(colour: Int, size: Float, bold: Boolean, align: Paint.Align) {
        color = colour
        textSize = size
        typeface = if (bold) MEDIUM else Typeface.DEFAULT
        textAlign = align
        letterSpacing = 0f
    }

    companion object {
        /** Big enough to stay sharp when the host scales it up, small enough to send every refresh. */
        const val WIDTH = 640
        const val HEIGHT = 400
        private const val PADDING = 20f
        private const val HEADER_HEIGHT = 112f
        private const val GAP_MILLIS = 60_000L
        private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
