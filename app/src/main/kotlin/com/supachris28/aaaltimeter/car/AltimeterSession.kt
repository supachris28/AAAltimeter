package com.supachris28.aaaltimeter.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class AltimeterSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = AltimeterScreen(carContext)
}
