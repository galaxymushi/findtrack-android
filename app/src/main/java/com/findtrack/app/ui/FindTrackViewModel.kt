package com.findtrack.app.ui

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.findtrack.app.BuildConfig
import com.findtrack.app.FindTrackApp
import com.findtrack.app.ble.DeviceIconType
import com.findtrack.app.ble.ScanProfile
import com.findtrack.app.ble.TrackedDevice
import com.findtrack.app.ble.TrackingController
import com.findtrack.app.ble.TrackingState
import com.findtrack.app.data.HistoryDao
import com.findtrack.app.data.HistoryEntity
import com.findtrack.app.data.SavedDeviceEntity
import com.findtrack.app.service.TrackingService
import com.findtrack.app.update.UpdateChecker
import com.findtrack.app.update.UpdateInfo
import com.findtrack.app.update.UpdateInstaller
import com.findtrack.app.util.HistoryCsv
import com.findtrack.app.util.HistoryExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Row model for the Devices list; merges live scan results with saved devices. */
data class DeviceListEntry(
    val address: String,
    val name: String,
    val rssi: Int?,
    val lastSeen: Long?,
    val isSaved: Boolean,
    val isDemo: Boolean,
    val iconType: DeviceIconType,
    val distanceM: Double?,
    val isNearby: Boolean,
    /** User alias, when one has been set for a saved device. */
    val customName: String? = null
) {
    companion object {
        fun from(device: TrackedDevice, saved: SavedDeviceEntity?) = DeviceListEntry(
            address = device.address,
            name = saved?.customName ?: device.name,
            rssi = device.rssi,
            lastSeen = device.lastSeen,
            isSaved = saved != null,
            isDemo = device.isDemo,
            iconType = device.iconType,
            distanceM = device.distanceM,
            isNearby = device.isNearby,
            customName = saved?.customName
        )

        fun fromSaved(entity: SavedDeviceEntity) = DeviceListEntry(
            address = entity.address,
            name = entity.customName ?: entity.name,
            rssi = null,
            lastSeen = null,
            isSaved = true,
            isDemo = entity.address.startsWith("demo:"),
            iconType = com.findtrack.app.ble.classifyDevice(entity.name),
            distanceM = null,
            isNearby = false,
            customName = entity.customName
        )
    }
}

