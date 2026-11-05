package com.findtrack.app.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.findtrack.app.data.PrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Process-wide BLE scanning engine.
 *
 * - Reference counted: the UI and the foreground tracking service share one scan loop.
 * - Periodic restart: some Android stacks stall long-running scans, so the scanner
 *   restarts every few seconds (5 s active / 2 s rest keeps battery use reasonable).
 * - Signal smoothing: RSSI is exponentially smoothed so meters don't flicker.
 * - Demo mode: a clearly-labeled simulator that emits the design's sample devices so
 *   every screen can be exercised without nearby BLE hardware.
 */
class BleScanner private constructor(context: Context) {

    companion object {
        private const val STALE_TIMEOUT_MS = 60_000L
        private const val MAX_DEVICES = 30
        private const val DEMO_PREFIX = "demo:"
        private const val RSSI_SMOOTHING = 0.45f

        @Volatile
        private var instance: BleScanner? = null

        fun get(context: Context): BleScanner =
            instance ?: synchronized(this) {
                instance ?: BleScanner(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = PrefsRepository(appContext)

    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _devices = MutableStateFlow<Map<String, TrackedDevice>>(emptyMap())
    val devices: StateFlow<List<TrackedDevice>> = MutableStateFlow<List<TrackedDevice>>(emptyList())
        .also { out ->
            scope.launch {
                _devices.collect { map ->
                    out.value = map.values.sortedByDescending { it.rssi }
                }
            }
        }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _bluetoothOn = MutableStateFlow(adapter?.isEnabled == true)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    val bluetoothAvailable: Boolean get() = adapter != null

    private val lock = Any()
    private val holders = mutableSetOf<Any>()
    private var scanJob: Job? = null
    private var demoJob: Job? = null

    /** Discovery/battery trade-off; change via [setProfile]. */
    @Volatile
    var profile: ScanProfile = ScanProfile.BALANCED
        private set

    /** Applies a new scan profile and restarts the loop if it is running. */
    fun setProfile(newProfile: ScanProfile) {
        if (profile == newProfile) return
        profile = newProfile
        synchronized(lock) {
            if (holders.isNotEmpty()) {
                scanJob?.cancel()
                stopLeScan()
                startScanLoop()
            }
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> _bluetoothOn.value = true
                BluetoothAdapter.STATE_OFF -> {
                    _bluetoothOn.value = false
                    _scanning.value = false
                }
            }
        }
    }

    // ------------------------------------------------------------------ init

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            appContext, btReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            prefs.demoMode.distinctUntilChanged().collect { enabled ->
                if (enabled) startDemo() else stopDemo()
            }
        }
    }

    // ------------------------------------------------------- reference count

    /** Keep the scan loop alive. Pair every [acquire] with a [release]. */
    fun acquire(holder: Any) {
        synchronized(lock) {
            holders.add(holder)
            if (holders.size == 1) startScanLoop()
        }
    }

    fun release(holder: Any) {
        val shouldStop = synchronized(lock) {
            holders.remove(holder)
            holders.isEmpty()
        }
        if (shouldStop) stopScanLoop()
    }

    fun device(address: String): TrackedDevice? = _devices.value[address]

    /** Restart the scan loop (user pulled refresh). */
    fun restart() {
        synchronized(lock) {
            if (holders.isNotEmpty()) {
                scanJob?.cancel()
                stopLeScan()
                startScanLoop()
            }
        }
    }

    fun clearScanError() {
        _scanError.value = null
    }

    fun replaceDevices(transform: (Map<String, TrackedDevice>) -> Map<String, TrackedDevice>) {
        _devices.value = transform(_devices.value)
    }

    fun clearDevices() {
        _devices.value = emptyMap()
    }

    // ------------------------------------------------------------ scan loop

