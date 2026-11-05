package com.findtrack.app.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.findtrack.app.MainActivity
import com.findtrack.app.R

/**
 * Posts the "update available" notification and downloads the new APK.
 * Downloading uses the system DownloadManager, so progress and the final
 * install prompt keep working even if the app process is killed.
 */
object UpdateInstaller {

    const val CHANNEL_ID = "updates"
    const val PREFS = "updates"
    const val KEY_DOWNLOAD_ID = "download_id"
    const val KEY_DOWNLOAD_VERSION = "download_version"
    const val EXTRA_APK_URL = "apk_url"
    const val EXTRA_VERSION = "version"

    private const val AVAILABLE_NOTIFICATION_ID = 5001
    private const val INSTALL_NOTIFICATION_ID = 5002
    private const val REQUEST_OPEN_APP = 5101
    private const val REQUEST_DOWNLOAD_ACTION = 5102
    private const val REQUEST_INSTALL = 5103

    fun updateChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.update_channel_desc)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** True when the notification was actually posted (permission granted). */
    fun notifyAvailable(context: Context, info: UpdateInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(context.getString(R.string.update_notif_title, info.version))
            .setContentText(context.getString(R.string.update_notif_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    info.notes.trim().takeIf { it.isNotEmpty() }
                        ?: context.getString(R.string.update_notif_text)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)

        val apkUrl = info.apkUrl
        if (apkUrl != null) {
            builder.addAction(
                0,
                context.getString(R.string.update_download),
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_DOWNLOAD_ACTION,
                    Intent(context, UpdateNotificationReceiver::class.java).apply {
                        action = UpdateNotificationReceiver.ACTION_DOWNLOAD
                        putExtra(EXTRA_APK_URL, apkUrl)
                        putExtra(EXTRA_VERSION, info.version)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            val releaseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
            val release = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP + 1,
                releaseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(release)
        }

        return try {
            NotificationManagerCompat.from(context)
                .notify(AVAILABLE_NOTIFICATION_ID, builder.build())
            true
        } catch (error: SecurityException) {
            false
        }
    }

    /** Enqueues the APK download; the completion receiver posts the install prompt. */
    fun download(context: Context, apkUrl: String, version: String): Boolean {
        val fileName = "FindTrack-$version.apk"
        // Re-downloading the same version should work even if a previous
        // attempt left a partial file at the destination.
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(fileName)
            ?.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(context.getString(R.string.update_download_title, version))
            .setDescription(context.getString(R.string.update_download_desc))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return try {
            val manager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_DOWNLOAD_ID, id)
                .putString(KEY_DOWNLOAD_VERSION, version)
                .apply()
            true
        } catch (error: Exception) {
            false
        }
    }

    fun installNotification(context: Context, version: String, apkUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val install = PendingIntent.getActivity(
            context,
            REQUEST_INSTALL,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(context.getString(R.string.update_install_title, version))
            .setContentText(context.getString(R.string.update_install_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(install)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(INSTALL_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            // Notifications are blocked; the DownloadManager entry still exists.
        }
    }

    fun openReleasePage(context: Context, url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            // No browser installed; nothing else we can do.
        }
    }

    const val APK_MIME = "application/vnd.android.package-archive"
}
