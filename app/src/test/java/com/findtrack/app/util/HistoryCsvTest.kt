package com.findtrack.app.util

import com.findtrack.app.data.HistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HistoryCsvTest {

    private val utc = ZoneId.of("UTC")

    private fun entry(
        name: String = "JBL Flip 6",
        address: String = "AA:BB:CC:DD:EE:FF",
        rssi: Int = -68,
        place: String = "Mlimani City, Dar es Salaam",
        lat: Double? = -6.773,
        lon: Double? = 39.221,
        timestamp: Long = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli(),
        isDemo: Boolean = false
    ) = HistoryEntity(
        address = address,
        name = name,
        rssi = rssi,
        lat = lat,
        lon = lon,
        place = place,
        timestamp = timestamp,
        isDemo = isDemo
    )

    @Test
    fun `header is written once`() {
        val csv = HistoryCsv.build(emptyList(), utc)
        assertEquals("Device,Address,RSSI,Place,Latitude,Longitude,Time,Demo\r\n", csv)
    }

    @Test
    fun `row contains all fields in order`() {
        val csv = HistoryCsv.build(listOf(entry()), utc)
        val row = csv.substringAfter("\r\n")
        assertEquals(
            "JBL Flip 6,AA:BB:CC:DD:EE:FF,-68,\"Mlimani City, Dar es Salaam\"," +
                "-6.773000,39.221000,2026-01-01 12:00:00,FALSE\r\n",
            row
        )
    }

    @Test
    fun `quotes are doubled and newlines are quoted`() {
        val csv = HistoryCsv.build(
            listOf(entry(name = "My \"speaker\"", place = "Line one\nLine two")),
            utc
        )
        val row = csv.substringAfter("\r\n")
        assertTrue(row.startsWith("\"My \"\"speaker\"\"\",AA:BB:CC:DD:EE:FF"))
        assertTrue(row.contains(",\"Line one\nLine two\","))
    }

    @Test
    fun `plain values are not quoted`() {
        assertEquals("speaker", HistoryCsv.escape("speaker"))
        assertEquals("", HistoryCsv.escape(null))
        assertEquals("\"a,b\"", HistoryCsv.escape("a,b"))
    }

    @Test
    fun `missing coordinates stay empty`() {
        val row = HistoryCsv.build(listOf(entry(lat = null, lon = null)), utc)
            .substringAfter("\r\n")
        assertTrue(row.contains("-68,\"Mlimani City, Dar es Salaam\",,,2026-01-01 12:00:00,FALSE"))
    }

    @Test
    fun `demo flag is exported`() {
        val row = HistoryCsv.build(listOf(entry(isDemo = true)), utc).substringAfter("\r\n")
        assertTrue(row.endsWith(",TRUE\r\n"))
    }

    @Test
    fun `file name has timestamp`() {
        val now = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
        assertEquals("findtrack-history-20260101-1200.csv", HistoryCsv.fileName(now, utc))
    }
}
