package com.supachris28.aaaltimeter.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.altitude.AltitudeConverter
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.supachris28.aaaltimeter.MainActivity
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.elevation.ElevationRepository
import java.io.IOException

/**
 * Foreground service that listens to GPS and feeds altitude into [ElevationRepository].
 *
 * It runs as a foreground service (with a notification) so Android keeps delivering location
 * while the phone screen is off and the app is only visible on the car display.
 */
class ElevationRecordingService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var locationManager: LocationManager
    private var listening = false

    // Last position we measured distance from. Only moved on once we've clearly travelled, so
    // GPS wander while parked doesn't add up to phantom miles.
    private var distanceAnchor: Location? = null

    // Created lazily: loading the geoid model is slow, and it only exists on Android 14+.
    private var altitudeConverter: Any? = null

    // All three callbacks are overridden on purpose: before Android 11 they have no default
    // implementation, and leaving them out crashes on older phones.
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleLocation(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Required on Android 10 and below")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("ElevationRecording").apply { start() }
        locationManager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // startForeground() must be called promptly after startForegroundService(), even if
        // we then bail out, or Android kills the app.
        try {
            startInForeground()
        } catch (e: RuntimeException) {
            // e.g. SecurityException if location permission was revoked in the meantime.
            Log.w(TAG, "Could not start foreground recording", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) {
            if (!hasLocationPermission(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL_MILLIS,
                    0f,
                    locationListener,
                    thread.looper,
                )
                listening = true
                ElevationRepository.setRecording(true)
            } catch (e: SecurityException) {
                Log.w(TAG, "Location permission missing", e)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (listening) locationManager.removeUpdates(locationListener)
        listening = false
        thread.quitSafely()
        ElevationRepository.setRecording(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleLocation(location: Location) {
        val distance = distanceMoved(location)
        val altitudeOk = location.hasAltitude() &&
            !(location.hasVerticalAccuracy() && location.verticalAccuracyMeters > MAX_VERTICAL_ACCURACY_METERS)
        ElevationRepository.onLocation(
            // elapsedRealtimeNanos is monotonic, unlike location.time (wall clock).
            location.elapsedRealtimeNanos / 1_000_000,
            if (altitudeOk) seaLevelAltitude(location) else null,
            distance,
        )
    }

    /** Metres travelled since the last counted position, or 0 if we haven't clearly moved. */
    private fun distanceMoved(location: Location): Double {
        if (location.hasAccuracy() && location.accuracy > MAX_HORIZONTAL_ACCURACY_METERS) return 0.0
        val anchor = distanceAnchor
        if (anchor == null) {
            distanceAnchor = location
            return 0.0
        }
        val meters = anchor.distanceTo(location)
        val threshold = maxOf(MIN_DISTANCE_STEP_METERS, if (location.hasAccuracy()) location.accuracy else 0f)
        if (meters < threshold) return 0.0
        distanceAnchor = location
        return meters.toDouble()
    }

    /**
     * GPS reports height above the WGS84 ellipsoid, which can be tens of metres off "height above
     * sea level" (about +50 m across the UK). Android 14 can correct this with a geoid model; on
     * older phones we fall back to the raw GPS value. The trend is unaffected either way.
     */
    private fun seaLevelAltitude(location: Location): Double {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (location.hasMslAltitude()) return location.mslAltitudeMeters
            try {
                val converter = (altitudeConverter as AltitudeConverter?)
                    ?: AltitudeConverter().also { altitudeConverter = it }
                converter.addMslAltitudeToLocation(this, location)
                if (location.hasMslAltitude()) return location.mslAltitudeMeters
            } catch (e: IOException) {
                Log.w(TAG, "Geoid model unavailable", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Could not convert altitude", e)
            }
        }
        return location.altitude
    }

    private fun startInForeground() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ElevationRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.stop_recording), stop).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ElevationRecording"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.supachris28.aaaltimeter.STOP_RECORDING"
        private const val UPDATE_INTERVAL_MILLIS = 1_000L
        private const val MAX_VERTICAL_ACCURACY_METERS = 50f
        private const val MAX_HORIZONTAL_ACCURACY_METERS = 50f
        private const val MIN_DISTANCE_STEP_METERS = 5f

        val LOCATION_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        fun hasLocationPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Starts recording if we have location permission. Returns false if we couldn't, e.g.
         * because Android doesn't let the app start a foreground service from the background.
         */
        fun start(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            return try {
                context.startForegroundService(Intent(context, ElevationRecordingService::class.java))
                true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
                Log.w(TAG, "Not allowed to start recording right now", e)
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to start recording", e)
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ElevationRecordingService::class.java))
        }
    }
}
