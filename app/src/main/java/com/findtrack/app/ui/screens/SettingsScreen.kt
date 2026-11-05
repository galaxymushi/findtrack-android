package com.findtrack.app.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhonelinkRing
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findtrack.app.BuildConfig
import com.findtrack.app.R
import com.findtrack.app.ui.FindTrackViewModel
import com.findtrack.app.ui.UpdateState
import com.findtrack.app.ui.components.BackTopBar
import com.findtrack.app.ui.components.SectionCard
import com.findtrack.app.ui.theme.CardWhite
import com.findtrack.app.ui.theme.DangerRed
import com.findtrack.app.ui.theme.DividerColor
import com.findtrack.app.ui.theme.GreenBadgeBg
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.GreenSoftBg
import com.findtrack.app.ui.theme.GreenTextAccent
import com.findtrack.app.ui.theme.PageBg
import com.findtrack.app.ui.theme.TextPrimary
import com.findtrack.app.ui.theme.TextSecondary
import com.findtrack.app.ui.theme.TextTertiary

/**
 * Settings — permissions status, simulation (demo) mode, sound feedback,
 * data management and about information. Styled consistently with the design.
 */
@Composable
fun SettingsScreen(
    vm: FindTrackViewModel,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val demoMode by vm.demoMode.collectAsStateWithLifecycle()
    val soundEnabled by vm.soundEnabled.collectAsStateWithLifecycle()
    val bluetoothOn by vm.bluetoothOn.collectAsStateWithLifecycle()
    val bluetoothAvailable = vm.bluetoothAvailable
    val historyCount by vm.historyCount.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val downloadingVersion by vm.downloadingVersion.collectAsStateWithLifecycle()

    val missing = vm.missingPermissions(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BackTopBar(title = "Settings", onBack = onBack)
        Spacer(modifier = Modifier.height(4.dp))

        // -------------------------------------------------- permissions
        SectionLabel("Permissions")
        SectionCard {
            Column {
                PermissionRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Bluetooth scanning",
                    subtitle = when {
                        missing.any { it.contains("BLUETOOTH") } -> "Required to find nearby devices"
                        else -> "Granted — scanning works"
                    },
                    granted = missing.none { it.contains("BLUETOOTH") },
                    onClick = if (missing.any { it.contains("BLUETOOTH") }) onRequestPermissions else null
                )
                SettingsDivider()
                PermissionRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Location (last seen places)",
                    subtitle = when {
                        !vm.hasLocationPermission() -> "Used to remember where devices were seen"
                        else -> "Granted — history saves places"
                    },
                    granted = vm.hasLocationPermission(),
                    onClick = if (!vm.hasLocationPermission()) onRequestPermissions else null
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notifGranted = Manifest.permission.POST_NOTIFICATIONS !in missing
                    SettingsDivider()
                    PermissionRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Notifications",
                        subtitle = if (notifGranted) {
                            "Granted — tracking alerts enabled"
                        } else {
                            "Shows the ongoing tracking notification"
                        },
                        granted = notifGranted,
                        onClick = if (notifGranted) null else onRequestPermissions
                    )
                }
            }
        }

        // ------------------------------------------------- connectivity
        SectionLabel("Bluetooth")
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBubble(Icons.Outlined.Bluetooth)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = if (!bluetoothAvailable) "Bluetooth unavailable" else if (bluetoothOn) "Bluetooth is on" else "Bluetooth is off",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = if (!bluetoothAvailable) "No BLE hardware detected on this device" else "Toggle Bluetooth in system settings to scan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // ---------------------------------------------------- experience
        SectionLabel("Experience")
        SectionCard {
            Column {
                SwitchRow(
                    icon = Icons.Outlined.Sensors,
                    title = "Demo mode (simulated devices)",
                    subtitle = "Review the full app with sample devices — no Bluetooth hardware needed. Clearly labeled everywhere it's used.",
                    checked = demoMode,
                    onCheckedChange = { vm.setDemoMode(it) }
                )
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Outlined.PhonelinkRing,
                    title = "Proximity sounds & vibration",
                    subtitle = "Beep faster as you get closer while tracking",
                    checked = soundEnabled,
                    onCheckedChange = { vm.setSoundEnabled(it) }
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.showOnboardingAgain() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBubble(Icons.Outlined.PlayCircle)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_show_welcome),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_show_welcome_sub),
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

        // --------------------------------------------------------- data
        SectionLabel("Data")
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (historyCount > 0) vm.clearHistory() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBubble(
                    icon = Icons.Outlined.DeleteSweep,
                    tint = if (historyCount > 0) DangerRed else TextTertiary
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Clear history",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (historyCount > 0) TextPrimary else TextTertiary
                    )
                    Text(
                        text = "$historyCount location entries stored on this phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // ------------------------------------------------------- about
        SectionLabel("About")
        SectionCard {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SettingsIconBubble(Icons.Outlined.Info)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "FindTrack v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Find your devices. Stay connected.\n\n" +
                                "• Everything is stored on this phone — no accounts, no cloud.\n" +
                                "• Distance is estimated from Bluetooth signal strength (RSSI).\n" +
                                "• Map positions are distance-accurate; bearings are illustrative.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                SettingsDivider()

                val available = (updateState as? UpdateState.Available)?.info
                val downloading = downloadingVersion
                val checking = updateState is UpdateState.Checking
                val subtitle = when {
                    downloading != null ->
                        stringResource(R.string.settings_update_downloading, downloading)
                    available != null ->
                        stringResource(R.string.settings_update_available, available.version)
                    checking -> stringResource(R.string.settings_update_checking)
                    updateState is UpdateState.UpToDate ->
                        stringResource(R.string.settings_update_latest, BuildConfig.VERSION_NAME)
                    updateState is UpdateState.Failed ->
                        stringResource(R.string.settings_update_failed)
                    else -> stringResource(R.string.settings_update_idle)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !checking && downloading == null) {
                            if (available != null) vm.downloadUpdate() else vm.checkForUpdates()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBubble(
                        icon = Icons.Outlined.Download,
                        tint = if (available != null) GreenPrimary else TextTertiary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_check_updates),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    if (available != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GreenBadgeBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_banner_download),
                                style = MaterialTheme.typography.labelSmall,
                                color = GreenTextAccent
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = TextTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsIconBubble(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = GreenPrimary) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(GreenSoftBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBubble(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (granted) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Granted",
                tint = GreenPrimary
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GreenBadgeBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Fix",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenTextAccent
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBubble(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = GreenPrimary,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = DividerColor,
                uncheckedThumbColor = CardWhite
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(DividerColor)
    )
}
