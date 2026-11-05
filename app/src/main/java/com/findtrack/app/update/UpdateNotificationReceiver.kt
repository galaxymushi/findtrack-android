package com.findtrack.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Two small jobs: start the APK download from the notification's Download
 * action, and turn DownloadManager's completion broadcast into a
 * "tap to install" notification.
 */
class UpdateNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNLOAD -> {
                val apkUrl = intent.getStringExtra(UpdateInstaller.EXTRA_APK_URL) ?: return
                val version = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION) ?: return
                UpdateInstaller.download(context, apkUrl, version)
                UpdateInstaller.updateChannel(context)
            }

            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val prefs = context.getSharedPreferences(UpdateInstaller.PREFS, Context.MODE_PRIVATE)
                if (id == -1L || id != prefs.getLong(UpdateInstaller.KEY_DOWNLOAD_ID, -1L)) return
                val version = prefs.getString(UpdateInstaller.KEY_DOWNLOAD_VERSION, null) ?: return

                val manager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val uri = manager.getUriForDownloadedFile(id) ?: return
                UpdateInstaller.installNotification(context, version, uri)
            }
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.findtrack.app.update.DOWNLOAD"
    }
}
