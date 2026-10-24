package com.supachris28.aaaltimeter.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {

    /** One point per second, travelling [metersPerSecond]. */
    private fun points(seconds: IntProgression, metersPerSecond: Double = 0.0, altitude: (Int) -> Double) =
        seconds.map { ElevationPoint(it * 1000L, altitude(it), altitude(it), it * metersPerSecond) }

    @Test
    fun needsTwoPoints() {
        assertNull(ChartScale.of(emptyList()))
        assertNull(ChartScale.of(points(0..0) { 100.0 }))
    }

    @Test
    fun shortRecordingStillShowsWholeRange() {
        val history = points(0..60) { 100.0 }
        val scale = ChartScale.of(history, ChartRange.MINUTES_5)!!
        assertEquals(60_000.0, scale.endX, 1e-9)
        assertEquals(-240_000.0, scale.startX, 1e-9)
        assertEquals(1.0, scale.x(history.last()), 1e-9)
        assertEquals(0.8, scale.x(history.first()), 1e-9)
    }

    @Test
    fun longRecordingShowsOnlyTheRange() {
        val history = points(0..7200 step 10) { 100.0 }
        val scale = ChartScale.of(history, ChartRange.MINUTES_30)!!
        assertEquals(7_200_000.0, scale.endX, 1e-9)
        assertEquals(5_400_000.0, scale.startX, 1e-9)
        assertEquals(181, ChartScale.visiblePoints(history, ChartRange.MINUTES_30).size)
    }

    @Test
    fun distanceRangeUsesDistanceTravelled() {
        // 20 m/s for an hour is 72 km; the last mile is the last ~80 seconds.
        val history = points(0..3600, metersPerSecond = 20.0) { if (it < 3500) 100.0 else 100.0 + (it - 3500) }
        val visible = ChartScale.visiblePoints(history, ChartRange.MILES_1)
        assertEquals(81, visible.size)
        val scale = ChartScale.of(history, ChartRange.MILES_1)!!
        assertEquals(72_000.0, scale.endX, 1e-9)
        assertEquals(72_000.0 - 1609.344, scale.startX, 1e-9)
        assertEquals(0.0, scale.x(visible.first()), 0.02)
        // Only the visible stretch sets the altitude axis.
        assertTrue(scale.maxMeters >= 200.0)
        assertTrue(scale.minMeters >= 50.0)
    }

    @Test
    fun parkedPointsShareTheSameDistance() {
        // Driving for a minute, then parked for ten: on a distance axis the parked points stack up
        // at "now" instead of stretching the chart.
        val driving = points(0..60, metersPerSecond = 10.0) { 100.0 }
        val parked = (61..660).map { ElevationPoint(it * 1000L, 100.0, 100.0, 600.0) }
        val scale = ChartScale.of(driving + parked, ChartRange.MILES_1)!!
        assertEquals(600.0, scale.endX, 1e-9)
        assertEquals(1.0, scale.x(parked.first()), 1e-9)
    }

    @Test
    fun flatGroundGetsMinimumRange() {
        val scale = ChartScale.of(points(0..60) { 100.0 + (it % 2) }, minRangeMeters = 20.0)!!
        assertTrue(scale.maxMeters - scale.minMeters >= 20.0)
        assertTrue(scale.minMeters <= 100.0 && scale.maxMeters >= 101.0)
    }

    @Test
    fun rangeSnapsToRoundGridlines() {
        // 123 m to 287 m: rough step 41 rounds up to 50, so 100 to 300.
        val scale = ChartScale.of(points(0..100) { 123.0 + it * 1.64 })!!
        assertEquals(50.0, scale.gridStepMeters, 1e-9)
        assertEquals(100.0, scale.minMeters, 1e-9)
        assertEquals(300.0, scale.maxMeters, 1e-9)
        assertEquals(listOf(100.0, 150.0, 200.0, 250.0, 300.0), scale.gridLines)
        assertEquals(0.5, scale.y(200.0), 1e-9)
    }

    @Test
    fun niceSteps() {
        assertEquals(1.0, ChartScale.niceStep(0.7), 1e-9)
        assertEquals(5.0, ChartScale.niceStep(5.0), 1e-9)
        assertEquals(10.0, ChartScale.niceStep(6.0), 1e-9)
        assertEquals(20.0, ChartScale.niceStep(12.0), 1e-9)
        assertEquals(500.0, ChartScale.niceStep(250.0), 1e-9)
    }

    @Test
    fun downsampleAveragesIntoBuckets() {
        val history = points(0..599) { it.toDouble() }
        val scale = ChartScale.of(history, ChartRange.MINUTES_15)!!
        val thinned = downsample(history, scale, 100)
        assertTrue(thinned.size <= 100)
        // The ten minutes of data fill two thirds of the fifteen minute chart.
        assertTrue(thinned.size >= 60)
        // Averaging a straight line keeps it straight and in order.
        thinned.zipWithNext().forEach { (a, b) -> assertTrue(b.timeMillis > a.timeMillis) }
        thinned.forEach { assertEquals(it.timeMillis / 1000.0, it.smoothedMeters, 1e-3) }
    }

    @Test
    fun downsampleLeavesSmallInputAlone() {
        val history = points(0..10) { 100.0 }
        val scale = ChartScale.of(history)!!
        assertEquals(history, downsample(history, scale, 100))
    }

    @Test
    fun rangeRoundTripsByName() {
        ChartRange.entries.forEach { assertEquals(it, ChartRange.fromName(it.name)) }
        assertEquals(ChartRange.DEFAULT, ChartRange.fromName(null))
        assertEquals(ChartRange.DEFAULT, ChartRange.fromName("NOT_A_RANGE"))
    }
}
