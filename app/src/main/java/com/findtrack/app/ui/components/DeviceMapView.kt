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
 * Only devices with a recorded GPS fix get a marker: a BLE scan yields a
 * distance estimate, never a direction, so plotting a "device position" from
 * signal strength alone would invent a location. Devices without a fix are
 * explained by the surrounding UI instead.
 *
 * [focus] is what the screen is about — the device's last-known fix when one
 * exists, otherwise the phone. The camera centers on it and then leaves the
 * user's pan/zoom alone.
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
    focus: LatLng? = null,
    onPinTap: (String) -> Unit = {},
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
    onRecenter: (() -> Unit)? = null
) {
    val useGoogleMaps = BuildConfig.MAPS_API_KEY.isNotBlank()

    if (useGoogleMaps) {
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                focus ?: userLocation ?: LatLng(0.0, 0.0), zoom
            )
        }

        // Center on the device whose fix the screen is showing. Keyed on the
        // target itself: new GPS ticks and manual pan/zoom no longer yank the
        // camera back.
        LaunchedEffect(focus?.latitude, focus?.longitude) {
            val target = focus ?: return@LaunchedEffect
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target, zoom)
            )
        }

        // The first fix can arrive after the map opened. Center on the phone
        // once, then hand the camera back to the user.
        var centeredOnUser by remember { mutableStateOf(false) }
        LaunchedEffect(userLocation) {
            val loc = userLocation
            if (loc != null && focus == null && !centeredOnUser) {
                centeredOnUser = true
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(loc, zoom)
                )
            }
        }

        Box(modifier = modifier) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                properties = MapProperties(isMyLocationEnabled = userLocation != null),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = false,
                    mapToolbarEnabled = false
                ),
                cameraPositionState = cameraPositionState
            ) {
                pins.forEach { pin ->
                    val coordinate = pin.realLatLng ?: return@forEach
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
                        val target = focus ?: userLocation
                        if (target != null) {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(
                                    target,
                                    cameraPositionState.position.zoom
                                )
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
