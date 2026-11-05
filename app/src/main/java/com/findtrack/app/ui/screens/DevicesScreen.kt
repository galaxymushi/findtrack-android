package com.findtrack.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findtrack.app.ble.DistanceEstimator
import com.findtrack.app.ui.DeviceListEntry
import com.findtrack.app.ui.FindTrackViewModel
import com.findtrack.app.ui.components.DeviceIconBubble
import com.findtrack.app.ui.components.FarBadge
import com.findtrack.app.ui.components.NearbyBadge
import com.findtrack.app.ui.components.SectionCard
import com.findtrack.app.ui.components.SignalBars
import com.findtrack.app.ui.theme.CardWhite
import com.findtrack.app.ui.theme.DividerColor
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.GreenSoftBg
import com.findtrack.app.ui.theme.GreenTextAccent
import com.findtrack.app.ui.theme.PageBg
import com.findtrack.app.ui.theme.TextPrimary
import com.findtrack.app.ui.theme.TextSecondary
import com.findtrack.app.ui.theme.TextTertiary
import com.findtrack.app.util.TimeFormat

private enum class DeviceFilter(val label: String) {
    ALL("All"),
    NEARBY("Nearby"),
    SAVED("Saved")
}

private enum class DeviceSort(val label: String) {
    STRONGEST("Strongest signal"),
    RECENT("Most recent"),
    NAME("Name A–Z")
}

/**
 * Devices — searchable, sortable list of live and saved devices with signal
 * meters, distance, and a one-tap bookmark.
 */
@Composable
fun DevicesScreen(
    vm: FindTrackViewModel,
    onOpenDevice: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(DeviceFilter.ALL) }
    var sort by remember { mutableStateOf(DeviceSort.STRONGEST) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val live = devices.filter { it.lastSeen != null }
    val counts = mapOf(
        DeviceFilter.ALL to live.size,
        DeviceFilter.NEARBY to live.count { it.isNearby },
        DeviceFilter.SAVED to devices.count { it.isSaved }
    )
    val base = when (filter) {
        DeviceFilter.ALL -> live
        DeviceFilter.NEARBY -> live.filter { it.isNearby }
        DeviceFilter.SAVED -> devices.filter { it.isSaved }
    }
    val matched = base.filter {
        query.isBlank() ||
            it.name.contains(query, ignoreCase = true) ||
            it.address.contains(query, ignoreCase = true)
    }
    val shown = when (sort) {
        DeviceSort.STRONGEST -> matched.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
        DeviceSort.RECENT -> matched.sortedByDescending { it.lastSeen ?: 0L }
        DeviceSort.NAME -> matched.sortedBy { it.name.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
    ) {
        // ------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Nearby Bluetooth devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            IconButton(onClick = { vm.refresh() }) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary,
                    modifier = Modifier.rotating(scanning)
                )
            }
        }

        // ------------------------------------------------------- search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            placeholder = {
                Text(
                    text = "Search devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = DividerColor,
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
                cursorColor = GreenPrimary
            )
        )

        // ------------------------------------------------ filter + sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceFilter.entries.forEach { f ->
                    val selected = f == filter
                    val text = when (f) {
                        DeviceFilter.ALL -> "All (${counts[f]})"
                        DeviceFilter.NEARBY -> "Nearby (${counts[f]})"
                        DeviceFilter.SAVED -> "Saved (${counts[f]})"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) GreenPrimary else GreenSoftBg)
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else GreenTextAccent
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "Sort devices",
                        tint = TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    DeviceSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (option == sort) GreenPrimary else TextPrimary
                                )
                            },
                            onClick = {
                                sort = option
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // --------------------------------------------------- device list
        if (shown.isEmpty()) {
            EmptyDevicesState(filter = filter, query = query)
        } else {
            // Individual white cards per device — matching the approved design.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    top = 2.dp,
                    bottom = 12.dp
                )
            ) {
                items(shown, key = { it.address }) { device ->
                    SectionCard {
                        DeviceRow(
                            device = device,
                            onClick = { onOpenDevice(device.address) },
                            onToggleSaved = { vm.toggleSaved(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceListEntry,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceIconBubble(type = device.iconType, size = 46.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (device.lastSeen != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (device.isNearby) NearbyBadge() else FarBadge()
                }
            }
            if (device.lastSeen != null) {
                val rssi = device.rssi ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    SignalBars(
                        level = device.rssi?.let { DistanceEstimator.signalLevel(it) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RSSI: $rssi dBm  •  ${TimeFormat.relative(device.lastSeen)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                device.distanceM?.let { distance ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "  ${DistanceEstimator.formatDistance(distance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Text(
                    text = "Saved • not seen recently",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        IconButton(onClick = onToggleSaved) {
            Icon(
                imageVector = if (device.isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (device.isSaved) "Remove from saved" else "Save device",
                tint = if (device.isSaved) GreenPrimary else TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

@Composable
private fun EmptyDevicesState(filter: DeviceFilter, query: String) {
    val searching = query.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(GreenSoftBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (searching) {
                    Icons.Outlined.Search
                } else when (filter) {
                    DeviceFilter.ALL -> Icons.Outlined.SensorsOff
                    DeviceFilter.NEARBY -> Icons.Outlined.Place
                    DeviceFilter.SAVED -> Icons.Outlined.BookmarkBorder
                },
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Text(
            text = if (searching) {
                "No devices match \"$query\""
            } else when (filter) {
                DeviceFilter.ALL -> "No devices found"
                DeviceFilter.NEARBY -> "No devices nearby"
                DeviceFilter.SAVED -> "No saved devices yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = if (searching) {
                "Try a different name, or clear the search."
            } else when (filter) {
                DeviceFilter.ALL -> "Make sure your devices are powered on and Bluetooth is enabled."
                DeviceFilter.NEARBY -> "Move closer to a device or keep scanning."
                DeviceFilter.SAVED -> "Open a device or tap the bookmark to save it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** Gentle continuous rotation for the refresh icon while scanning. */
@Composable
private fun Modifier.rotating(active: Boolean): Modifier {
    if (!active) return this
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rotate")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "rotateAngle"
    )
    return this.graphicsLayer { rotationZ = angle }
}
