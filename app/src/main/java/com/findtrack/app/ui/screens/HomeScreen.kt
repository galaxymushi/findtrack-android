package com.findtrack.app.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findtrack.app.R
import com.findtrack.app.ble.DistanceEstimator
import com.findtrack.app.ui.DeviceListEntry
import com.findtrack.app.ui.DownloadProgress
import com.findtrack.app.ui.DownloadStatus
import com.findtrack.app.ui.FindTrackViewModel
import com.findtrack.app.ui.UpdateState
import com.findtrack.app.ui.components.DeviceIconBubble
import com.findtrack.app.ui.components.DeviceMapView
import com.findtrack.app.ui.components.MapPin
import com.findtrack.app.ui.components.NearbyBadge
import com.findtrack.app.ui.components.SectionCard
import com.findtrack.app.ui.components.SignalBars
import com.findtrack.app.ui.components.StylizedMap
import com.findtrack.app.ui.theme.CardWhite
import com.findtrack.app.ui.theme.GreenBadgeBg
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.GreenSoftBg
import com.findtrack.app.ui.theme.GreenTextAccent
import com.findtrack.app.ui.theme.PageBg
import com.findtrack.app.ui.theme.TextPrimary
import com.findtrack.app.ui.theme.TextSecondary
import com.findtrack.app.ui.theme.TextTertiary
import com.findtrack.app.util.TimeFormat
import kotlin.math.roundToInt

/**
 * Home — the design's landing screen: brand header, live scanning card,
 * map with device pins and the strongest/selected device quick card.
 */
