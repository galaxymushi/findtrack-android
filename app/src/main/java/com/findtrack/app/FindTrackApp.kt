package com.findtrack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.findtrack.app.ble.BleScanner
import com.findtrack.app.data.AppDatabase
import com.findtrack.app.data.PrefsRepository
import com.findtrack.app.location.LocationRepository

/**
 * Tiny hand-rolled dependency graph. The app has a deliberately small number
 * of singletons; wiring them here keeps them process-wide, lazy and testable
 * without pulling in a DI framework.
 */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val scanner: BleScanner = BleScanner.get(appContext)
    val prefs: PrefsRepository = PrefsRepository(appContext)
    val location: LocationRepository = LocationRepository(appContext)
    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
}

class FindTrackApp : Application() {

    companion object {
        @Volatile
        private var graphInstance: AppGraph? = null

        val graph: AppGraph
            get() = graphInstance
                ?: throw IllegalStateException("FindTrackApp not initialized")
    }

    override fun onCreate() {
        super.onCreate()
        graphInstance = AppGraph(this)
        createTrackingChannel()
        com.findtrack.app.update.UpdateInstaller.updateChannel(this)
    }

    private fun createTrackingChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            com.findtrack.app.service.TrackingService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
