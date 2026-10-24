package com.supachris28.aaaltimeter.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {

    private fun points(seconds: IntProgression, altitude: (Int) -> Double) =
        seconds.map { ElevationPoint(it * 1000L, altitude(it), altitude(it)) }

    @Test
    fun needsTwoPoints() {
        assertNull(ChartScale.of(emptyList()))
        assertNull(ChartScale.of(points(0..0) { 100.0 }))
    }

    @Test
    fun shortRecordingStillShowsMinimumWindow() {
        val scale = ChartScale.of(points(0..60) { 100.0 }, minWindowMillis = 300_000)!!
        assertEquals(60_000L, scale.endMillis)
        assertEquals(300_000L, scale.spanMillis)
        assertEquals(1.0, scale.x(60_000), 1e-9)
    }

    @Test
    fun longRecordingShowsOnlyTheWindow() {
        val history = points(0..7200 step 10) { 100.0 }
        val scale = ChartScale.of(history, windowMillis = 1_800_000)!!
        assertEquals(7_200_000L, scale.endMillis)
        assertEquals(5_400_000L, scale.startMillis)
        assertEquals(181, ChartScale.visiblePoints(history, 1_800_000).size)
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
        val scale = ChartScale.of(history, minWindowMillis = 0)!!
        val thinned = downsample(history, scale, 100)
        assertTrue(thinned.size <= 100)
        assertTrue(thinned.size >= 95)
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
}
