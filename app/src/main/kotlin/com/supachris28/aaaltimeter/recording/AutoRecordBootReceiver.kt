package com.supachris28.aaaltimeter.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the Android Auto watcher after the phone reboots or the app is updated. */
class AutoRecordBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                ElevationRecordingService.startWatching(context)
        }
    }
}
