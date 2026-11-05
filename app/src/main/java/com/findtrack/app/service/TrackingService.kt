package com.findtrack.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.findtrack.app.FindTrackApp
import com.findtrack.app.MainActivity
import com.findtrack.app.R
import com.findtrack.app.ble.BleScanner
import com.findtrack.app.ble.DistanceEstimator
import com.findtrack.app.ble.TrackStatus
import com.findtrack.app.ble.TrackedDevice
import com.findtrack.app.ble.TrackingController
import com.findtrack.app.ble.TrackingState
import com.findtrack.app.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps tracking a device even when the app is in the
 * background: it owns the BLE scan session (reference counted), updates the
 * persistent notification with the live distance, emits proximity feedback
 * (beep/vibrate) and records the final "last seen" history entry when the
 * device goes out of range.
 */
class TrackingService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "com.findtrack.app.action.STOP"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_NAME = "extra_name"
        const val NOTIFICATION_ID = 4201
        const val CHANNEL_ID = "tracking"
        private const val LOST_TIMEOUT_MS = 15_000L
        private const val NOTIF_THROTTLE_MS = 3_000L
        private const val BEEP_MIN_DELTA_M = 1.5
        private const val BEEP_MIN_INTERVAL_MS = 1_200L

        fun start(context: Context, address: String, name: String) {
            val intent = Intent(context, TrackingService::class.java)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_NAME, name)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackingService::class.java).setAction(ACTION_STOP)
            // startService is safe while the service is already foreground; if it
            // is not running (or the OS blocks the start), stop it outright.
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                context.stopService(Intent(context, TrackingService::class.java))
            }
        }
    }

    private val scanner: BleScanner get() = FindTrackApp.graph.scanner
    private val locationRepo get() = FindTrackApp.graph.location
    private val historyDao get() = FindTrackApp.graph.database.historyDao()
    private val prefs get() = FindTrackApp.graph.prefs

    private val handler = Handler(Looper.getMainLooper())
    private var address: String? = null
    private var name: String? = null
    private var maxRssi: Int? = null
    private var lastBeepAt = 0L
    private var lastBeepDistance = Double.MAX_VALUE
    private var lastNotifAt = 0L
    private var wasLost = false
    private var lostHistoryWritten = false
    private var toneGenerator: ToneGenerator? = null
    private var soundOnCache: Boolean = true

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Keep a cached copy of the sound preference (handlers are not suspend).
        lifecycleScope.launch {
            runCatching { prefs.soundEnabled.collect { soundOnCache = it } }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateSession()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            finishSession()
            return START_NOT_STICKY
        }
        val addr = intent?.getStringExtra(EXTRA_ADDRESS) ?: run {
            finishSession(); return START_NOT_STICKY
        }
        val devName = intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.notif_device_generic)

        // Restart case: same device already tracked, keep session state.
        if (address != addr) {
            address = addr
            name = devName
            maxRssi = null
            wasLost = false
            lostHistoryWritten = false
        }

        scanner.acquire(this)
        startForegroundCompat(buildNotification(getString(R.string.notif_searching, devName)))
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_REDELIVER_INTENT
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateSession() {
        val addr = address ?: return
        val devName = name ?: return
        val device = scanner.device(addr)
        val now = System.currentTimeMillis()
        val soundOn = soundOnCache

        val state: TrackingState
        if (device == null || now - device.lastSeen > LOST_TIMEOUT_MS) {
            val last = TrackingController.state.value
            state = TrackingState(
                address = addr,
                name = devName,
                rssi = last?.rssi,
                distanceM = last?.distanceM,
                lastSeen = last?.lastSeen ?: now,
                status = TrackStatus.LOST,
                soundOn = soundOn,
                maxRssi = maxRssi
            )
            if (!wasLost) {
                wasLost = true
                vibrateOnce()
                writeLostHistory(addr, devName, device)
            }
        } else {
            val previousMax = maxRssi ?: device.rssi
            maxRssi = maxOf(previousMax, device.rssi)
            val status = when {
                device.rssi >= previousMax -> TrackStatus.STRONGER
                device.rssi <= previousMax - 8 -> TrackStatus.WEAKER
                else -> TrackStatus.STEADY
            }
            state = TrackingState(
                address = addr,
                name = devName,
                rssi = device.rssi,
                distanceM = device.distanceM,
                lastSeen = device.lastSeen,
                status = status,
                soundOn = soundOn,
                maxRssi = maxRssi
            )
            if (wasLost) {
                wasLost = false
                lostHistoryWritten = false
                vibrateOnce()
            }
            maybeBeep(device)
        }
        TrackingController.update(state)

        if (now - lastNotifAt > NOTIF_THROTTLE_MS) {
            lastNotifAt = now
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(notificationText(state)))
        }
    }

    private fun notificationText(state: TrackingState): String = when (state.status) {
        TrackStatus.LOST -> getString(R.string.notif_lost)
        TrackStatus.SEARCHING -> getString(R.string.notif_searching, state.name)
        else -> {
            val dist = state.distanceM?.let { DistanceEstimator.formatDistance(it) } ?: "…"
            getString(R.string.notif_device_distance, state.name, dist)
        }
    }

    private fun writeLostHistory(addr: String, devName: String, lastDevice: TrackedDevice?) {
        if (lostHistoryWritten) return
        lostHistoryWritten = true
        val device = lastDevice ?: TrackedDevice(
            address = addr, name = devName, rssi = -100,
            lastSeen = System.currentTimeMillis()
        )
        locationRepo.recordSighting(historyDao, device, minIntervalMs = 5 * 60 * 1000L)
    }

    private fun maybeBeep(device: TrackedDevice) {
        val now = System.currentTimeMillis()
        val dist = device.distanceM
        val improved = dist + BEEP_MIN_DELTA_M < lastBeepDistance
        if (!improved || now - lastBeepAt < BEEP_MIN_INTERVAL_MS) return
        lastBeepAt = now
        lastBeepDistance = dist
        try {
            val gen = toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                .also { toneGenerator = it }
            // Pitch rises as the device gets closer.
            val tone = if (dist < 3) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
            gen.startTone(tone, 120)
        } catch (_: Exception) {
        }
        vibrateOnce()
    }

    @Suppress("DEPRECATION")
    private fun vibrateOnce() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bluetooth)
            .setContentTitle(
                getString(R.string.notif_tracking_title, name ?: getString(R.string.notif_device_generic))
            )
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .addAction(
                R.drawable.ic_stat_stop,
                getString(R.string.notif_stop_action),
                stopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun finishSession() {
        handler.removeCallbacks(tickRunnable)
        TrackingController.update(null)
        scanner.release(this)
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        scanner.release(this)
        TrackingController.update(null)
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        super.onDestroy()
    }

    // Unused helpers kept for clarity of lifecycle contract
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }
}
