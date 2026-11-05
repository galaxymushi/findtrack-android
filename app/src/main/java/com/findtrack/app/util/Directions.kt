package com.findtrack.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens turn-by-turn directions to a device's last known coordinates. */
object Directions {

    fun open(context: Context, lat: Double, lon: Double, label: String? = null) {
        val query = if (label.isNullOrBlank()) {
            "$lat,$lon"
        } else {
            "$lat,$lon(${Uri.encode(label)})"
        }
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$query"))
        if (context !is Activity) geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(geo)
            return
        } catch (_: ActivityNotFoundException) {
            // No maps app installed — fall back to Google Maps in a browser.
        }

        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
        )
        if (context !is Activity) web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
        }
    }
}
