package com.supachris28.aaaltimeter.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.supachris28.aaaltimeter.R

/** Entry point Android Auto binds to when the user opens the app on the car screen. */
class AltimeterCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Debug builds accept any host (e.g. the Desktop Head Unit); release builds only
        // trust the known Android Auto / Automotive hosts.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = AltimeterSession()
}
