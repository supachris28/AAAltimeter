package com.supachris28.aaaltimeter.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Whether to record automatically whenever the phone is connected to Android Auto. */
object AutoRecordSetting {
    private const val PREFS = "recording"
    private const val KEY_AUTO = "auto_record"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /**
     * Auto-recording starts GPS while the app isn't on screen, which Android only allows with
     * location set to "Allow all the time". Before Android 10 there was no separate permission.
     */
    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ElevationRecordingService.hasLocationPermission(context)
        }
}
