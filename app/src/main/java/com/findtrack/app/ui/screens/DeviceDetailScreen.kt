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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findtrack.app.R
import com.findtrack.app.ble.DistanceEstimator
import com.findtrack.app.ui.FindTrackViewModel
import com.findtrack.app.ui.components.BackTopBar
import com.findtrack.app.ui.components.DeviceMapView
import com.findtrack.app.ui.components.MapPin
import com.findtrack.app.ui.components.NearbyBadge
import com.findtrack.app.ui.components.SectionCard
import com.findtrack.app.ui.components.SignalBars
import com.findtrack.app.ui.components.SignalSparkline
import com.findtrack.app.ui.components.StylizedMap
import com.findtrack.app.ui.components.signalColor
import com.findtrack.app.ui.theme.CardWhite
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.GreenSoftBg
import com.findtrack.app.ui.theme.GreenTextAccent
import com.findtrack.app.ui.theme.PageBg
import com.findtrack.app.ui.theme.TextPrimary
import com.findtrack.app.ui.theme.TextSecondary
import com.findtrack.app.ui.theme.TextTertiary
import com.findtrack.app.util.Directions
import com.findtrack.app.util.TimeFormat

/**
 * Device detail — map-centric screen for one device with its last known
 * location and the primary "Start Tracking" action.
 */
@Composable
fun DeviceDetailScreen(
    vm: FindTrackViewModel,
    address: String,
    onBack: () -> Unit,
    onStartTracking: () -> Unit,
    contentPadding: PaddingValues
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val device = remember(devices, address) { devices.firstOrNull { it.address == address } }
    val trackingAddress by vm.trackingAddress.collectAsStateWithLifecycle()
    val isTracking = trackingAddress == address
    val userLatLng by vm.userLatLng.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val signalHistory by vm.signalHistory.collectAsStateWithLifecycle()
    val points = signalHistory[address].orEmpty()
    val context = LocalContext.current

    val lastFix = remember(history, address) {
        history.firstOrNull {
            it.address == address && it.lat != null && it.lon != null
        }
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
    ) {
        BackTopBar(
            title = device?.name ?: "Device",
            onBack = onBack,
            actions = {
                if (device != null && device.isSaved) {
                    IconButton(onClick = {
                        renameValue = device.customName ?: device.name
                        renameOpen = true
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.device_rename),
                            tint = TextSecondary
                        )
                    }
                }
                if (device != null) {
                    IconButton(onClick = { vm.toggleSaved(device) }) {
                        Icon(
                            imageVector = if (device.isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (device.isSaved) "Remove from saved" else "Save device",
                            tint = if (device.isSaved) GreenPrimary else TextSecondary
                        )
                    }
                }
            }
        )

        // Status line: badge + last seen + RSSI (design copy)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (device != null && device.lastSeen != null) {
                if (device.isNearby) NearbyBadge()
                Spacer(modifier = Modifier.width(10.dp))
                val lastSeen = device.lastSeen
                val rssi = device.rssi
                Text(
                    text = "Last seen ${TimeFormat.relative(lastSeen)}" +
                        (if (rssi != null) "  •  RSSI: $rssi dBm" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = "Not seen recently",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        }

        // -------------------------------------------------- device map
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (device != null && device.lastSeen != null) {
                DeviceMapView(
                    pins = listOf(
                        MapPin(
                            address = device.address,
                            iconType = device.iconType,
                            distanceM = device.distanceM ?: 10.0,
                            selected = true,
                            rssi = device.rssi,
                            lat = lastFix?.lat,
                            lon = lastFix?.lon
                        )
                    ),
                    modifier = Modifier.fillMaxSize(),
                    zoom = 16f,
                    showControls = true,
                    showLegend = false,
                    userLocation = userLatLng,
                    onPinTap = { }
                )
                // Floating "Last known location" card (bottom-left)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .shadowCard()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardWhite)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = "Last known location",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = TimeFormat.relative(device.lastSeen),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                if (lastFix != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(48.dp)
                            .shadowCard()
                            .clip(CircleShape)
                            .background(CardWhite)
                            .clickable {
                                Directions.open(
                                    context,
                                    lastFix.lat!!,
                                    lastFix.lon!!,
                                    device.name
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Directions,
                            contentDescription = stringResource(R.string.device_directions),
                            tint = GreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
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
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = GreenPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No location yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "This device hasn't been seen.\nKeep scanning — it will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ---------------------------------------------- distance summary
        if (device != null && device.distanceM != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DistanceEstimator.formatDistance(device.distanceM),
                    style = MaterialTheme.typography.titleMedium,
                    color = GreenTextAccent
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "estimated distance • signal " +
                        (device.rssi?.let { DistanceEstimator.signalLevel(it).label } ?: "unknown"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // ---------------------------------------------- signal chart
        if (device != null && device.lastSeen != null) {
            SectionCard(modifier = Modifier.padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Signal strength",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = if (points.size < 2) {
                                    "Collecting readings…"
                                } else {
                                    "${points.size} readings this session"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        SignalBars(level = device.rssi?.let { DistanceEstimator.signalLevel(it) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device.rssi?.let { "$it dBm" } ?: "—",
                            style = MaterialTheme.typography.labelLarge,
                            color = signalColor(device.rssi)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (points.size >= 2) {
                        SignalSparkline(
                            points = points,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Move around to see how the signal changes",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------- primary CTA
        Button(
            onClick = onStartTracking,
            enabled = device != null && !isTracking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp)
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Icon(
                imageVector = Icons.Outlined.Navigation,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTracking) "Tracking active" else "Start Tracking",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (renameOpen && device != null) {
            AlertDialog(
                onDismissRequest = { renameOpen = false },
                title = { Text(stringResource(R.string.device_rename_title)) },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it.take(60) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.device_rename_label)) },
                        supportingText = { Text(stringResource(R.string.device_rename_hint)) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.renameDevice(device.address, renameValue)
                        renameOpen = false
                    }) {
                        Text(stringResource(R.string.device_rename_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameOpen = false }) {
                        Text(stringResource(R.string.device_rename_cancel))
                    }
                }
            )
        }
    }
}

private fun Modifier.shadowCard(): Modifier =
    this.shadow(4.dp, RoundedCornerShape(16.dp))
