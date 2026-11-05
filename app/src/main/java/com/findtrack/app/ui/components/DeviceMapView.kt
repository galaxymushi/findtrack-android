package com.findtrack.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.findtrack.app.BuildConfig
import com.findtrack.app.ui.components.MapPin
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.cos
import kotlin.math.sin

/***
 * Unified map used by every map surface in the app.
 *
 * Priority order for map selection:
 *   1) If a Google Maps API key is configured (local.properties → maps.api.key),
 *      a real Google Map is shown with the user's actual location and true
 *      device markers (last-known GPS fix when available).
 *   2) Without a key, the offline stylized vector map is used so the app remains
 *      fully functional with zero configuration.
 *
 * The camera always follows the user's live GPS fix. No hardcoded fallback
 * city (Riverside Park) is ever shown — if there is no fix yet, the map
 * stays empty until one arrives.
 */
@Composable
fun DeviceMapView(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    zoom: Float = 16f,
    showControls: Boolean = true,
    showLegend: Boolean = true,
    userLocation: LatLng? = null,
    selectedAddress: String? = null,
    onPinTap: (String) -> Unit = {},
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
    onRecenter: (() -> Unit)? = null
) {
    val useGoogleMaps = BuildConfig.MAPS_API_KEY.isNotBlank()

    if (useGoogleMaps) {
        // Camera state remembers the user's GPS fix. When switching tabs and
        // coming back, userLocation is the same LatLng value, so the same
        // CameraPositionState is reused — map doesn't reset.
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                userLocation ?: LatLng(0.0, 0.0), zoom
            )
        }

        // Always fly to the user's live position whenever a new fix arrives.
        LaunchedEffect(userLocation) {
            val loc = userLocation
            if (loc != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(loc, zoom)
                )
            }
        }

        Box(modifier = modifier) {
            if (userLocation == null) {
                // No GPS fix yet — show empty map, no fake city.
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                        compassEnabled = false,
                        mapToolbarEnabled = false
                    ),
                    cameraPositionState = cameraPositionState
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    properties = MapProperties(isMyLocationEnabled = true),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                        compassEnabled = false,
                        mapToolbarEnabled = false
                    ),
                    cameraPositionState = cameraPositionState
                ) {
                    pins.forEach { pin ->
                        val coordinate = pin.realLatLng ?: estimateCoordinate(userLocation, pin)
                        val markerState = remember(pin.address, coordinate.latitude, coordinate.longitude) {
                            MarkerState(position = coordinate)
                        }
                        Marker(
                            state = markerState,
                            title = pin.calloutTitle ?: "Device",
                            snippet = pin.calloutSubtitle,
                            // Green = Bluetooth device (the phone is the native
                            // blue dot); the selected device turns amber.
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (pin.selected) BitmapDescriptorFactory.HUE_ORANGE
                                else BitmapDescriptorFactory.HUE_GREEN
                            ),
                            onClick = {
                                onPinTap(pin.address)
                                true
                            }
                        )
                    }
                }
            }

            if (showLegend) {
                MapLegend(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }

            if (showControls) {
                MapOverlayControls(
                    showControls = true,
                    onRecenter = {
                        val loc = userLocation
                        if (loc != null) {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(loc, zoom)
                            )
                        }
                        onRecenter?.invoke()
                    },
                    onZoomIn = {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(
                                cameraPositionState.position.target,
                                (cameraPositionState.position.zoom + 1f).coerceAtMost(20f)
                            )
                        )
                        onZoomIn?.invoke()
                    },
                    onZoomOut = {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(
                                cameraPositionState.position.target,
                                (cameraPositionState.position.zoom - 1f).coerceAtLeast(3f)
                            )
                        )
                        onZoomOut?.invoke()
                    }
                )
            }
        }
        return
    }

    var stylizedZoom by remember { mutableFloatStateOf(1f) }
    StylizedMap(
        pins = pins,
        modifier = modifier,
        zoom = stylizedZoom,
        showControls = showControls,
        showLegend = showLegend,
        onZoomIn = {
            stylizedZoom = (stylizedZoom + 0.25f).coerceAtMost(2.5f)
            onZoomIn?.invoke()
        },
        onZoomOut = {
            stylizedZoom = (stylizedZoom - 0.25f).coerceAtLeast(0.5f)
            onZoomOut?.invoke()
        },
        onRecenter = {
            stylizedZoom = 1f
            onRecenter?.invoke()
        },
        onPinTap = onPinTap
    )
}

private fun estimateCoordinate(origin: LatLng, pin: MapPin): LatLng {
    val distance = pin.distanceM.coerceAtMost(500.0)
    val bearingRad = Math.toRadians(deviceBearing(pin.address).toDouble())
    val earthRadius = 6_371_000.0
    val angular = distance / earthRadius
    val lat1 = Math.toRadians(origin.latitude)
    val lon1 = Math.toRadians(origin.longitude)
    val lat2 = Math.asin(
        sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearingRad)
    )
    val lon2 = lon1 + kotlin.math.atan2(
        sin(bearingRad) * sin(angular) * cos(lat1),
        cos(angular) - sin(lat1) * sin(lat2)
    )
    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

private fun deviceBearing(address: String): Float {
    var hash = 0
    for (c in address.toCharArray()) {
        hash = 31 * hash + c.code
    }
    return (hash % 360).toFloat()
}
