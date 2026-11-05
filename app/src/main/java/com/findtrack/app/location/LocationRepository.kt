package com.findtrack.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.findtrack.app.ble.TrackedDevice
import com.findtrack.app.data.HistoryDao
import com.findtrack.app.data.HistoryEntity
import com.findtrack.app.util.PlaceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Captures the phone's own GPS position so History can store real
 * "last seen" places when a tracked device is observed or lost.
 *
 * Prefers Google's FusedLocationProviderClient (best accuracy/efficiency on
 * Pixel & Play services devices) and falls back to the framework
 * LocationManager on AOSP builds without Play services.
 */
class LocationRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _lastFix = MutableStateFlow<LatLng?>(null)

    /** Last known position of this phone, if any. */
    val lastFix: StateFlow<LatLng?> = _lastFix.asStateFlow()

    data class LatLng(val lat: Double, val lon: Double)

    private val manager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /** Google's fused provider — best accuracy/efficiency on Play services devices. */
    private val fused: com.google.android.gms.location.FusedLocationProviderClient? = try {
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
    } catch (_: Throwable) {
        null
    }

    private var listener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Warm up: cache the best last-known location right away. */
    fun prime() {
        if (!hasPermission()) return
        bestLastKnown()?.let { _lastFix.value = LatLng(it.latitude, it.longitude) }
        startUpdates()
    }

    private fun bestLastKnown(): Location? {
        val m = manager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            try {
                if (!m.isProviderEnabled(p)) continue
                val l = m.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return best
    }

    private fun startUpdates() {
        val m = manager ?: return
        if (!hasPermission() || listener != null) return
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _lastFix.value = LatLng(location.latitude, location.longitude)
            }

            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        listener = l
        try {
            val provider = when {
                m.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                m.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                m.requestLocationUpdates(provider, 5_000L, 1f, l, Looper.getMainLooper())
            } else {
                @Suppress("DEPRECATION")
                m.requestLocationUpdates(provider, 5_000L, 1f, l)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    /**
     * One-shot fix with a short timeout. Prefers Google's fused provider
     * (best accuracy on Play services devices), falls back to the framework
     * LocationManager. Returns null when location is unavailable or permission
     * is missing — callers degrade gracefully.
     */
    suspend fun currentFix(timeoutMs: Long = 6_000L): LatLng? {
        _lastFix.value?.let { return it }
        if (!hasPermission()) return null

        // 1) Fused provider (Google) — most accurate on production devices.
        fused?.let { f ->
            try {
                @Suppress("MissingPermission")
                val fusedFix = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    @Suppress("MissingPermission")
                    f.lastLocation
                        .addOnSuccessListener { loc ->
                            if (loc != null && !cont.isCancelled) cont.resume(loc) else cont.resume(null)
                        }
                        .addOnFailureListener { cont.resume(null) }
                    // lastLocation may never resolve on some devices; safety timeout below.
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (cont.isActive) cont.resume(null)
                    }, 2_000L)
                }
                if (fusedFix != null) {
                    val fix = LatLng(fusedFix.latitude, fusedFix.longitude)
                    _lastFix.value = fix
                    return fix
                }
            } catch (_: Throwable) {
            }
        }

        // 2) Framework last-known
        bestLastKnown()?.let {
            val fix = LatLng(it.latitude, it.longitude)
            _lastFix.value = fix
            return fix
        }

        val m = manager ?: return null
        val provider = when {
            m.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            m.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<LatLng?> { cont ->
                val oneShot = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            m.removeUpdates(this)
                        } catch (_: Exception) {
                        }
                        val fix = LatLng(location.latitude, location.longitude)
                        _lastFix.value = fix
                        if (cont.isActive) cont.resume(fix)
                    }

                    @Deprecated("Deprecated in API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) =
                        Unit

                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                try {
                    m.requestLocationUpdates(provider, 0L, 0f, oneShot, Looper.getMainLooper())
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
                // Safety timeout cleanup
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        m.removeUpdates(oneShot)
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resume(null)
                }, timeoutMs + 250)
            }
        }
    }

    /**
     * Persist a history entry for a device sighting, coalescing entries that
     * happen within [minIntervalMs] of the previous one for the same device.
     */
    fun recordSighting(
        dao: HistoryDao,
        device: TrackedDevice,
        displayName: String = device.name,
        minIntervalMs: Long = 10 * 60 * 1000L
    ) {
        scope.launch {
            try {
                val last = dao.lastTimestampFor(device.address)
                if (last != null && System.currentTimeMillis() - last < minIntervalMs) return@launch
                val fix = currentFix()
                dao.insert(
                    HistoryEntity(
                        address = device.address,
                        name = displayName,
                        rssi = device.rssi,
                        lat = fix?.lat,
                        lon = fix?.lon,
                        place = PlaceResolver.resolve(fix?.lat, fix?.lon),
                        timestamp = device.lastSeen,
                        isDemo = device.isDemo
                    )
                )
            } catch (_: Exception) {
            }
        }
    }
}
