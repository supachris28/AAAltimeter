package com.supachris28.aaaltimeter.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.elevation.ChartScale
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
 * level indicator on the right (an arrow plus words, so it never relies on colour alone). Below it is the last [ChartScale.of] window of smoothed altitude.
 *
 * Layout is in a fixed 640×400 coordinate space. The bitmap is opaque RGB_565 to halve its
 * size: it is sent to the car host over IPC on every refresh, and very large images can be
 * rejected.
 */
class ElevationChartRenderer(private val context: Context, private val dark: Boolean) {

    private val surface = if (dark) 0xFF1A1A19.toInt() else 0xFFFCFCFB.toInt()
    private val textPrimary = if (dark) 0xFFF0EFEC.toInt() else 0xFF0B0B0B.toInt()
    private val textSecondary = if (dark) 0xFFC3C2B7.toInt() else 0xFF52514E.toInt()
    private val grid = if (dark) 0xFF383835.toInt() else 0xFFE4E3DF.toInt()
    private val line = if (dark) 0xFF3987E5.toInt() else 0xFF2A78D6.toInt()
    private val climbing = if (dark) 0xFFD95926.toInt() else 0xFFEB6834.toInt()
    private val descending = if (dark) 0xFF199E70.toInt() else 0xFF1BAF7A.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Returns a [WIDTH]×[HEIGHT] bitmap, or that size times [pixelScale]. */
    fun render(snapshot: ElevationSnapshot, pixelScale: Float = 1f): Bitmap {
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
        val scale = ChartScale.of(snapshot.history)
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
        canvas.drawText(elevation, PADDING, 72f, text)
        text.textStyle(textSecondary, 24f, bold = false, align = Paint.Align.LEFT)
        canvas.drawText(context.getString(R.string.elevation), PADDING, 106f, text)

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
        canvas.drawText(headline, right, 68f, text)
        val arrowRight = right - text.measureText(headline) - 16f
        drawTrendMark(canvas, snapshot.trend, arrowRight, 50f, colour)
        text.textStyle(textSecondary, 24f, bold = false, align = Paint.Align.RIGHT)
        canvas.drawText(caption, right, 106f, text)
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
        fun px(time: Long) = plot.left + (scale.x(time) * plot.width()).toFloat()
        fun py(meters: Double) = plot.bottom - (scale.y(meters) * plot.height()).toFloat()

        stroke.color = grid
        stroke.strokeWidth = 2f
        for ((meters, label) in labels) {
            val y = py(meters)
            canvas.drawLine(plot.left, y, plot.right, y, stroke)
            canvas.drawText(label, plot.left - 12f, y + 8f, text)
        }

        // Time labels along the bottom: the start of the window and "now".
        text.textStyle(textSecondary, 22f, bold = false, align = Paint.Align.LEFT)
        val minutes = (scale.spanMillis / 60_000.0).roundToInt()
        canvas.drawText(context.getString(R.string.minutes_ago, minutes), plot.left, plot.bottom + 32f, text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(context.getString(R.string.now), plot.right, plot.bottom + 32f, text)

        val points = downsample(
            ChartScale.visiblePoints(snapshot.history, scale.spanMillis),
            scale,
            plot.width().toInt() / 2,
        )
        // Split the line wherever GPS dropped out, rather than drawing a misleading straight join.
        val segments = mutableListOf(mutableListOf(points.first()))
        for ((previous, point) in points.zipWithNext()) {
            if (point.timeMillis - previous.timeMillis > GAP_MILLIS) segments += mutableListOf(point)
            else segments.last() += point
        }

        canvas.save()
        canvas.clipRect(plot)
        for (segment in segments) {
            if (segment.size < 2) continue
            val path = Path()
            segment.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(px(p.timeMillis), py(p.smoothedMeters))
                else path.lineTo(px(p.timeMillis), py(p.smoothedMeters))
            }
            val under = Path(path).apply {
                lineTo(px(segment.last().timeMillis), plot.bottom)
                lineTo(px(segment.first().timeMillis), plot.bottom)
                close()
            }
            fill.color = Color.argb(56, Color.red(line), Color.green(line), Color.blue(line))
            canvas.drawPath(under, fill)
            stroke.color = line
            stroke.strokeWidth = 4f
            canvas.drawPath(path, stroke)
        }
        canvas.restore()

        // Current position: a dot with a surface-coloured ring so it stands off the line.
        val last = points.last()
        val x = px(last.timeMillis)
        val y = py(last.smoothedMeters)
        fill.color = surface
        canvas.drawCircle(x, y, 11f, fill)
        fill.color = line
        canvas.drawCircle(x, y, 8f, fill)
    }

    private fun Paint.textStyle(colour: Int, size: Float, bold: Boolean, align: Paint.Align) {
        color = colour
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = align
    }

    companion object {
        /** Big enough to stay sharp when the host scales it up, small enough to send every refresh. */
        const val WIDTH = 640
        const val HEIGHT = 400
        private const val PADDING = 20f
        private const val HEADER_HEIGHT = 112f
        private const val GAP_MILLIS = 60_000L
    }
}
