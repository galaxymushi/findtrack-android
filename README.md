# FindTrack — Android App

**Find your devices. Stay connected.**

FindTrack is a Bluetooth Low Energy (BLE) device-finding app built to the approved
design mockups (green/white identity, bottom navigation: Home · Devices · Map · History).
It scans for nearby Bluetooth devices, estimates their distance from signal strength,
tracks them live with a persistent notification, and remembers where they were last seen —
fully offline, with **no accounts and no servers**. The only optional server use is the
Google Maps tile layer (API key) and the GitHub Releases update check.

---

## 1. Deliverables in this folder

| Item | Location |
|---|---|
| **Debug APK (install now)** | `app/build/outputs/apk/debug/app-debug.apk` |
| **Release APK (signed, R8)** | `app/build/outputs/apk/release/app-release.apk` |
| Complete source code | `app/src/main/java/com/findtrack/app/…` |
| Unit tests | `app/src/test/java/com/findtrack/app/…` |
| Development keystore | `app/findtrack.keystore` (see §6) |

---

## 2. Installation guide

### Option A — install on a phone (USB)
1. Enable **Developer options** → **USB debugging** on the phone.
2. Connect the phone and accept the debugging prompt.
3. Install:
   ```
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
4. Launch **FindTrack** from the app drawer.

### Option B — install without a computer
1. Copy `app-debug.apk` to the phone (cloud drive, cable, Bluetooth…).
2. Open it with the file manager and allow **"Install from this source"** when asked.
3. If Play Protect shows a warning, choose **"Install anyway"** — debug builds are
   not Play-verified. (Use the release APK + your own Play signing for distribution.)

### Option C — emulator
```
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.findtrack.app/com.findtrack.app.MainActivity
```

### First launch
A three-page welcome flow introduces scanning, the map markers and the
offline/privacy model (skippable; replayable from **Settings → Show welcome
screen again**). FindTrack then asks for the permissions it needs and explains why:
- **Bluetooth (Scan/Connect)** — to discover nearby devices (Android 12+)
- **Location** — to remember *where* your devices were last seen (History)
- **Notifications** — to show the ongoing tracking notification (Android 13+)

Bluetooth scanning never derives your location (declared `neverForLocation`);
places in History come from the phone's own GPS.

### Update notifications
After onboarding, FindTrack quietly checks the public GitHub Releases page
(`galaxymushi/findtrack-android`) on launch. When a newer version exists it posts
one notification and shows a green **Update available** card on Home. Tapping
**Download** (in the app or in the notification) downloads the APK through
Android's DownloadManager; when it finishes, a **tap to install** notification
opens the system installer. The same check is available any time from
**Settings → Check for updates**. It is best-effort: offline or blocked
notifications never affect scanning, tracking or history, and each version is
announced at most once.

---

## 3. Implemented features

### Screens (all from the approved design)
- **Welcome** — animated onboarding: radar sweep, marker explainer (blue dot vs
  green pin) and privacy/offline pages; persisted so it shows once.
- **Home** — brand header, live "Scanning for devices…" card with nearby count,
  nearby/found/saved stat tiles, stylized map with a **legend** (blue = you,
  green = Bluetooth device), zoom +/− and recenter controls, featured-device
  quick card with signal bars → device detail.
- **Devices** — search by name/address, sort by **strongest signal / most recent
  / name**, filter chips **All / Nearby / Saved** with live counts, refresh
  action with rotating icon, rows with icon, name, Nearby/Far badge, 4-bar
  signal meter, RSSI, age and estimated distance, one-tap bookmark, empty
  states per filter and for empty search results.
- **Device detail** — name + Nearby badge + "Last seen … • RSSI: …", map with the
  device pin and a "Last known location" card, **signal chart** of this session's
  readings, save/unsave (bookmark), primary **Start Tracking** button.
- **Tracking** — animated radar, live status copy ("Signal getting stronger /
  weakening / steady / lost"), signal meter, RSSI with strength tag,
  approximate distance, last-seen time, proximity beeps + vibration (mutable),
  **Stop Tracking**.
- **Finder Map** — full map with callout ("Last seen 2 min ago"), legend,
  device summary card and "Last seen location" from History.
- **History** — chronological device sightings with place, time and RSSI pill,
  tap → device detail, clear-history with confirmation, info card.
- **Settings** — permission status + fix buttons, Bluetooth state, **Demo mode**
  switch, proximity sound switch, replay welcome, **check for updates**, clear
  history, about.

### Real functionality (not placeholders)
- **BLE scanning** — reference-counted scan loop shared by UI and service;
  5 s on / 2 s off duty cycling for battery; periodic restart for flaky stacks;
  exponentially smoothed RSSI; Bluetooth on/off hot-plug via broadcast receiver.
- **Distance estimation** — log-distance path-loss model (TxPower −59 dBm @ 1 m,
  exponent 2.2), clamped 0.3–99 m; drives Nearby/Far classification (≤ 30 m).
- **Live tracking service** — foreground service (`connectedDevice` type) keeps
  tracking in the background with a persistent notification showing live distance,
  Stop action, and **saves the last known location + notifies when the device
  goes out of range** (15 s silence).
- **Proximity feedback** — beep rate + vibration improve as you get closer.
- **Persistent storage (offline-first)** — Room database: saved devices and
  location history (10-minute coalescing per device); DataStore for settings.
  **Scanning, history and GPS work in airplane mode;** the only network uses are
  downloading Google Maps tiles (when an API key is configured) and the optional
  GitHub update check.
- **In-app updates** — checks `releases/latest` on the public repo, posts a
  one-per-version notification plus a Home card, downloads the APK with
  DownloadManager, and turns download completion into a **tap to install**
  notification. Falls back to the release page if a release has no APK asset.
- **Real GPS capture** — FusedLocationProvider with framework `LocationManager`
  fallback, offline reverse-geocoding against a bundled Tanzania gazetteer.
- **Demo mode** — a clearly-labeled simulator emitting the design's five sample
  devices (drifting RSSI) so every screen can be reviewed on any hardware,
  including the emulator. Off by default; labeled on Home whenever active.
- **Robust states** — loading (scanning), Bluetooth off, permission needed,
  empty list/history/map, scan failure, location unavailable, invalid RSSI.
- **Clear map semantics** — this phone is always the pulsing **blue dot**; BLE
  devices are **green pins** with an anchor dot colored by live signal
  (green/amber/red). A legend is shown on both maps.
- **Localized UI** — notification and app-level strings, welcome flow and time
  formatting switch to **Kiswahili** when the phone language is Swahili.
- **Security/privacy** — data stays on device; cloud backup is disabled
  (`allowBackup="false"`); `neverForLocation` BLE flag; no secrets in the APK.

---

## 4. Technology stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.09.03), Material 3, **Inter** typeface (bundled) |
| Maps | **Google Maps SDK for Android** (real tiles when API key configured) + offline stylized vector map fallback |
| Location | **FusedLocationProvider** (Google Play services) with LocationManager fallback |
| Navigation | Navigation-Compose 2.8.1 |
| Async | Kotlin coroutines + Flow |
| Storage | Room 2.6.1 (KSP), DataStore Preferences |
| Bluetooth | Android BLE APIs (`BluetoothLeScanner`) |
| Background | Foreground `Service`, type `connectedDevice` |
| Build | Gradle 8.14.3, AGP 8.7.3, JDK 17 target |

**Compatibility:** minSdk 26 (Android 8.0) → targetSdk/compileSdk 35 (Android 15).
Version 1.3.0. Phone, foldable and tablet layouts are responsive; the light design identity is kept in both system themes.

### Google Maps setup (2 minutes, optional but recommended)
The app ships map-ready: with an API key it shows **real Google Maps with your true
location**; without one it uses the built-in offline map. To enable Google Maps:
1. Open [Google Cloud Console](https://console.cloud.google.com/) → create a project.
2. **APIs & Services → Library** → enable **"Maps SDK for Android"**.
3. **APIs & Services → Credentials → Create credentials → API key**.
   (Google gives free monthly credit for Maps; billing account required.)
4. Restrict the key: *Application restrictions* → Android apps → package
   `com.findtrack.app` (SHA-1 of your signing cert optional for debug).
5. Paste into `android/local.properties`:
   ```properties
   maps.api.key=AIzaSy...your-key...
   ```
6. Rebuild: `gradlew assembleDebug`. The key is injected via manifest placeholder
   and BuildConfig — it never appears in source code.

---

## 5. Building from source

```
cd android
gradlew assembleDebug       # debug APK
gradlew testDebugUnitTest   # unit tests
gradlew assembleRelease     # signed release APK (after keystore exists)
```
No backend setup is required — FindTrack stores everything on-device. The only
optional credential is the Google Maps API key (§4); without it the app uses the
offline map and still records GPS coordinates for History.

---

## 6. Signing

- **Debug APK** is signed with the auto-generated Android debug keystore —
  ideal for installation and testing (deliverable for this phase).
- **Release APK** uses `app/findtrack.keystore` if that file is present locally;
  otherwise Gradle falls back to the debug keystore. The keystore is **not
  committed** and is not part of this delivery, so builds on a new machine are
  debug-signed until you add it.
- **Updates install over the old version only with the same signing key.** Keep
  the release keystore safe; if you change keys, users must uninstall once.
- For production (Play Store) distribution:
  1. Create your own keystore: `keytool -genkey -v -keystore company.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
  2. Store it + passwords in CI secrets, never in git.
  3. Point `signingConfigs.release` in `app/build.gradle.kts` at it (or use
     environment variables) and rebuild `assembleRelease`.

