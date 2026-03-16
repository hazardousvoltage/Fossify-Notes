package org.fossify.notes.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.fossify.notes.extensions.config

/**
 * Small helper to request a single foreground location permission and obtain last-known location.
 *
 * Usage:
 * val helper = LocationHelper(activity) { granted -> ... }
 * helper.requestPermission()
 *
 * This helper WILL NOT request permission or return location data when the app-wide
 * `locationAccess` config toggle is disabled. Callers do not need to check the toggle
 * before using this helper; the helper enforces the master toggle to avoid accidental
 * location access.
 */
class LocationHelper(private val activity: ComponentActivity, private val callback: (Boolean) -> Unit) {
    private val requestPermissionLauncher: ActivityResultLauncher<String>

    init {
        requestPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            callback(granted)
        }
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        if (!activity.config.locationAccess) {
            callback(false)
            return
        }
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Returns last known location from best available provider or null.
     * Caller must ensure permission is granted before calling.
     */
    fun getLastKnownLocation(context: Context): Location? {
        if (!context.config.locationAccess) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val l = lm.getLastKnownLocation(provider)
                if (l != null && (bestLocation == null || l.accuracy < bestLocation.accuracy)) {
                    bestLocation = l
                }
            } catch (_: SecurityException) {
                // permission not granted
                return null
            }
        }
        return bestLocation
    }
}
