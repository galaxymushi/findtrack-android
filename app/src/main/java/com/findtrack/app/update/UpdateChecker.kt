package com.findtrack.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Release information fetched from GitHub Releases. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val publishedAt: String?
)

/**
 * Checks the project's public GitHub Releases for a newer version. This is
 * the only network call the app makes besides Google Maps tiles, and it is
 * best-effort: offline users simply see "couldn't check" and keep working.
 */
object UpdateChecker {

    const val REPO = "galaxymushi/findtrack-android"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$REPO/releases/latest"

    sealed interface Result {
        data class Available(val info: UpdateInfo) : Result
        data object UpToDate : Result
        data class Failed(val message: String) : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FindTrack-Android")
            }
        } catch (error: Exception) {
            return@withContext Result.Failed(error.message ?: "Network error")
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.Failed("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("tag_name")
                .trim()
                .removePrefix("v")
                .removePrefix("V")
            if (version.isBlank()) return@withContext Result.Failed("No release tag")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                            .takeIf { it.isNotBlank() }
                        break
                    }
                }
            }

            val info = UpdateInfo(
                version = version,
                notes = json.optString("body"),
                releaseUrl = json.optString("html_url"),
                apkUrl = apkUrl,
                publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }
            )
            if (isNewer(version, currentVersion)) Result.Available(info) else Result.UpToDate
        } catch (error: Exception) {
            Result.Failed(error.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Numeric semantic-version comparison; handles tags like "v1.3.0" and
     * build labels like "1.3.0-debug". Returns true only when [latest] is
     * strictly newer than [current].
     */
    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        if (latestParts.isEmpty() || currentParts.isEmpty()) return false
        val size = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until size) {
            val l = latestParts.getOrElse(index) { 0 }
            val c = currentParts.getOrElse(index) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}
