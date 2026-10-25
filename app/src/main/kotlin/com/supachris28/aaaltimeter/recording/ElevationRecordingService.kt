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
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.supachris28.aaaltimeter.MainActivity
import com.supachris28.aaaltimeter.R
import com.supachris28.aaaltimeter.elevation.ElevationRepository
import java.io.IOException

/**
 * Foreground service that listens to GPS and feeds altitude into [ElevationRepository].
 *
 * It runs as a foreground service (with a notification) so Android keeps delivering location
 * while the phone screen is off and the app is only visible on the car display.
 *
 * GPS runs while any of these hold:
 *  - recording was asked for, by the car screen being open or the phone's Start button;
 *  - auto-record is on ([AutoRecordSetting]) and the phone is connected to Android Auto, unless
 *    Stop was pressed during this drive.
 *
 * With auto-record on, the service also stays alive between drives with GPS off ("watching"),
 * because Android gives an app no way to be woken up when Android Auto connects.
 */
class ElevationRecordingService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var locationManager: LocationManager
    private var listening = false

    private var recordRequested = false
    private var watching = false
    private var carConnected = false
    private var stoppedThisDrive = false
    private var inForeground = false
    private var carConnection: LiveData<Int>? = null
    private val connectionObserver = Observer<Int> { type ->
        val connected = type == CarConnection.CONNECTION_TYPE_PROJECTION ||
            type == CarConnection.CONNECTION_TYPE_NATIVE
        if (connected != carConnected) {
            carConnected = connected
            if (!connected) stoppedThisDrive = false
            applyState()
        }
    }

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
        running = true
        thread = HandlerThread("ElevationRecording").apply { start() }
        locationManager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECORD -> {
                recordRequested = true
                stoppedThisDrive = false
            }
            ACTION_RELEASE -> recordRequested = false
            ACTION_STOP -> {
                recordRequested = false
                if (carConnected) stoppedThisDrive = true
            }
            ACTION_UNWATCH -> watching = false
            // ACTION_WATCH, or null when Android restarts the service after killing it.
            else -> watching = AutoRecordSetting.isEnabled(this)
        }
        applyState()
        return if (watching) START_STICKY else START_NOT_STICKY
    }

    /** Brings GPS, the notification and the Android Auto listener in line with the flags above. */
    private fun applyState() {
        val autoRecord = watching && carConnected && !stoppedThisDrive &&
            AutoRecordSetting.hasBackgroundLocation(this)
        val wantGps = hasLocationPermission(this) && (recordRequested || autoRecord)

        if (!wantGps && !watching) {
            // startForeground() must be called promptly after startForegroundService(), even if
            // we then bail out, or Android kills the app.
            if (!inForeground) {
                try {
                    startInForeground(gps = false)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not enter foreground before stopping", e)
                }
            }
            stopListening()
            stopSelf()
            return
        }

        var gps = wantGps
        try {
            startInForeground(gps)
        } catch (e: RuntimeException) {
            // e.g. SecurityException if location permission was revoked, or Android refused to
            // start location in the background.
            Log.w(TAG, "Could not start foreground recording", e)
            gps = false
            if (!watching) {
                stopSelf()
                return
            }
            try {
                startInForeground(gps = false)
            } catch (e2: RuntimeException) {
                Log.w(TAG, "Could not keep watching for Android Auto", e2)
                stopSelf()
                return
            }
        }
        if (gps) startListening() else stopListening()
        updateCarConnectionListener()
    }

    private fun startListening() {
        if (listening) return
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
        }
    }

    private fun stopListening() {
        if (!listening) return
        locationManager.removeUpdates(locationListener)
        listening = false
        distanceAnchor = null
        ElevationRepository.setRecording(false)
    }

    private fun updateCarConnectionListener() {
        if (watching && carConnection == null) {
            val live = CarConnection(this).type
            carConnection = live
            live.observeForever(connectionObserver)
        } else if (!watching && carConnection != null) {
            carConnection?.removeObserver(connectionObserver)
            carConnection = null
            carConnected = false
        }
    }

    override fun onDestroy() {
        carConnection?.removeObserver(connectionObserver)
        carConnection = null
        stopListening()
        thread.quitSafely()
        running = false
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

    private fun startInForeground(gps: Boolean) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        // Shown between drives with auto-record on. Minimum importance keeps it out of the
        // status bar; Android insists on some notification for a long-running service.
        notificationManager.createNotificationChannel(
            NotificationChannel(
                WATCHING_CHANNEL_ID,
                getString(R.string.watching_channel),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ElevationRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = if (gps) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.recording_title))
                .setContentText(getString(R.string.recording_text))
                .setContentIntent(openApp)
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(null, getString(R.string.stop_recording), stop).build()
                )
                .build()
        } else {
            Notification.Builder(this, WATCHING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.watching_title))
                .setContentText(getString(R.string.watching_text))
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // "Connected device" (the car) covers waiting for Android Auto; "location" is added
            // only while GPS is actually on.
            var type = 0
            if (gps) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (watching || !gps) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
    }

    companion object {
        private const val TAG = "ElevationRecording"
        private const val CHANNEL_ID = "recording"
        private const val WATCHING_CHANNEL_ID = "watching"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.supachris28.aaaltimeter.STOP_RECORDING"
        private const val ACTION_RECORD = "com.supachris28.aaaltimeter.RECORD"
        private const val ACTION_RELEASE = "com.supachris28.aaaltimeter.RELEASE"
        private const val ACTION_WATCH = "com.supachris28.aaaltimeter.WATCH"
        private const val ACTION_UNWATCH = "com.supachris28.aaaltimeter.UNWATCH"

        /** Main thread only. Lets us send commands without accidentally starting the service. */
        private var running = false
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
            return send(context, ACTION_RECORD, canStart = true)
        }

        /** Stops recording. With auto-record on, GPS stays off until the next Android Auto connection. */
        fun stop(context: Context) {
            send(context, ACTION_STOP, canStart = false)
        }

        /** The car screen closed. Recording carries on if auto-record wants it. */
        fun release(context: Context) {
            send(context, ACTION_RELEASE, canStart = false)
        }

        /** Starts waiting for Android Auto, if auto-record is on and allowed. */
        fun startWatching(context: Context): Boolean {
            if (!AutoRecordSetting.isEnabled(context) || !AutoRecordSetting.hasBackgroundLocation(context)) {
                return false
            }
            return send(context, ACTION_WATCH, canStart = true)
        }

        fun stopWatching(context: Context) {
            send(context, ACTION_UNWATCH, canStart = false)
        }

        private fun send(context: Context, action: String, canStart: Boolean): Boolean {
            if (!running && !canStart) return true
            val intent = Intent(context, ElevationRecordingService::class.java).setAction(action)
            return try {
                // A running foreground service can take commands from the background; only a
                // fresh start has to promise to go foreground.
                if (running) context.startService(intent) else context.startForegroundService(intent)
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
    }
}
