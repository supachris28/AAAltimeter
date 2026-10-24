package com.supachris28.aaaltimeter.elevation

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * App-wide holder for the recording. The recording service feeds readings in from its own
 * thread; the car screen and phone screen listen for changes. Listeners run on the main thread.
 */
object ElevationRepository {
    private val tracker = ElevationTracker()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    var snapshot: ElevationSnapshot = ElevationSnapshot.EMPTY
        private set

    @Volatile
    var isRecording: Boolean = false
        private set

    /** Main thread only. The listener is called once straight away. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
        listener()
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Called from the recording thread with each GPS fix. */
    fun onAltitude(timeMillis: Long, altitudeMeters: Double) {
        synchronized(tracker) {
            if (!tracker.add(timeMillis, altitudeMeters)) return
            snapshot = tracker.snapshot()
        }
        notifyListeners()
    }

    fun setRecording(recording: Boolean) {
        isRecording = recording
        notifyListeners()
    }

    private fun notifyListeners() {
        mainHandler.post { listeners.forEach { it() } }
    }
}