@Composable
fun HomeScreen(
    vm: FindTrackViewModel,
    onOpenDevices: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val devices by vm.devices.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val bluetoothOn by vm.bluetoothOn.collectAsStateWithLifecycle()
    val bluetoothAvailable = vm.bluetoothAvailable
    val selectedDevice by vm.selectedDevice.collectAsStateWithLifecycle()
    val selectedAddress by vm.selectedAddress.collectAsStateWithLifecycle()
    val demoMode by vm.demoMode.collectAsStateWithLifecycle()
    val savedCount by vm.savedCount.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val downloadingVersion by vm.downloadingVersion.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val missingPermissions = vm.missingPermissions(context)

    var zoom by remember { mutableFloatStateOf(1f) }
    val userLatLng by vm.userLatLng.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    // The quick card shows the explicit selection or the strongest device.
    val featured: DeviceListEntry? = selectedDevice
        ?: devices.firstOrNull { it.lastSeen != null }

    val pins = remember(devices, selectedAddress, history, userLatLng) {
        // ----------------------------------- live devices (BLE scan)
        val livePins = devices
            .filter { !it.isDemo && it.lastSeen != null && it.distanceM != null }
            .map { entry ->
                val fix = history.firstOrNull {
                    it.address == entry.address && it.lat != null && it.lon != null
                }
                MapPin(
                    address = entry.address,
                    iconType = entry.iconType,
                    distanceM = entry.distanceM ?: 10.0,
                    selected = entry.address == selectedAddress || (selectedAddress == null && featured?.address == entry.address),
                    rssi = entry.rssi,
                    lat = fix?.lat ?: 0.0,
                    lon = fix?.lon ?: 0.0,
                    calloutTitle = entry.name,
                    calloutSubtitle = if (entry.lastSeen != null) "Last seen ${TimeFormat.relative(entry.lastSeen)}" else null
                )
            }

        // ----------------------------------- saved devices with real last-seen fix
        val historyPins = devices
            .filter { !it.isDemo && it.isSaved }
            .flatMap { entry ->
                history.firstOrNull {
                    it.address == entry.address && it.lat != null && it.lon != null
                }?.let { fix ->
                    listOf(MapPin(
                        address = entry.address,
                        iconType = entry.iconType,
                        distanceM = 0.0,
                        selected = false,
                        lat = fix.lat ?: 0.0,
                        lon = fix.lon ?: 0.0,
                        calloutTitle = entry.name,
                        calloutSubtitle = if (entry.lastSeen != null) "Last seen ${TimeFormat.relative(entry.lastSeen)}" else "Saved device"
                    ))
                } ?: emptyList()
            }

        // Merge: live pins take precedence over history-only pins
        (livePins + historyPins)
            .groupBy { it.address }
            .mapValues { it.value.first() }
            .values
            .toList()
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
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "FindTrack",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Find your devices. Stay connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }

        // -------------------------------------------- permission banner
        if (missingPermissions.isNotEmpty()) {
            SectionCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GreenBadgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bluetooth,
                            tint = GreenPrimary,
                            contentDescription = null
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Access needed",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Grant Bluetooth & location access to discover nearby devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "Grant",
                        style = MaterialTheme.typography.labelLarge,
                        color = GreenPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(GreenSoftBg)
                            .clickable { onRequestPermissions() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ------------------------------------------------ update banner
        val availableUpdate = (updateState as? UpdateState.Available)?.info
        if (availableUpdate != null) {
            UpdateBanner(
                version = availableUpdate.version,
                downloading = downloadingVersion == availableUpdate.version,
                progress = downloadProgress,
                onDownload = { vm.downloadUpdate() },
                onInstall = { vm.installDownloadedUpdate() },
                onBrowser = { vm.openDownloadInBrowser() },
                onDismiss = { vm.dismissUpdate() }
            )
        }

        // ------------------------------------------------ scanning card
        // (Shown only after access is granted — the banner above owns the CTA,
        // so the two never duplicate "Access needed".)
        if (missingPermissions.isEmpty()) {
            SectionCard(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (missingPermissions.isEmpty()) onOpenDevices() else onRequestPermissions()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GreenSoftBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        tint = GreenPrimary
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    val title = when {
                        missingPermissions.isNotEmpty() -> "Access needed"
                        !bluetoothAvailable -> "Bluetooth unavailable"
                        !bluetoothOn -> "Bluetooth is off"
                        scanning -> "Scanning for devices…"
                        else -> "Scanning paused"
                    }
                    val subtitle = when {
                        missingPermissions.isNotEmpty() -> "Tap to grant access"
                        !bluetoothAvailable -> "This device has no BLE hardware — try demo mode"
                        !bluetoothOn -> "Turn on Bluetooth to find your devices"
                        else -> {
                            val count = devices.count { it.lastSeen != null }
                            val near = devices.count { it.isNearby && it.lastSeen != null }
                            if (count == 0) "Looking for nearby devices…"
                            else "$near devices nearby • $count found"
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scanning && bluetoothOn && missingPermissions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary
                )
            }
            }
        }

        // ------------------------------------------------ quick stats
        if (missingPermissions.isEmpty()) {
            val found = devices.count { it.lastSeen != null }
            val near = devices.count { it.isNearby && it.lastSeen != null }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    icon = Icons.Outlined.Bluetooth,
                    value = "$near",
                    label = "Nearby",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Outlined.SignalCellularAlt,
                    value = "$found",
                    label = "Found",
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Outlined.Bookmark,
                    value = "$savedCount",
                    label = "Saved",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --------------------------------------------------- quick map
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            DeviceMapView(
                pins = pins,
                modifier = Modifier.fillMaxSize(),
                zoom = 16f,
                showControls = true,
                userLocation = userLatLng,
                selectedAddress = selectedAddress,
                onPinTap = { vm.select(it) }
            )
            if (demoMode) {
                Text(
                    text = "Demo mode — simulated devices",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenTextAccent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(GreenBadgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (devices.isEmpty() && missingPermissions.isEmpty() && bluetoothOn) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SignalCellularAlt,
                        contentDescription = null,
                        tint = TextTertiary
                    )
                    Text(
                        text = "No devices found yet",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Keep your devices powered and nearby",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // ------------------------------------------- featured device card
        if (featured != null) {
            val device = featured
            SectionCard(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDevice(device.address) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceIconBubble(type = device.iconType, size = 42.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (device.isNearby) NearbyBadge()
                        }
                        val rssi = device.rssi
                        val seen = device.lastSeen
                        val detail = buildList {
                            if (rssi != null) add("RSSI: $rssi dBm")
                            if (seen != null) add(TimeFormat.relative(seen))
                        }.joinToString("  •  ")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SignalBars(
                                level = rssi?.let { DistanceEstimator.signalLevel(it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = TextTertiary
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

/** Green promo card announcing a newer release, with live download progress. */
@Composable
private fun UpdateBanner(
    version: String,
    downloading: Boolean,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    val status = progress?.status
    val failed = status == DownloadStatus.FAILED
    val finished = status == DownloadStatus.SUCCESS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F4D2E), Color(0xFF1E7A46))
                )
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.update_banner_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.update_banner_dismiss),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        when {
            downloading -> {
                val current = progress
                val fraction = current?.fraction
                if (current != null && fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .height(6.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                    Text(
                        text = stringResource(
                            R.string.update_download_progress,
                            (fraction * 100).roundToInt(),
                            formatSize(current.bytes),
                            formatSize(current.total)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .height(6.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                    Text(
                        text = stringResource(R.string.update_download_starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.update_banner_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            finished -> Text(
                text = stringResource(R.string.update_banner_downloaded),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            failed -> Text(
                text = stringResource(R.string.update_banner_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            else -> Text(
                text = stringResource(R.string.update_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
        }

        if (!downloading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (failed) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    finished -> BannerAction(
                        text = stringResource(R.string.update_banner_install),
                        filled = true,
                        onClick = onInstall
                    )

                    failed -> {
                        BannerAction(
                            text = stringResource(R.string.update_banner_browser),
                            filled = false,
                            onClick = onBrowser
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BannerAction(
                            text = stringResource(R.string.update_banner_retry),
                            filled = true,
                            onClick = onDownload
                        )
                    }

                    else -> BannerAction(
                        text = stringResource(R.string.update_banner_download),
                        filled = true,
                        onClick = onDownload
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerAction(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) GreenPrimary else Color.White
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) {
        String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(
            java.util.Locale.getDefault(),
            "%d KB",
            (bytes / 1024).coerceAtLeast(1)
        )
    }
}

/** Small white stat tile used by the home summary row. */
@Composable
private fun StatPill(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