/** In-app update flow, surfaced on Home and in Settings. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val message: String) : UpdateState
}

enum class DownloadStatus { RUNNING, SUCCESS, FAILED }

/** Live DownloadManager progress for the update APK. */
data class DownloadProgress(
    val bytes: Long,
    val total: Long,
    val status: DownloadStatus
) {
    /** 0..1, or null while the server hasn't reported a size yet. */
    val fraction: Float?
        get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

private data class DownloadSnapshot(val status: Int, val bytes: Long, val total: Long)

/**
 * Single shared ViewModel for all screens: owns merged device state, saved
 * devices, history, selection and tracking session wiring.
 */
class FindTrackViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = FindTrackApp.graph
    private val scanner = graph.scanner
    private val prefs = graph.prefs
    private val location = graph.location
    private val database = graph.database
    private val deviceDao = database.deviceDao()
    private val historyDao: HistoryDao = database.historyDao()

    // ---------------------------------------------------------------- BLE

    val bluetoothAvailable: Boolean get() = scanner.bluetoothAvailable
    val scanning: StateFlow<Boolean> = scanner.scanning
    val bluetoothOn: StateFlow<Boolean> = scanner.bluetoothOn
    val scanError: StateFlow<String?> = scanner.scanError

    /** Merged live + saved devices, strongest first. */
    val devices: StateFlow<List<DeviceListEntry>> =
        combine(scanner.devices, deviceDao.observeSaved()) { live, saved ->
            val savedMap = saved.associateBy { it.address }
            live.map { DeviceListEntry.from(it, savedMap[it.address]) } +
                saved.filter { s -> live.none { it.address == s.address } }
                    .map { DeviceListEntry.fromSaved(it) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedCount: StateFlow<Int> = deviceDao.observeSaved()
        .map { list -> list.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val nearbyCount: StateFlow<Int> = devices
        .map { list -> list.count { it.isNearby } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ------------------------------------------------------------ history

    val history: StateFlow<List<HistoryEntity>> = historyDao.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val historyCount: StateFlow<Int> = historyDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // --------------------------------------------------------- selection

    private val _selectedAddress = MutableStateFlow<String?>(null)
    val selectedAddress: StateFlow<String?> = _selectedAddress.asStateFlow()

    /** Last-known entry cache so Map/Home keep working between scan batches. */
    private val _knownDevices = MutableStateFlow<Map<String, DeviceListEntry>>(emptyMap())

    val selectedDevice: StateFlow<DeviceListEntry?> =
        combine(_selectedAddress, _knownDevices) { address, known ->
            address?.let { known[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ----------------------------------------------------- tracking/prefs

    val tracking: StateFlow<TrackingState?> = TrackingController.state
    val trackingAddress: StateFlow<String?> = TrackingController.activeAddress

    val demoMode: StateFlow<Boolean> = prefs.demoMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val soundEnabled: StateFlow<Boolean> = prefs.soundEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** How aggressively Bluetooth is scanned (battery vs. freshness). */
    val scanProfile: StateFlow<ScanProfile> = prefs.scanProfile
        .map { ScanProfile.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanProfile.BALANCED)

    /** Null until DataStore has loaded, so the welcome screen never flashes. */
    val onboardingDone: StateFlow<Boolean?> = prefs.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Rolling RSSI readings per device, newest last (used by sparklines). */
    private val _signalHistory = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val signalHistory: StateFlow<Map<String, List<Int>>> = _signalHistory.asStateFlow()

    // ------------------------------------------------------------- updates

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** Version whose APK is currently downloading (null once idle). */
    private val _downloadingVersion = MutableStateFlow<String?>(null)
    val downloadingVersion: StateFlow<String?> = _downloadingVersion.asStateFlow()

    /** DownloadManager progress, kept fresh by a 1 s poll while downloading. */
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private var updateCheckJob: Job? = null
    private var downloadWatchJob: Job? = null

    /** User's live position for the real Google Map (null until fixed). */
    val userLatLng: StateFlow<com.google.android.gms.maps.model.LatLng?> =
        location.lastFix
            .map { fix -> fix?.let { com.google.android.gms.maps.model.LatLng(it.lat, it.lon) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Camera position for Google Map — survives tab switches because it lives in ViewModel. */
    val cameraPosition = mutableStateOf(
        com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(0.0, 0.0), 16f
        )
    )

    /** Last known real coordinates per device address (from History). */
    fun lastKnownCoordinates(address: String): Pair<Double, Double>? =
        history.value.firstOrNull { it.address == address && it.lat != null && it.lon != null }
            ?.let { it.lat!! to it.lon!! }

    init {
        // Keep scanning while any screen is open.
        scanner.acquire(this)
        if (location.hasPermission()) location.prime()

        // Keep camera centered on live GPS fix.
        viewModelScope.launch {
            userLatLng.collect { loc ->
                if (loc != null) {
                    cameraPosition.value = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(loc, 16f)
                }
            }
        }

        viewModelScope.launch {
            devices.debounce(300).collect { list ->
                _knownDevices.value = list.associateBy { it.address }
            }
        }

        // Keep a short rolling RSSI history per device for the signal chart.
        viewModelScope.launch {
            scanner.devices.collect { list ->
                val live = list.map { it.address }.toSet()
                val updated = _signalHistory.value
                    .filterKeys { it in live }
                    .toMutableMap()
                list.forEach { device ->
                    val previous = updated[device.address].orEmpty()
                    if (previous.lastOrNull() != device.rssi) {
                        updated[device.address] = (previous + device.rssi).takeLast(40)
                    }
                }
                _signalHistory.value = updated
            }
        }

        // Record history for saved devices while the app is open (10 min cooldown
        // is applied inside LocationRepository.recordSighting).
        viewModelScope.launch {
            combine(scanner.devices, deviceDao.observeSaved()) { live, saved ->
                val savedMap = saved.associateBy { it.address }
                live.filter { it.address in savedMap }
                    .map { it to (savedMap[it.address]?.customName ?: it.name) }
            }.distinctUntilChanged().debounce(5_000).collect { list ->
                if (hasLocationPermission()) {
                    list.forEach { (device, name) ->
                        location.recordSighting(historyDao, device, displayName = name)
                    }
                }
            }
        }

        // Demo mode: seed sample history when switched on, clear it when switched off.
        viewModelScope.launch {
            combine(prefs.demoMode, historyDao.observeCount()) { demo, count -> demo to count }
                .distinctUntilChanged().collect { (demo, count) ->
                    if (demo && count == 0) {
                        seedDemoHistory()
                    } else if (!demo && count > 0) {
                        clearDemoHistory()
                    }
                }
        }

        // Quietly look for a newer GitHub release after the first screens settle.
        viewModelScope.launch {
            delay(2_500)
            if (prefs.onboardingDone.first()) checkForUpdates(notify = true)
        }
    }

    // ------------------------------------------------------------ actions

    fun refresh() {
        scanner.restart()
    }

    fun clearScanError() {
        scanner.clearScanError()
    }

    fun select(address: String) {
        _selectedAddress.value = address
    }

    fun startTracking(context: Context, entry: DeviceListEntry) {
        viewModelScope.launch {
            val existing = deviceDao.find(entry.address)
            deviceDao.upsert(
                SavedDeviceEntity(
                    address = entry.address,
                    name = existing?.name ?: entry.name,
                    addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                    customName = existing?.customName
                )
            )
        }
        select(entry.address)
        TrackingService.start(context, entry.address, entry.name)
    }

    fun stopTracking(context: Context) {
        TrackingService.stop(context)
    }

    fun toggleSaved(entry: DeviceListEntry) {
        viewModelScope.launch {
            if (entry.isSaved) deviceDao.delete(entry.address)
            else deviceDao.upsert(
                SavedDeviceEntity(entry.address, entry.name, System.currentTimeMillis())
            )
        }
    }

    /** Sets or clears a saved device's custom name (blank restores the advertised name). */
    fun renameDevice(address: String, newName: String) {
        viewModelScope.launch {
            val existing = deviceDao.find(address) ?: return@launch
            val alias = newName.trim().takeIf { it.isNotEmpty() && it != existing.name }
            deviceDao.updateCustomName(address, alias)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDemoMode(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setSoundEnabled(enabled) }
    }

    fun setScanProfile(profile: ScanProfile) {
        viewModelScope.launch { prefs.setScanProfile(profile.name) }
    }

    /** Writes all history to a CSV in the cache and opens the share sheet. */
    fun exportHistory(context: Context, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val entries = historyDao.all()
            val uri = if (entries.isEmpty()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    HistoryExport.write(context, HistoryCsv.build(entries))
                }
            }
            if (uri == null) {
                onResult(false)
            } else {
                HistoryExport.share(context, uri)
                onResult(true)
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }

    fun showOnboardingAgain() {
        viewModelScope.launch { prefs.setOnboardingDone(false) }
    }

    // ------------------------------------------------------------ updates

    /**
     * Best-effort check against GitHub Releases. [notify] posts a system
     * notification, at most once per released version.
     */
    fun checkForUpdates(notify: Boolean = false) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateChecker.Result.Available -> {
                    _updateState.value = UpdateState.Available(result.info)
                    if (notify) notifyUpdateAvailable(result.info)
                }

                UpdateChecker.Result.UpToDate -> _updateState.value = UpdateState.UpToDate
                is UpdateChecker.Result.Failed -> _updateState.value =
                    UpdateState.Failed(result.message)
            }
        }
    }

    private suspend fun notifyUpdateAvailable(info: UpdateInfo) {
        if (prefs.lastNotifiedVersion.first() == info.version) return
        if (UpdateInstaller.notifyAvailable(getApplication(), info)) {
            prefs.setLastNotifiedVersion(info.version)
        }
    }

    /** Starts the APK download, falling back to the release page without an asset. */
    fun downloadUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        val app = getApplication<Application>()
        val apkUrl = info.apkUrl
        if (apkUrl == null) {
            UpdateInstaller.openReleasePage(app, info.releaseUrl)
            return
        }
        _downloadProgress.value = null
        if (UpdateInstaller.download(app, apkUrl, info.version)) {
            _downloadingVersion.value = info.version
            _downloadProgress.value = DownloadProgress(0L, -1L, DownloadStatus.RUNNING)
            watchDownload()
        } else {
            _downloadProgress.value = DownloadProgress(0L, -1L, DownloadStatus.FAILED)
        }
    }

    /** Polls DownloadManager so the UI shows real progress and failures. */
    private fun watchDownload() {
        downloadWatchJob?.cancel()
        downloadWatchJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = UpdateInstaller.downloadId(app)
            if (id == null) {
                _downloadProgress.value = DownloadProgress(0L, -1L, DownloadStatus.FAILED)
                _downloadingVersion.value = null
                return@launch
            }
            var stalledSeconds = 0
            while (isActive) {
                delay(1_000)
                val snapshot = queryDownload(manager, id) ?: continue
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _downloadProgress.value =
                            DownloadProgress(snapshot.bytes, snapshot.total, DownloadStatus.SUCCESS)
                        _downloadingVersion.value = null
                        return@launch
                    }

                    DownloadManager.STATUS_FAILED -> {
                        _downloadProgress.value =
                            DownloadProgress(snapshot.bytes, snapshot.total, DownloadStatus.FAILED)
                        _downloadingVersion.value = null
                        return@launch
                    }

                    else -> {
                        // No bytes for 45 s means the system downloader is stuck
                        // (common on flaky mobile networks); offer retry/browser
                        // instead of showing "Downloading…" forever.
                        stalledSeconds = if (snapshot.bytes > 0) 0 else stalledSeconds + 1
                        if (stalledSeconds >= 45) {
                            _downloadProgress.value =
                                DownloadProgress(snapshot.bytes, snapshot.total, DownloadStatus.FAILED)
                            _downloadingVersion.value = null
                            return@launch
                        }
                        _downloadProgress.value =
                            DownloadProgress(snapshot.bytes, snapshot.total, DownloadStatus.RUNNING)
                    }
                }
            }
        }
    }

    private fun queryDownload(manager: DownloadManager, id: Long): DownloadSnapshot? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            DownloadSnapshot(
                status = it.getInt(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                ),
                bytes = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ),
                total = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
            )
        }
    }

    /** Opens the system installer for the finished APK. */
    fun installDownloadedUpdate() {
        val app = getApplication<Application>()
        val uri = UpdateInstaller.downloadedUri(app)
        if (uri == null) {
            _downloadProgress.value = DownloadProgress(0L, -1L, DownloadStatus.FAILED)
            return
        }
        UpdateInstaller.openInstaller(app, uri)
    }

    /** Escape hatch when the system downloader is stuck on this network. */
    fun openDownloadInBrowser() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        UpdateInstaller.openReleasePage(
            getApplication(),
            info.apkUrl ?: info.releaseUrl
        )
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    // -------------------------------------------------------- permissions

    fun hasAllPermissions(context: Context): Boolean =
        missingPermissions(context).isEmpty()

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun hasLocationPermission(): Boolean = location.hasPermission()

    /** Called after a permission request completes to warm up location fixes. */
    fun onLocationPermissionPossiblyGranted() {
        if (location.hasPermission()) location.prime()
    }

    fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        // GPS is used to save real "last seen" places in History.
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.distinct().toTypedArray()

    fun permissionRationale(context: Context): String {
        val missing = missingPermissions(context)
        return when {
            missing.isEmpty() -> "All permissions granted."
            missing.any { it.contains("BLUETOOTH") } ->
                "Bluetooth access is required to scan for nearby devices. " +
                    "FindTrack never derives your location from Bluetooth."
            else ->
                "Location access is used to remember where your devices were last seen. " +
                    "Everything stays on this phone."
        }
    }

    // ---------------------------------------------------------------- demo

    /** The design's sample devices, used for demo history seeding. */
    private fun seedDemoHistory() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val samples = listOf(
                Triple("demo:00:00:00:00:01", "JBL Flip 6", -68),
                Triple("demo:00:00:00:00:02", "Galaxy S23", -65),
                Triple("demo:00:00:00:00:03", "Apple Watch", -82),
                Triple("demo:00:00:00:00:04", "AirPods Pro", -90),
                Triple("demo:00:00:00:00:05", "JBL Charge 5", -102)
            )
            val places = listOf(
                "Mlimani City, Dar es Salaam" to Pair(-6.7730, 39.2210),
                "Kariakoo Market, Dar es Salaam" to Pair(-6.8180, 39.2803),
                "Coco Beach, Dar es Salaam" to Pair(-6.7590, 39.2810),
                "Julius Nyerere Airport, Dar es Salaam" to Pair(-6.8780, 39.2026),
                "Mwenge, Dar es Salaam" to Pair(-6.7695, 39.2435)
            )
            val times = listOf(
                clockToday(9, 37, now),
                clockToday(8, 14, now),
                clockYesterday(18, 32, now),
                clockYesterday(15, 21, now),
                clockYesterday(23, 3, now)
            )
            samples.forEachIndexed { i, (address, devName, rssi) ->
                historyDao.insert(
                    HistoryEntity(
                        address = address,
                        name = devName,
                        rssi = rssi,
                        lat = places[i].second.first,
                        lon = places[i].second.second,
                        place = places[i].first,
                        timestamp = times[i],
                        isDemo = true
                    )
                )
            }
        }
    }

    private fun clockToday(hour: Int, minute: Int, now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now) add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

    private fun clockYesterday(hour: Int, minute: Int, now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Remove all demo history entries when demo mode is switched off. */
    private fun clearDemoHistory() {
        viewModelScope.launch {
            historyDao.deleteDemo()
        }
    }

    override fun onCleared() {
        scanner.release(this)
        super.onCleared()
    }
}
