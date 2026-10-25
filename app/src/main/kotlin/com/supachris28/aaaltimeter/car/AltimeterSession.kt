package com.supachris28.aaaltimeter.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.supachris28.aaaltimeter.recording.ElevationRecordingService

/**
 * One session per car connection. Recording runs for as long as the app is open in the car, and
 * for the whole drive if auto-record is on.
 */
class AltimeterSession : Session() {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                ElevationRecordingService.start(carContext)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                ElevationRecordingService.release(carContext)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen = AltimeterScreen(carContext)
}
