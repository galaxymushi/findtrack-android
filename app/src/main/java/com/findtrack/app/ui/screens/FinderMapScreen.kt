package com.findtrack.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findtrack.app.ble.DistanceEstimator
import com.findtrack.app.ui.FindTrackViewModel
import com.findtrack.app.ui.components.BackTopBar
import com.findtrack.app.ui.components.DeviceIconBubble
import com.findtrack.app.ui.components.DeviceMapView
import com.findtrack.app.ui.components.MapPin
import com.findtrack.app.ui.components.NearbyBadge
import com.findtrack.app.ui.components.SectionCard
import com.findtrack.app.ui.components.StylizedMap
import com.findtrack.app.ui.theme.DividerColor
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.GreenSoftBg
import com.findtrack.app.ui.theme.PageBg
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.findtrack.app.ui.theme.TextPrimary
import com.findtrack.app.ui.theme.TextSecondary
import com.findtrack.app.ui.theme.TextTertiary
import com.findtrack.app.util.TimeFormat

/**
 * Finder Map — the design's full-screen map for the selected device with a
 * summary card and "last seen location" details.
 */
@Composable
fun FinderMapScreen(
    vm: FindTrackViewModel,
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onPickDevice: () -> Unit,
    contentPadding: PaddingValues
) {
    val selected by vm.selectedDevice.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val trackingAddress by vm.trackingAddress.collectAsStateWithLifecycle()
    val userLatLng by vm.userLatLng.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var zoom by remember { mutableFloatStateOf(1f) }

    val device = selected ?: devices.firstOrNull { it.lastSeen != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
    ) {
        BackTopBar(
            title = "Finder Map",
            onBack = onBack,
            subtitle = {
                Text(
                    text = "Locate your device on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        )

        if (device == null || device.lastSeen == null) {
            EmptyMapState(onPickDevice)
            return@Column
        }

        // ------------------------------------------------------ map
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val deviceFix = remember(history, device.address) {
                history.firstOrNull {
                    it.address == device.address && it.lat != null && it.lon != null
                }
            }
            DeviceMapView(
                pins = listOf(
                    MapPin(
                        address = device.address,
                        iconType = device.iconType,
                        distanceM = device.distanceM ?: 10.0,
                        selected = true,
                        rssi = device.rssi,
                        calloutTitle = device.name,
                        calloutSubtitle = "Last seen ${TimeFormat.relative(device.lastSeen)}",
                        lat = deviceFix?.lat,
                        lon = deviceFix?.lon
                    )
                ),
                modifier = Modifier.fillMaxSize(),
                zoom = 16f,
                showControls = true,
                userLocation = userLatLng,
                onPinTap = { }
            )
        }

        // ------------------------------------------------ summary card
        SectionCard(modifier = Modifier.padding(top = 12.dp)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDevice(device.address) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceIconBubble(type = device.iconType, size = 44.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (device.isNearby) {
                                NearbyBadge()
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            val distance = device.distanceM
                            Text(
                                text = (if (device.isNearby) "Nearby" else "Far") +
                                    (if (distance != null) " • ${DistanceEstimator.formatDistance(distance)}" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    if (trackingAddress == device.address) {
                        Text(
                            text = "Tracking",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GreenPrimary)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = TextTertiary
                    )
                }

                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Last seen location",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = nearestPlaceFor(vm, device.address),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = TimeFormat.absolute(device.lastSeen),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

/** Latest known place for a device from History (offline gazetteer). */
@Composable
private fun nearestPlaceFor(vm: FindTrackViewModel, address: String): String {
    val history by vm.history.collectAsStateWithLifecycle()
    val entry = history.firstOrNull { it.address == address }
    return entry?.place ?: "Waiting for a location fix…"
}

@Composable
private fun EmptyMapState(onPickDevice: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(GreenSoftBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SensorsOff,
                contentDescription = null,
                tint = GreenPrimary
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Text(
            text = "No device to show",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "Scan for devices first — then pick one\nto see it on the map.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        TextButton(onClick = onPickDevice) {
            Text(text = "Go to Devices", color = GreenPrimary)
        }
    }
}