---

## 7. Publishing a new version (so users get the notification)

1. Bump `versionCode` **and** `versionName` in `app/build.gradle.kts`
   (`versionCode` must increase or Android refuses the update).
2. Build the release APK: `gradlew assembleRelease`.
3. On GitHub, create a **Release** with tag `v<versionName>` (for example
   `v1.3.0`) and upload the APK as an asset (any `*.apk` name works).
4. Mark it as the latest release. Installed apps then find it via
   `releases/latest` on their next launch or Settings check and offer the
   download.

No app-store account or backend is involved — the GitHub release is the update
channel.

---

## 8. Testing performed

- Unit tests: distance model, signal levels, device classification, time/place
  formatting, bearing stability, version comparison — `gradlew testDebugUnitTest`.
- Build verification: `assembleDebug` / `assembleRelease` compile clean.
- On-device verification: installed and exercised on a running Android 34
  emulator (Pixel 6), including permission flow, all seven screens, demo mode,
  tracking session start/stop and history persistence (screenshots captured).

## 9. Known limitations

- **Bearings on estimated pins are illustrative.** BLE gives distance, not direction;
  when a device has no stored GPS fix, its pin is placed at the correct *distance*
  radius around you at a stable pseudo-random bearing. Once a device has been seen
  while tracking, its **real last-known GPS coordinates** are stored and used on the
  Google Map.
- **Distance is an RSSI estimate** — obstructions/multipath make it approximate
  (±30-50 %), the same as every BLE finder.
- **BLE device names** depend on what devices advertise; unnamed advertisers
  appear as "Unknown device" (common for beacons/tags).
- **No crowd-finding network** (like Apple/Tile) — FindTrack finds devices that
  are in Bluetooth range of *this phone*.
- **Location labels** use a small bundled gazetteer; outside its coverage the
  History shows "Captured location" (coordinates are still stored).
- **Google Maps needs an API key** (§4); without it the app falls back to the
  built-in offline map. The map tile layer needs internet; everything else works
  offline.
- The design is light-identity only; the app keeps that identity in dark mode.
