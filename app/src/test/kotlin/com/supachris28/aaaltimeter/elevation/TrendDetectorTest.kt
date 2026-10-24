package com.supachris28.aaaltimeter.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendDetectorTest {

    private fun points(seconds: IntRange, altitude: (Int) -> Double) =
        seconds.map { ElevationPoint(it * 1000L, altitude(it), altitude(it)) }

    @Test
    fun unknownWithoutEnoughData() {
        val detector = TrendDetector(minSpanMillis = 20_000)
        assertEquals(Trend.UNKNOWN, detector.update(emptyList()))
        assertEquals(Trend.UNKNOWN, detector.update(points(0..10) { 100.0 }))
        assertNull(detector.rateMetersPerSecond)
    }

    @Test
    fun flatIsLevel() {
        assertEquals(Trend.LEVEL, TrendDetector().update(points(0..60) { 100.0 }))
    }

    @Test
    fun climbIsDetectedWithRate() {
        val detector = TrendDetector()
        assertEquals(Trend.CLIMBING, detector.update(points(0..60) { 100.0 + it * 0.5 }))
        assertEquals(0.5, detector.rateMetersPerSecond!!, 1e-9)
    }

    @Test
    fun descentIsDetected() {
        assertEquals(Trend.DESCENDING, TrendDetector().update(points(0..60) { 500.0 - it * 0.3 }))
    }

    @Test
    fun onlyRecentWindowCounts() {
        // Climbed hard ten minutes ago, flat for the last two minutes.
        val history = points(0..600) { if (it < 480) it * 1.0 else 480.0 }
        assertEquals(Trend.LEVEL, TrendDetector(windowMillis = 60_000).update(history))
    }

    @Test
    fun hysteresisKeepsClimbingOnGentlerSlope() {
        val detector = TrendDetector(enterRateMetersPerSecond = 0.15, exitRateMetersPerSecond = 0.075)
        // 0.1 m/s on its own isn't enough to start climbing...
        assertEquals(Trend.LEVEL, detector.update(points(0..60) { it * 0.1 }))
        // ...but once climbing, it's enough to stay climbing.
        detector.update(points(0..60) { it * 0.5 })
        assertEquals(Trend.CLIMBING, detector.update(points(0..60) { it * 0.1 }))
        // Below the exit rate it drops back to level.
        assertEquals(Trend.LEVEL, detector.update(points(0..60) { it * 0.05 }))
    }
}
