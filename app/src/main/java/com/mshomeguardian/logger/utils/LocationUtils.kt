package com.mshomeguardian.logger.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LocationUtils {
    private const val TAG = "LocationUtils"

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        // Check for foreground location permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Foreground location permission not granted")
            return null
        }

        // For Android 10+, check for background permission as well
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Background location permission not granted")
                return null
            }
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check if location services are enabled
        val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.e(TAG, "Location services are disabled")
            return null
        }

        // Try to get last known location first (fastest method)
        val lastKnownLocation = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }
            .minByOrNull { it.accuracy }

        if (lastKnownLocation != null) {
            Log.d(TAG, "Using last known location: lat=${lastKnownLocation.latitude}, lng=${lastKnownLocation.longitude}")
            return lastKnownLocation
        }

        // If no last known location, try to get a fresh location
        // (this is a blocking call but has a timeout)
        return try {
            Log.d(TAG, "No last known location, requesting fresh location")
            requestSingleLocationUpdate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fresh location", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleLocationUpdate(context: Context): Location? {
        // No need to check permissions again, already checked in getLastKnownLocation

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val latch = CountDownLatch(1)
        var location: Location? = null

        // Check if any provider is enabled
        val providers = lm.getProviders(true)
        if (providers.isEmpty()) {
            Log.e(TAG, "No location providers enabled")
            return null
        }

        // Use GPS provider if available, otherwise use network provider
        val provider = if (providers.contains(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else {
            providers.first()
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                Log.d(TAG, "New location received: lat=${loc.latitude}, lng=${loc.longitude}")
                location = loc
                latch.countDown()
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider disabled: $provider")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider enabled: $provider")
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d(TAG, "Provider status changed: $provider, status=$status")
            }
        }

        try {
            lm.requestLocationUpdates(provider, 0, 0f, listener, Looper.getMainLooper())

            // Wait up to 10 seconds for a location update
            val success = latch.await(10, TimeUnit.SECONDS)

            if (!success) {
                Log.w(TAG, "Timeout waiting for location update")
            }

            return location
        } catch (e: Exception) {
            Log.e(TAG, "Error in location request", e)
            return null
        } finally {
            lm.removeUpdates(listener)
        }
    }
}