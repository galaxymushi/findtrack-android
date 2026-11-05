package com.findtrack.app.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(val label: String, val lat: Double, val lon: Double)

/**
 * Offline reverse-geocoder: resolves a captured GPS fix to the nearest known
 * landmark within a plausible radius. No network access is required, which
 * keeps the app fully offline-capable. The gazetteer is focused on Tanzania.
 */
object PlaceResolver {

    private val gazetteer = listOf(
        // Dar es Salaam
        Place("Kariakoo Market, Dar es Salaam", -6.8180, 39.2803),
        Place("Mlimani City, Dar es Salaam", -6.7730, 39.2210),
        Place("Julius Nyerere Airport, Dar es Salaam", -6.8780, 39.2026),
        Place("Coco Beach, Dar es Salaam", -6.7590, 39.2810),
        Place("Mwenge, Dar es Salaam", -6.7695, 39.2435),
        Place("Kivukoni, Dar es Salaam", -6.8160, 39.2890),
        Place("Ubungo, Dar es Salaam", -6.7830, 39.2050),
        // Other regions
        Place("Clock Tower, Arusha", -3.3720, 36.6940),
        Place("Kilimanjaro Airport", -3.4290, 37.0740),
        Place("Moshi Town", -3.3430, 37.3390),
        Place("Dodoma City Centre", -6.1730, 35.7420),
        Place("Mwanza City Centre", -2.5230, 32.9020),
        Place("Stone Town, Zanzibar", -6.1620, 39.1920),
        Place("Karume Airport, Zanzibar", -6.2220, 39.2246),
        Place("Mbeya City", -8.9090, 33.4600),
        Place("Morogoro Town", -6.8240, 37.6630),
        Place("Tanga City", -5.0700, 39.0990),
        Place("Iringa Town", -7.7700, 35.6930),
        Place("Kigoma Town", -4.8770, 29.6300),
        Place("Tabora Town", -5.0170, 32.8260),
        Place("Musoma Town", -1.5030, 33.8000),
        Place("Bukoba Town", -1.3320, 31.8120),
        Place("Songea Town", -10.6830, 35.6500),
        Place("Mtwara Town", -10.2670, 40.1830),
        Place("Lindi Town", -9.9970, 39.7140),
        Place("Sumbawanga Town", -7.9670, 31.6170),
        Place("Singida Town", -4.8130, 34.7460),
        Place("Shinyanga Town", -3.6670, 33.4170),
        Place("Geita Town", -2.8670, 32.1670),
        Place("Kahama Town", -3.8330, 32.6000),
        Place("Njombe Town", -9.3330, 34.4670),
        Place("Babati Town", -4.2170, 35.7500),
        Place("Bagamoyo", -6.4330, 38.9000),
        Place("Kibaha", -6.7670, 38.9170),
        Place("Serengeti National Park", -2.3330, 34.8330),
        Place("Ngorongoro Crater", -3.1700, 35.5800),
        Place("Mount Kilimanjaro", -3.0670, 37.3560)
    )

    /** Maximum distance (km) from the nearest landmark to use its label. */
    private const val MATCH_RADIUS_KM = 12.0

    fun resolve(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "Location unavailable"
        val nearest = gazetteer.minByOrNull { haversineKm(it.lat, it.lon, lat, lon) }
            ?: return "Captured location"
        val distance = haversineKm(nearest.lat, nearest.lon, lat, lon)
        return if (distance <= MATCH_RADIUS_KM) nearest.label else "Captured location"
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
