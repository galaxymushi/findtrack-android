package com.findtrack.app.util

import com.findtrack.app.data.HistoryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds an RFC 4180 CSV of location history for sharing or spreadsheet import. */
object HistoryCsv {

    private const val HEADER = "Device,Address,RSSI,Place,Latitude,Longitude,Time,Demo"

    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun build(entries: List<HistoryEntity>, zone: ZoneId = ZoneId.systemDefault()): String {
        val out = StringBuilder(HEADER).append("\r\n")
        entries.forEach { entry ->
            out.append(escape(entry.name)).append(',')
                .append(escape(entry.address)).append(',')
                .append(entry.rssi).append(',')
                .append(escape(entry.place)).append(',')
                .append(formatCoordinate(entry.lat)).append(',')
                .append(formatCoordinate(entry.lon)).append(',')
                .append(timeFormat.format(Instant.ofEpochMilli(entry.timestamp).atZone(zone))).append(',')
                .append(if (entry.isDemo) "TRUE" else "FALSE")
                .append("\r\n")
        }
        return out.toString()
    }

    fun fileName(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US)
            .format(Instant.ofEpochMilli(now).atZone(zone))
        return "findtrack-history-$stamp.csv"
    }

    /** Quotes a field only when it contains a comma, quote or line break. */
    internal fun escape(value: String?): String {
        val text = value.orEmpty()
        val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    private fun formatCoordinate(value: Double?): String =
        value?.let { String.format(Locale.US, "%.6f", it) } ?: ""
}
