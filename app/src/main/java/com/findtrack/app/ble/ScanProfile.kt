package com.findtrack.app.ble

import android.bluetooth.le.ScanSettings

/**
 * Trade-off between how often devices are discovered and battery use.
 * The scanner alternates [activeMs] of scanning with [idleMs] of rest.
 */
enum class ScanProfile(
    val activeMs: Long,
    val idleMs: Long,
    val mode: Int
) {
    FAST(6_000L, 1_000L, ScanSettings.SCAN_MODE_LOW_LATENCY),
    BALANCED(5_000L, 2_000L, ScanSettings.SCAN_MODE_LOW_LATENCY),
    SAVER(3_000L, 6_000L, ScanSettings.SCAN_MODE_LOW_POWER);

    companion object {
        fun fromKey(key: String?): ScanProfile =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: BALANCED
    }
}
