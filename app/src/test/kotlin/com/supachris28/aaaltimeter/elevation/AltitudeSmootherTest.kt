package com.supachris28.aaaltimeter.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AltitudeSmootherTest {

    @Test
    fun firstReadingIsReturnedAsIs() {
        assertEquals(100.0, AltitudeSmoother().add(0, 100.0)!!, 1e-9)
    }

    @Test
    fun noiseIsDamped() {
        val smoother = AltitudeSmoother()
        smoother.add(0, 100.0)
        // Alternate +-10 m around 100 m, once a second.
        var worst = 0.0
        for (i in 1..120) {
            val raw = if (i % 2 == 0) 110.0 else 90.0
            val smoothed = smoother.add(i * 1000L, raw)!!
            if (i > 30) worst = maxOf(worst, abs(smoothed - 100.0))
        }
        assertTrue("smoothed wobble was $worst m", worst < 1.5)
    }

    @Test
    fun followsSteadyClimb() {
        val smoother = AltitudeSmoother(timeConstantMillis = 15_000)
        for (i in 0..300) smoother.add(i * 1000L, 100.0 + i * 0.5)
        val last = smoother.add(301_000, 100.0 + 301 * 0.5)!!
        // An EMA lags a ramp by rate * timeConstant = 0.5 m/s * 15 s = 7.5 m.
        assertEquals(250.5 - 7.5, last, 0.5)
    }

    @Test
    fun singleSpikeIsRejected() {
        val smoother = AltitudeSmoother()
        smoother.add(0, 100.0)
        smoother.add(1000, 100.0)
        assertNull(smoother.add(2000, 300.0))
        assertEquals(100.0, smoother.add(3000, 100.0)!!, 1e-9)
    }

    @Test
    fun persistentJumpIsEventuallyAccepted() {
        val smoother = AltitudeSmoother(maxConsecutiveRejections = 3)
        smoother.add(0, 100.0)
        assertNull(smoother.add(1000, 300.0))
        assertNull(smoother.add(2000, 300.0))
        assertNull(smoother.add(3000, 300.0))
        assertEquals(300.0, smoother.add(4000, 300.0)!!, 1e-9)
    }

    @Test
    fun bigChangeAfterLongGapIsNotASpike() {
        val smoother = AltitudeSmoother()
        smoother.add(0, 100.0)
        // 200 m in 5 minutes is 0.67 m/s: plausible, and after such a gap the EMA has caught up.
        val after = smoother.add(300_000, 300.0)!!
        assertEquals(300.0, after, 0.1)
    }

    @Test
    fun outOfOrderReadingIsIgnored() {
        val smoother = AltitudeSmoother()
        smoother.add(5000, 100.0)
        assertNull(smoother.add(4000, 100.0))
    }
}
