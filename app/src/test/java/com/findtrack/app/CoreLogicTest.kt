package com.findtrack.app

import com.findtrack.app.ble.DeviceIconType
import com.findtrack.app.ble.classifyDevice
import com.findtrack.app.util.PlaceResolver
import com.findtrack.app.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CoreLogicTest {

    // ------------------------------------------------------- classification

    @Test
    fun `device families map to the design's icons`() {
        assertEquals(DeviceIconType.SPEAKER, classifyDevice("JBL Flip 6"))
        assertEquals(DeviceIconType.SPEAKER, classifyDevice("JBL Charge 5"))
        assertEquals(DeviceIconType.PHONE, classifyDevice("Galaxy S23"))
        assertEquals(DeviceIconType.WATCH, classifyDevice("Apple Watch"))
        assertEquals(DeviceIconType.EARBUDS, classifyDevice("AirPods Pro"))
        assertEquals(DeviceIconType.LAPTOP, classifyDevice("MacBook Pro"))
        assertEquals(DeviceIconType.TV, classifyDevice("Living Room TV"))
        assertEquals(DeviceIconType.TAG, classifyDevice("Tile Mate"))
    }

    @Test
    fun `watch wins over phone keywords`() {
        assertEquals(DeviceIconType.WATCH, classifyDevice("Galaxy Watch 6"))
    }

    // ------------------------------------------------------------ time copy

    @Test
    fun `relative time matches the design's copy`() {
        val now = 1_700_000_000_000L
        assertEquals("just now", TimeFormat.relative(now - 30_000, now))
        assertEquals("2 min ago", TimeFormat.relative(now - 2 * 60_000, now))
        assertEquals("18 min ago", TimeFormat.relative(now - 18 * 60_000, now))
    }

    @Test
    fun `absolute time uses Today prefix`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 37)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatted = TimeFormat.absolute(cal.timeInMillis)
        assertTrue("expected Today prefix, got $formatted", formatted.startsWith("Today,"))
        assertTrue(formatted.contains("9:37"))
    }

    // ------------------------------------------------------ place resolver

    @Test
    fun `nearby coordinates resolve to the nearest landmark`() {
        // ~200m from Kariakoo Market
        val place = PlaceResolver.resolve(-6.8200, 39.2800)
        assertEquals("Kariakoo Market, Dar es Salaam", place)
    }

    @Test
    fun `coordinates far from landmarks are generic`() {
        val place = PlaceResolver.resolve(-33.8688, 151.2093) // Sydney
        assertEquals("Captured location", place)
    }

    @Test
    fun `null location degrades gracefully`() {
        assertEquals("Location unavailable", PlaceResolver.resolve(null, null))
    }

    // ------------------------------------------------------- misc sanity

    @Test
    fun `bearing is stable and within bounds`() {
        val a = com.findtrack.app.ui.components.stableBearing("D0:DE:00:00:00:01")
        val b = com.findtrack.app.ui.components.stableBearing("D0:DE:00:00:00:01")
        assertEquals(a, b)
        assertTrue(a in 0..359)
    }
}
