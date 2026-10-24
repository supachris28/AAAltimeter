package com.supachris28.aaaltimeter.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ElevationTrackerTest {

    @Test
    fun noisyClimbThenFlatGivesClimbingThenLevel() {
        val tracker = ElevationTracker()
        val random = Random(42)
        // Five minutes climbing at 0.5 m/s with +-8 m GPS noise.
        for (s in 0..300) tracker.add(s * 1000L, 100 + s * 0.5 + random.nextDouble(-8.0, 8.0))
        assertEquals(Trend.CLIMBING, tracker.snapshot().trend)
        assertEquals(30.0, tracker.snapshot().metersPerMinute!!, 6.0)

        // Then five minutes flat.
        for (s in 301..600) tracker.add(s * 1000L, 250 + random.nextDouble(-8.0, 8.0))
        val snapshot = tracker.snapshot()
        assertEquals(Trend.LEVEL, snapshot.trend)
        assertEquals(250.0, snapshot.current!!.smoothedMeters, 5.0)
    }

    @Test
    fun spikesAreNotRecorded() {
        val tracker = ElevationTracker()
        assertTrue(tracker.add(0, 100.0))
        assertFalse(tracker.add(1000, 400.0))
        assertEquals(1, tracker.snapshot().history.size)
    }

    @Test
    fun distanceAccumulatesEvenAcrossRejectedFixes() {
        val tracker = ElevationTracker()
        tracker.add(0, 100.0, 0.0)
        tracker.add(1000, 100.0, 10.0)
        assertFalse(tracker.add(2000, 400.0, 10.0)) // altitude spike, but we still moved
        tracker.addDistance(5.0) // fix with no altitude
        tracker.add(3000, 100.0, 10.0)
        val history = tracker.snapshot().history
        assertEquals(listOf(0.0, 10.0, 35.0), history.map { it.distanceMeters })
    }

    @Test
    fun oldHistoryIsDropped() {
        val tracker = ElevationTracker(maxHistoryMillis = 60_000)
        for (s in 0..120) tracker.add(s * 1000L, 100.0)
        val history = tracker.snapshot().history
        assertEquals(60_000L, history.first().timeMillis)
        assertEquals(120_000L, history.last().timeMillis)
    }

    @Test
    fun clearEmptiesEverything() {
        val tracker = ElevationTracker()
        for (s in 0..60) tracker.add(s * 1000L, 100.0)
        tracker.clear()
        assertEquals(ElevationSnapshot.EMPTY, tracker.snapshot())
    }
}