    private fun startScanLoop() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            _scanError.value = null
            while (isActive) {
                pruneStaleDevices()
                runOneScan()
                delay(profile.idleMs)
            }
        }
    }

    /** Drop devices that stopped advertising so lists and counts stay accurate. */
    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        val current = _devices.value
        val kept = current.filterValues {
            it.isDemo || now - it.lastSeen <= STALE_TIMEOUT_MS
        }
        if (kept.size != current.size) _devices.value = kept
    }

    private fun stopScanLoop() {
        scanJob?.cancel()
        scanJob = null
        stopLeScan()
        _scanning.value = false
    }

    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private suspend fun runOneScan() {
        val bt = adapter ?: run {
            _bluetoothOn.value = false
            return
        }
        if (!bt.isEnabled) {
            _bluetoothOn.value = false
            _scanning.value = false
            return
        }
        _bluetoothOn.value = true

        if (!hasScanPermission()) {
            _scanning.value = false
            _scanError.value = "permission"
            return
        }

        val scanner = try {
            bt.bluetoothLeScanner
        } catch (se: SecurityException) {
            _scanError.value = "permission"
            return
        } ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(profile.mode)
            .build()

        _scanning.value = true
        try {
            scanner.startScan(null, settings, callback)
        } catch (se: SecurityException) {
            _scanning.value = false
            _scanError.value = "permission"
            return
        } catch (e: IllegalStateException) {
            _scanning.value = false
            _scanError.value = "failed"
            return
        } catch (e: Exception) {
            _scanning.value = false
            _scanError.value = "failed"
            return
        }

        delay(profile.activeMs)
        stopLeScan()
        _scanning.value = false
    }

    private fun stopLeScan() {
        val bt = adapter ?: return
        try {
            if (bt.isEnabled && hasScanPermission()) {
                bt.bluetoothLeScanner?.stopScan(callback)
            }
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        } catch (_: Exception) {
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            _scanError.value = "failed"
        }
    }

    private fun handleResult(result: ScanResult) {
        val rssi = result.rssi
        if (rssi == 127 || rssi > 20 || rssi < -127) return // invalid readings
        val address = result.device?.address ?: return
        if (address.startsWith(DEMO_PREFIX)) return

        val advertised = try {
            result.device.name ?: result.scanRecord?.deviceName
        } catch (_: SecurityException) {
            result.scanRecord?.deviceName
        }
        val name = advertised?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown device"

        val now = System.currentTimeMillis()
        val current = _devices.value
        val previous = current[address]
        val smoothed = if (previous == null || previous.isDemo) {
            rssi
        } else {
            (previous.rssi * (1 - RSSI_SMOOTHING) + rssi * RSSI_SMOOTHING).roundToInt()
        }

        val updated = current + (address to TrackedDevice(address, name, smoothed, now))
        _devices.value = trim(updated)
    }

    /** Keep the map bounded: strongest first (demo devices win ties). */
    private fun trim(map: Map<String, TrackedDevice>): Map<String, TrackedDevice> {
        if (map.size <= MAX_DEVICES) return map
        return map.values
            .sortedWith(compareByDescending<TrackedDevice> { it.isDemo }.thenByDescending { it.rssi })
            .take(MAX_DEVICES)
            .associateBy { it.address }
    }

    // --------------------------------------------------------------- demo

    private data class DemoSpec(val index: Int, val name: String, val baseRssi: Int)

    private val demoSpecs = listOf(
        DemoSpec(1, "JBL Flip 6", -78),
        DemoSpec(2, "Galaxy S23", -65),
        DemoSpec(3, "Apple Watch", -82),
        DemoSpec(4, "AirPods Pro", -90),
        DemoSpec(5, "JBL Charge 5", -102)
    )

    private fun demoAddress(index: Int) = "demo:00:00:00:00:0${index}"

    private fun startDemo() {
        if (demoJob?.isActive == true) return
        demoJob = scope.launch {
            var tick = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val updates = demoSpecs.map { spec ->
                    // Slow, human-like signal drift so meters visibly react.
                    val drift = (kotlin.math.sin(tick / 7.0 + spec.index * 1.7) * 7.0 +
                        kotlin.math.sin(tick / 2.9 + spec.index) * 2.5).roundToInt()
                    val rssi = (spec.baseRssi + drift).coerceAtMost(-40)
                    demoAddress(spec.index) to
                        TrackedDevice(demoAddress(spec.index), spec.name, rssi, now, isDemo = true)
                }.toMap()
                val kept = _devices.value.filterKeys { !it.startsWith(DEMO_PREFIX) }
                _devices.value = kept + updates
                tick++
                delay(1_500)
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
        val kept = _devices.value.filterKeys { !it.startsWith(DEMO_PREFIX) }
        if (kept.size != _devices.value.size) _devices.value = kept
    }

    /** True when a given device is a simulated (demo) device. */
    fun isDemoAddress(address: String) = address.startsWith(DEMO_PREFIX)

    /** Demo-mode helper used by seeding logic (stable mock addresses). */
    fun demoAddressFor(index: Int): String = demoAddress(index)

    /** Roughness check kept for tests: smoothing must never flip sign of drift. */
    internal fun smoothRssi(previous: Int?, incoming: Int): Int =
        previous?.let { (it * (1 - RSSI_SMOOTHING) + incoming * RSSI_SMOOTHING).roundToInt() } ?: incoming

    internal fun distanceDelta(a: Int, b: Int): Int = abs(a - b)
}
